package com.clothing.app.controller;

import com.clothing.app.config.SecurityConfig;
import com.clothing.app.entity.Employee;
import com.clothing.app.entity.UserAccount;
import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.security.CustomUserDetailsService;
import com.clothing.app.service.OracleSessionContextService;
import com.clothing.app.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReportApiController.class)
@Import(SecurityConfig.class)
class ReportApiControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private OracleSessionContextService oracleSessionContextService;

    @BeforeEach
    void setUp() {
        when(userAccountRepository.findByUsername("admin")).thenReturn(Optional.of(createAccount("admin")));
        when(userAccountRepository.findByUsername("cashier")).thenReturn(Optional.of(createAccount("cashier")));
    }

    private UserAccount createAccount(String username) {
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setEnabled(true);
        Employee employee = new Employee();
        employee.setEmployeeId(1L);
        employee.setEmployeeName("Test Employee");
        employee.setStatus("ACTIVE");
        account.setEmployee(employee);
        return account;
    }

    @Test
    void unauthenticatedAccessToReportsApiIsBlocked() throws Exception {
        mockMvc.perform(get("/api/reports/kpi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cashierRoleCannotAccessReportsApi() throws Exception {
        mockMvc.perform(get("/api/reports/kpi").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessKpiEndpoint() throws Exception {
        when(reportService.getKpiSummary()).thenReturn(Map.of(
                "totalNetSales", 9046.91,
                "totalOrders", 62,
                "totalInventoryCostValue", 88493,
                "totalInventorySaleValue", 161353.72,
                "lowStockCount", 15,
                "totalVariants", 48
        ));

        mockMvc.perform(get("/api/reports/kpi").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNetSales").value(9046.91))
                .andExpect(jsonPath("$.totalOrders").value(62))
                .andExpect(jsonPath("$.lowStockCount").value(15))
                .andExpect(jsonPath("$.totalVariants").value(48));
    }

    @Test
    void adminCanAccessDashboardDataEndpoint() throws Exception {
        when(reportService.getDashboardMetrics(anyString())).thenReturn(Map.of(
                "totalOrders", 10,
                "totalNetSales", new BigDecimal("1200.00")
        ));
        when(reportService.getSalesTrend(anyString())).thenReturn(List.of(
                Map.of("LABEL", "12:00", "NET_SALES", 200.00)
        ));
        when(reportService.getCategorySales(anyString())).thenReturn(List.of(
                Map.of("CATEGORY_NAME", "Shirts", "TOTAL_REVENUE", 500.00)
        ));
        when(reportService.getCashierSales(anyString())).thenReturn(List.of(
                Map.of("EMPLOYEE_NAME", "Vicheka Ly", "NET_REVENUE", 700.00)
        ));
        when(reportService.getTopSellingProducts(anyString())).thenReturn(List.of(
                Map.of("PRODUCT_NAME", "P1")
        ));

        mockMvc.perform(get("/api/reports/dashboard-data?period=daily").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.totalOrders").value(10))
                .andExpect(jsonPath("$.metrics.totalNetSales").value(1200.00))
                .andExpect(jsonPath("$.trend[0].LABEL").value("12:00"))
                .andExpect(jsonPath("$.categorySales[0].CATEGORY_NAME").value("Shirts"))
                .andExpect(jsonPath("$.cashierSales[0].EMPLOYEE_NAME").value("Vicheka Ly"))
                .andExpect(jsonPath("$.topSelling[0].PRODUCT_NAME").value("P1"));
    }

    @Test
    void adminCanAccessCashierSalesAndDetails() throws Exception {
        when(reportService.getCashierSales("all")).thenReturn(List.of(
                Map.of("EMPLOYEE_ID", 54L, "EMPLOYEE_NAME", "Vicheka Ly", "TOTAL_ORDERS", 26)
        ));
        when(reportService.getCashierSalesDetails(54L, "all")).thenReturn(List.of(
                Map.of("SALE_ID", 1001L, "GRAND_TOTAL", 150.00, "STATUS", "COMPLETED")
        ));

        mockMvc.perform(get("/api/reports/cashiers?period=all").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].EMPLOYEE_NAME").value("Vicheka Ly"))
                .andExpect(jsonPath("$[0].TOTAL_ORDERS").value(26));

        mockMvc.perform(get("/api/reports/cashiers/54/sales?period=all").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].SALE_ID").value(1001L))
                .andExpect(jsonPath("$[0].STATUS").value("COMPLETED"));
    }
}
