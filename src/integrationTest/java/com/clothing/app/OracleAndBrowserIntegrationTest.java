package com.clothing.app;

import com.clothing.app.dto.StockAdjustmentRequestDto;
import com.clothing.app.service.StockAdjustmentService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OracleAndBrowserIntegrationTest {
    private static final String ADMIN_PASSWORD = "AdminStrong@123";
    private static final String CASHIER_PASSWORD = "CashierStrong@123";

    @Container
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:slim-faststart")
            .withUsername("CLOTHING_APP")
            .withPassword("IntegrationOnly@123")
            .withCopyFileToContainer(MountableFile.forHostPath("database/oracle-bootstrap.sql"),
                    "/container-entrypoint-initdb.d/01_oracle_bootstrap.sql");

    @DynamicPropertySource
    static void oracleProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
        registry.add("spring.datasource.username", ORACLE::getUsername);
        registry.add("spring.datasource.password", ORACLE::getPassword);
        registry.add("spring.jpa.properties.hibernate.default_schema", ORACLE::getUsername);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("app.schema-migration.enabled", () -> "true");
        registry.add("app.demo-data.enabled", () -> "false");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StockAdjustmentService stockAdjustmentService;
    @LocalServerPort int port;

    private long adminEmployeeId;
    private long cashierEmployeeId;
    private long supplierId;
    private long activeVariantId;
    private long inactiveVariantId;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO APP_ROLE (ROLE_NAME, DESCRIPTION) VALUES ('ADMIN', 'Integration administrator')");
        jdbc.update("INSERT INTO APP_ROLE (ROLE_NAME, DESCRIPTION) VALUES ('CASHIER', 'Integration cashier')");
        jdbc.update("INSERT INTO EMPLOYEE (EMPLOYEE_NAME, POSITION, STATUS) VALUES ('E2E Administrator', 'Administrator', 'ACTIVE')");
        jdbc.update("INSERT INTO EMPLOYEE (EMPLOYEE_NAME, POSITION, STATUS) VALUES ('E2E Cashier', 'Cashier', 'ACTIVE')");
        adminEmployeeId = id("SELECT EMPLOYEE_ID FROM EMPLOYEE WHERE EMPLOYEE_NAME='E2E Administrator'");
        cashierEmployeeId = id("SELECT EMPLOYEE_ID FROM EMPLOYEE WHERE EMPLOYEE_NAME='E2E Cashier'");
        insertUser("e2e_admin", ADMIN_PASSWORD, adminEmployeeId, "ADMIN");
        insertUser("e2e_cashier", CASHIER_PASSWORD, cashierEmployeeId, "CASHIER");

        jdbc.update("INSERT INTO CATEGORY (CATEGORY_NAME, DESCRIPTION) VALUES ('E2E Apparel', 'Integration catalog')");
        jdbc.update("INSERT INTO PRODUCT_SIZE (SIZE_NAME, DESCRIPTION) VALUES ('E2E-M', 'Integration size')");
        jdbc.update("INSERT INTO COLOR (COLOR_NAME, DESCRIPTION) VALUES ('E2E Blue', 'Integration color')");
        long categoryId = id("SELECT CATEGORY_ID FROM CATEGORY WHERE CATEGORY_NAME='E2E Apparel'");
        long sizeId = id("SELECT SIZE_ID FROM PRODUCT_SIZE WHERE SIZE_NAME='E2E-M'");
        long colorId = id("SELECT COLOR_ID FROM COLOR WHERE COLOR_NAME='E2E Blue'");
        jdbc.update("INSERT INTO PRODUCT (CATEGORY_ID, PRODUCT_NAME, IS_ACTIVE) VALUES (?, 'E2E Active Shirt', 'Y')", categoryId);
        jdbc.update("INSERT INTO PRODUCT (CATEGORY_ID, PRODUCT_NAME, IS_ACTIVE) VALUES (?, 'E2E Inactive Shirt', 'N')", categoryId);
        long activeProduct = id("SELECT PRODUCT_ID FROM PRODUCT WHERE PRODUCT_NAME='E2E Active Shirt'");
        long inactiveProduct = id("SELECT PRODUCT_ID FROM PRODUCT WHERE PRODUCT_NAME='E2E Inactive Shirt'");
        jdbc.update("INSERT INTO PRODUCT_VARIANT (PRODUCT_ID, SIZE_ID, COLOR_ID, SKU, COST_PRICE, SALE_PRICE, STOCK_QTY) VALUES (?, ?, ?, 'E2E-ACTIVE-M', 10, 25, 100)", activeProduct, sizeId, colorId);
        jdbc.update("INSERT INTO PRODUCT_VARIANT (PRODUCT_ID, SIZE_ID, COLOR_ID, SKU, COST_PRICE, SALE_PRICE, STOCK_QTY) VALUES (?, ?, ?, 'E2E-INACTIVE-M', 10, 25, 5)", inactiveProduct, sizeId, colorId);
        activeVariantId = id("SELECT VARIANT_ID FROM PRODUCT_VARIANT WHERE SKU='E2E-ACTIVE-M'");
        inactiveVariantId = id("SELECT VARIANT_ID FROM PRODUCT_VARIANT WHERE SKU='E2E-INACTIVE-M'");
        jdbc.update("INSERT INTO SUPPLIER (SUPPLIER_NAME, STATUS) VALUES ('E2E Supplier', 'ACTIVE')");
        supplierId = id("SELECT SUPPLIER_ID FROM SUPPLIER WHERE SUPPLIER_NAME='E2E Supplier'");
    }

    @Test
    @Order(1)
    void oracleSchemaProceduresConstraintsAndAtomicConcurrentAdjustmentsWork() throws Exception {
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME='REFUND_REQUEST'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM USER_OBJECTS WHERE OBJECT_NAME='SP_ADJUST_STOCK_DELTA' AND STATUS='VALID'", Integer.class));

        int before = jdbc.queryForObject("SELECT STOCK_QTY FROM PRODUCT_VARIANT WHERE VARIANT_ID=?", Integer.class, activeVariantId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> one = pool.submit(() -> adjustAfterBarrier(ready, start));
            Future<?> two = pool.submit(() -> adjustAfterBarrier(ready, start));
            ready.await();
            start.countDown();
            one.get();
            two.get();
        } finally {
            pool.shutdownNow();
        }
        assertEquals(before + 10,
                jdbc.queryForObject("SELECT STOCK_QTY FROM PRODUCT_VARIANT WHERE VARIANT_ID=?", Integer.class, activeVariantId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM STOCK_MOVEMENT WHERE VARIANT_ID=? AND REFERENCE_TYPE='ADJUSTMENT'", Integer.class, activeVariantId));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM AUDIT_LOG WHERE TABLE_NAME='PRODUCT_VARIANT' AND ACTION_TYPE='UPDATE'", Integer.class) >= 2);
    }

    @Test
    @Order(2)
    void browserCoversCheckoutPaymentCancellationPurchasingStockBarcodesRefundsAndRoles() {
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            try (BrowserContext adminContext = browser.newContext()) {
                Page admin = adminContext.newPage();
                login(admin, "e2e_admin", ADMIN_PASSWORD, "ADMIN");

                admin.navigate(baseUrl() + "/products");
                admin.getByTitle("Generate & Print Apparel Barcode Labels").first().click();
                admin.locator("#barcodeGeneratorModal").waitFor();
                assertTrue(admin.locator("#barcodeStatusActive").isChecked());
                assertFalse(admin.locator("#barcodeStatusInactive").isChecked());
                assertEquals(1, admin.locator("#barcodeVariantCheckboxList .barcode-variant-chk").count());
                admin.locator("#barcodeStatusInactive").check();
                assertEquals(2, admin.locator("#barcodeVariantCheckboxList .barcode-variant-chk").count());

                admin.navigate(baseUrl() + "/sales");
                Map<?, ?> sale = evaluateApi(admin, "/api/sales", "POST", Map.of(
                        "employeeId", adminEmployeeId,
                        "paymentAmount", 25,
                        "paymentMethod", "CASH",
                        "referenceNo", "E2E-PAYMENT",
                        "items", new Object[]{Map.of("variantId", activeVariantId, "quantity", 1, "unitPrice", 25, "discount", 0)}));
                long saleId = ((Number) sale.get("saleId")).longValue();
                assertEquals(0, new BigDecimal(sale.get("remainingBalance").toString()).compareTo(BigDecimal.ZERO));

                long pendingSaleId = ((Number) admin.evaluate("""
                    async (employeeId) => {
                      const token=document.querySelector('meta[name="_csrf"]').content;
                      const header=document.querySelector('meta[name="_csrf_header"]').content;
                      const r=await fetch('/api/sales/pending?employeeId='+employeeId, {method:'POST',headers:{[header]:token}});
                      return (await r.json()).saleId;
                    }
                    """, adminEmployeeId)).longValue();
                admin.evaluate("async (id) => await apiRequest('/api/sales/'+id+'/cancel?reason=E2E%20duplicate','POST')", pendingSaleId);
                assertEquals("CANCELLED", jdbc.queryForObject("SELECT STATUS FROM SALE WHERE SALE_ID=?", String.class, pendingSaleId));

                evaluateApi(admin, "/api/purchases", "POST", Map.of(
                        "supplierId", supplierId, "employeeId", adminEmployeeId,
                        "note", "E2E receipt", "receiveImmediately", true,
                        "items", new Object[]{Map.of("variantId", activeVariantId, "quantity", 3, "costPrice", 10)}));
                int beforeManual = jdbc.queryForObject("SELECT STOCK_QTY FROM PRODUCT_VARIANT WHERE VARIANT_ID=?", Integer.class, activeVariantId);
                evaluateApi(admin, "/api/stock/adjust", "POST", Map.of(
                        "variantId", activeVariantId, "quantityChange", 2, "reason", "E2E cycle count"));
                assertEquals(beforeManual + 2, jdbc.queryForObject("SELECT STOCK_QTY FROM PRODUCT_VARIANT WHERE VARIANT_ID=?", Integer.class, activeVariantId));

                Map<?, ?> refund = evaluateApi(admin, "/api/refunds/request/" + saleId, "POST", Map.of("reason", "E2E customer return"));
                long refundId = ((Number) refund.get("refundId")).longValue();
                evaluateApi(admin, "/api/refunds/" + refundId + "/approve", "POST", Map.of("note", "Approved in E2E"));
                assertEquals("COMPLETED", jdbc.queryForObject("SELECT STATUS FROM SALE WHERE SALE_ID=?", String.class, saleId));
                evaluateApi(admin, "/api/refunds/" + refundId + "/complete", "POST", Map.of("refundReference", "E2E-REFUND-001"));
                assertEquals("CANCELLED", jdbc.queryForObject("SELECT STATUS FROM SALE WHERE SALE_ID=?", String.class, saleId));
                assertEquals("COMPLETED", jdbc.queryForObject("SELECT STATUS FROM REFUND_REQUEST WHERE REFUND_ID=?", String.class, refundId));
            }

            try (BrowserContext cashierContext = browser.newContext()) {
                Page cashier = cashierContext.newPage();
                login(cashier, "e2e_cashier", CASHIER_PASSWORD, "CASHIER");
                assertEquals(403, cashier.navigate(baseUrl() + "/dashboard").status());
                cashier.navigate(baseUrl() + "/sales");
                Object status = cashier.evaluate("""
                    async (variantId) => {
                      const token=document.querySelector('meta[name="_csrf"]').content;
                      const header=document.querySelector('meta[name="_csrf_header"]').content;
                      return (await fetch('/api/stock/adjust',{method:'POST',headers:{'Content-Type':'application/json',[header]:token},body:JSON.stringify({variantId,quantityChange:1,reason:'forbidden'})})).status;
                    }
                    """, activeVariantId);
                assertEquals(403, ((Number) status).intValue());
            }
        }
    }

    private void adjustAfterBarrier(CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown(); start.await();
            stockAdjustmentService.adjustStock(new StockAdjustmentRequestDto(activeVariantId, null, 5, "Concurrent E2E adjustment"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new RuntimeException(ex);
        }
    }
    private void insertUser(String username, String password, long employeeId, String role) {
        jdbc.update("INSERT INTO USER_ACCOUNT (EMPLOYEE_ID, USERNAME, PASSWORD_HASH, ENABLED) VALUES (?, ?, ?, 'Y')",
                employeeId, username, passwordEncoder.encode(password));
        long userId = id("SELECT USER_ID FROM USER_ACCOUNT WHERE USERNAME='" + username + "'");
        long roleId = id("SELECT ROLE_ID FROM APP_ROLE WHERE ROLE_NAME='" + role + "'");
        jdbc.update("INSERT INTO USER_ROLE (USER_ID, ROLE_ID) VALUES (?, ?)", userId, roleId);
    }
    private long id(String sql) { return jdbc.queryForObject(sql, Long.class); }
    private String baseUrl() { return "http://127.0.0.1:" + port; }
    private void login(Page page, String username, String password, String role) {
        page.navigate(baseUrl() + "/login");
        page.locator("#username").fill(username);
        page.locator("#password").fill(password);
        page.locator("#role").selectOption(role);
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Sign in to Account")).click();
        page.waitForLoadState();
        assertFalse(page.url().contains("/login"), "Login failed for " + username);
    }
    @SuppressWarnings("unchecked")
    private Map<?, ?> evaluateApi(Page page, String url, String method, Map<String, Object> payload) {
        return (Map<?, ?>) page.evaluate("async (arg) => await apiRequest(arg.url,arg.method,arg.payload)",
                Map.of("url", url, "method", method, "payload", payload));
    }
}
