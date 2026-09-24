package com.clothing.app.config;

import com.clothing.app.entity.*;
import com.clothing.app.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.clothing.app.service.OracleSessionContextService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CategoryRepository categoryRepository;
    private final ProductSizeRepository productSizeRepository;
    private final ColorRepository colorRepository;
    private final SupplierRepository supplierRepository;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseDetailRepository purchaseDetailRepository;
    private final SaleRepository saleRepository;
    private final SaleDetailRepository saleDetailRepository;
    private final PaymentRepository paymentRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AppRoleRepository appRoleRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OracleSessionContextService oracleSessionContextService;

    @Value("${app.demo-data.admin-password:}")
    private String demoAdminPassword;

    @Value("${app.demo-data.cashier-password:}")
    private String demoCashierPassword;

    public DataInitializer(CategoryRepository categoryRepository,
                           ProductSizeRepository productSizeRepository,
                           ColorRepository colorRepository,
                           SupplierRepository supplierRepository,
                           EmployeeRepository employeeRepository,
                           CustomerRepository customerRepository,
                           ProductRepository productRepository,
                           ProductVariantRepository productVariantRepository,
                           PurchaseRepository purchaseRepository,
                           PurchaseDetailRepository purchaseDetailRepository,
                           SaleRepository saleRepository,
                           SaleDetailRepository saleDetailRepository,
                           PaymentRepository paymentRepository,
                           StockMovementRepository stockMovementRepository,
                           AppRoleRepository appRoleRepository,
                           UserAccountRepository userAccountRepository,
                           UserRoleRepository userRoleRepository,
                           PasswordEncoder passwordEncoder,
                           OracleSessionContextService oracleSessionContextService) {
        this.categoryRepository = categoryRepository;
        this.productSizeRepository = productSizeRepository;
        this.colorRepository = colorRepository;
        this.supplierRepository = supplierRepository;
        this.employeeRepository = employeeRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.purchaseRepository = purchaseRepository;
        this.purchaseDetailRepository = purchaseDetailRepository;
        this.saleRepository = saleRepository;
        this.saleDetailRepository = saleDetailRepository;
        this.paymentRepository = paymentRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.appRoleRepository = appRoleRepository;
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.oracleSessionContextService = oracleSessionContextService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Checking and initializing demo dataset for Clothing Sales Management System...");

        requireConfiguredPassword(demoAdminPassword, "app.demo-data.admin-password");
        requireConfiguredPassword(demoCashierPassword, "app.demo-data.cashier-password");
        oracleSessionContextService.applySystemUser("DEMO_DATA");

        // 1. Roles
        AppRole adminRole = getOrCreateRole("ADMIN", "Administrator with full system permissions");
        AppRole cashierRole = getOrCreateRole("CASHIER", "Cashier with sales and checkout access");

        // 2. Categories
        Category catMen = getOrCreateCategory("Men's Apparel", "Shirts, trousers, and casual wear for men");
        Category catWomen = getOrCreateCategory("Women's Apparel", "Dresses, blouses, skirts, and tailored fashion");
        Category catSummer = getOrCreateCategory("Summer & Beach", "Linen shirts, resort wear, swimwear, and hats");
        Category catOuterwear = getOrCreateCategory("Jackets & Outerwear", "Blazers, denim jackets, and coats");
        Category catCasual = getOrCreateCategory("Casual & Tees", "Everyday cotton tees, polos, and jeans");
        Category catAccessories = getOrCreateCategory("Accessories & Belts", "Leather goods, hats, scarves, and accessories");

        // 3. Sizes
        ProductSize sizeXS = getOrCreateSize("XS", "Extra Small");
        ProductSize sizeS = getOrCreateSize("S", "Small");
        ProductSize sizeM = getOrCreateSize("M", "Medium");
        ProductSize sizeL = getOrCreateSize("L", "Large");
        ProductSize sizeXL = getOrCreateSize("XL", "Extra Large");
        ProductSize sizeXXL = getOrCreateSize("XXL", "Double Extra Large");
        ProductSize sizeFree = getOrCreateSize("FREE", "Free Size / Adjustable");

        // 4. Colors
        Color colWhite = getOrCreateColor("Pure White", "#FFFFFF");
        Color colBlack = getOrCreateColor("Midnight Black", "#111111");
        Color colNavy = getOrCreateColor("Navy Blue", "#002B49");
        Color colAmber = getOrCreateColor("Golden Amber", "#FFC300");
        Color colOlive = getOrCreateColor("Olive Green", "#556B2F");
        Color colRed = getOrCreateColor("Crimson Red", "#990000");
        Color colBeige = getOrCreateColor("Warm Beige", "#D4B996");
        Color colSky = getOrCreateColor("Sky Blue", "#87CEEB");

        // 5. Suppliers
        Supplier supSilk = getOrCreateSupplier("Royal Silk & Cotton Garment Ltd", "+855 23 881 234", "contact@royalsilk.com", "Building 12, Sen Sok, Phnom Penh", "ACTIVE");
        Supplier supPacific = getOrCreateSupplier("Pacific Denim & Casuals Co.", "+66 2 456 7890", "sales@pacificdenim.com", "Sukhumvit Road, Bangkok", "ACTIVE");
        Supplier supTokyo = getOrCreateSupplier("Tokyo Streetwear & Urban Imports", "+81 3 1234 5678", "info@tokyostreet.jp", "Shibuya-ku, Tokyo", "ACTIVE");
        Supplier supEuro = getOrCreateSupplier("EuroStyle Tailored Fashion Wholesale", "+39 02 87654321", "orders@eurostyle.it", "Via Montenapoleone, Milan", "ACTIVE");
        Supplier supLotus = getOrCreateSupplier("Golden Lotus Textile Mills", "+84 24 3987 6543", "export@goldenlotus.vn", "Hoan Kiem, Hanoi", "ACTIVE");

        // 6. Employees
        Employee empManager = getOrCreateEmployee("Chanthy Sok", "Male", "012 334 556", "chanthy.sok@mystyle.com", "Store Manager", "BKK1, Phnom Penh", LocalDate.of(2023, 1, 15), "ACTIVE");
        Employee empCashier = getOrCreateEmployee("Vicheka Ly", "Female", "098 776 554", "vicheka.ly@mystyle.com", "Lead Cashier", "Toul Kork, Phnom Penh", LocalDate.of(2023, 6, 1), "ACTIVE");
        Employee empSales = getOrCreateEmployee("Rithy Heng", "Male", "017 889 900", "rithy.heng@mystyle.com", "Sales Associate", "Chbar Ampov, Phnom Penh", LocalDate.of(2024, 2, 10), "ACTIVE");
        Employee empInventory = getOrCreateEmployee("Bopha Chea", "Female", "070 112 233", "bopha.chea@mystyle.com", "Inventory Clerk", "Sen Sok, Phnom Penh", LocalDate.of(2024, 3, 20), "ACTIVE");

        // 7. User Accounts
        getOrCreateUserAccount("admin", demoAdminPassword, empManager, adminRole);
        getOrCreateUserAccount("cashier", demoCashierPassword, empCashier, cashierRole);

        // 8. Customers
        List<Customer> customers = new ArrayList<>();
        customers.add(getOrCreateCustomer("Sophea Meas", "Female", "011 223 344", "sophea.meas@gmail.com", "BKK1, Phnom Penh"));
        customers.add(getOrCreateCustomer("Dara Pich", "Male", "012 445 566", "dara.pich@gmail.com", "Toul Kork, Phnom Penh"));
        customers.add(getOrCreateCustomer("Rathana Som", "Male", "015 667 788", "rathana.som@outlook.com", "Sen Sok, Phnom Penh"));
        customers.add(getOrCreateCustomer("Kalyan Chhim", "Female", "093 889 900", "kalyan.chhim@yahoo.com", "Riverside, Phnom Penh"));
        customers.add(getOrCreateCustomer("Minea Keo", "Male", "089 112 233", "minea.keo@gmail.com", "Siem Reap Central"));
        customers.add(getOrCreateCustomer("Sreynich Heng", "Female", "077 334 455", "sreynich.heng@gmail.com", "Battambang City"));
        customers.add(getOrCreateCustomer("Vannak Ouk", "Male", "096 556 677", "vannak.ouk@hotmail.com", "Toul Tom Poung, Phnom Penh"));
        customers.add(getOrCreateCustomer("Channary Yun", "Female", "088 778 899", "channary.yun@gmail.com", "Chroy Changvar, Phnom Penh"));

        // 9. Products & Variants (Seed 20+ rich products)
        if (productRepository.count() < 12) {
            log.info("Seeding extensive catalog of products and variants...");

            // Product 1: Classic Oxford Cotton Shirt
            Product p1 = createProduct("Classic Oxford Cotton Shirt", catMen, "Breathable premium long-sleeve oxford shirt for business and casual.", "https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=600&auto=format&fit=crop&q=80");
            createVariant(p1, sizeM, colWhite, "OXF-WHT-M", new BigDecimal("12.00"), new BigDecimal("28.00"), 45);
            createVariant(p1, sizeL, colWhite, "OXF-WHT-L", new BigDecimal("12.00"), new BigDecimal("28.00"), 38);
            createVariant(p1, sizeM, colNavy, "OXF-NAV-M", new BigDecimal("12.50"), new BigDecimal("29.00"), 25);
            createVariant(p1, sizeL, colNavy, "OXF-NAV-L", new BigDecimal("12.50"), new BigDecimal("29.00"), 4); // Low stock

            // Product 2: Slim Fit Linen Button-Down
            Product p2 = createProduct("Slim Fit Linen Button-Down", catMen, "Lightweight breathable 100% pure linen shirt.", "https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=600&auto=format&fit=crop&q=80");
            createVariant(p2, sizeM, colBeige, "LIN-BEI-M", new BigDecimal("15.00"), new BigDecimal("34.00"), 30);
            createVariant(p2, sizeL, colBeige, "LIN-BEI-L", new BigDecimal("15.00"), new BigDecimal("34.00"), 18);
            createVariant(p2, sizeM, colSky, "LIN-SKY-M", new BigDecimal("15.00"), new BigDecimal("34.00"), 5); // Low stock

            // Product 3: Floral Silk Summer Dress
            Product p3 = createProduct("Floral Silk Summer Dress", catWomen, "Handcrafted elegant floral midi dress made from soft silk blend.", "https://images.unsplash.com/photo-1572804013309-59a88b7e92f1?w=600&auto=format&fit=crop&q=80");
            createVariant(p3, sizeS, colAmber, "DRS-AMB-S", new BigDecimal("22.00"), new BigDecimal("49.00"), 22);
            createVariant(p3, sizeM, colAmber, "DRS-AMB-M", new BigDecimal("22.00"), new BigDecimal("49.00"), 35);
            createVariant(p3, sizeS, colRed, "DRS-RED-S", new BigDecimal("24.00"), new BigDecimal("52.00"), 3); // Low stock

            // Product 4: Tropical Hawaiian Print Shirt
            Product p4 = createProduct("Tropical Hawaiian Print Shirt", catSummer, "Vibrant floral pattern resort-style vacation shirt.", "https://images.unsplash.com/photo-1523381294911-8d3cead13475?w=600&auto=format&fit=crop&q=80");
            createVariant(p4, sizeM, colAmber, "HAW-YEL-M", new BigDecimal("10.00"), new BigDecimal("24.00"), 50);
            createVariant(p4, sizeL, colAmber, "HAW-YEL-L", new BigDecimal("10.00"), new BigDecimal("24.00"), 42);
            createVariant(p4, sizeXL, colSky, "HAW-SKY-XL", new BigDecimal("10.50"), new BigDecimal("25.00"), 15);

            // Product 5: Raw Selvedge Slim Fit Jeans
            Product p5 = createProduct("Raw Selvedge Slim Fit Jeans", catCasual, "14oz authentic Japanese selvedge denim tailored fit.", "https://images.unsplash.com/photo-1542272604-780c96856592?w=600&auto=format&fit=crop&q=80");
            createVariant(p5, sizeM, colNavy, "JNS-NAV-32", new BigDecimal("25.00"), new BigDecimal("58.00"), 28);
            createVariant(p5, sizeL, colBlack, "JNS-BLK-34", new BigDecimal("25.00"), new BigDecimal("58.00"), 19);

            // Product 6: Tailored Wool Blend Blazer
            Product p6 = createProduct("Tailored Wool Blend Blazer", catOuterwear, "Modern slim fit blazer with notch lapel and horn buttons.", "https://images.unsplash.com/photo-1507679799987-c73779587ccf?w=600&auto=format&fit=crop&q=80");
            createVariant(p6, sizeM, colNavy, "BLZ-NAV-M", new BigDecimal("45.00"), new BigDecimal("115.00"), 14);
            createVariant(p6, sizeL, colBlack, "BLZ-BLK-L", new BigDecimal("45.00"), new BigDecimal("115.00"), 2); // Low stock alert!

            // Product 7: Premium Pima Cotton T-Shirt
            Product p7 = createProduct("Premium Pima Cotton T-Shirt", catCasual, "Ultra-soft heavyweight everyday luxury essential crewneck.", "https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=600&auto=format&fit=crop&q=80");
            createVariant(p7, sizeS, colWhite, "TEE-WHT-S", new BigDecimal("6.00"), new BigDecimal("16.00"), 80);
            createVariant(p7, sizeM, colWhite, "TEE-WHT-M", new BigDecimal("6.00"), new BigDecimal("16.00"), 95);
            createVariant(p7, sizeL, colBlack, "TEE-BLK-L", new BigDecimal("6.00"), new BigDecimal("16.00"), 65);
            createVariant(p7, sizeM, colOlive, "TEE-OLV-M", new BigDecimal("6.50"), new BigDecimal("17.00"), 40);

            // Product 8: Minimalist Wide-Brim Sun Hat
            Product p8 = createProduct("Minimalist Wide-Brim Sun Hat", catAccessories, "Natural woven panama straw hat with genuine leather band.", "https://images.unsplash.com/photo-1576871337622-98d48d1cf531?w=600&auto=format&fit=crop&q=80");
            createVariant(p8, sizeFree, colBeige, "HAT-STR-FR", new BigDecimal("8.00"), new BigDecimal("22.00"), 35);
            createVariant(p8, sizeFree, colBlack, "HAT-BLK-FR", new BigDecimal("8.50"), new BigDecimal("24.00"), 18);

            // Product 9: Full-Grain Leather Dress Belt
            Product p9 = createProduct("Full-Grain Leather Dress Belt", catAccessories, "Handmade vegetable-tanned leather belt with brass buckle.", "https://images.unsplash.com/photo-1624222247344-550fb60583dc?w=600&auto=format&fit=crop&q=80");
            createVariant(p9, sizeFree, colBlack, "BLT-LTH-BLK", new BigDecimal("11.00"), new BigDecimal("29.00"), 26);
            createVariant(p9, sizeFree, colAmber, "BLT-LTH-BRN", new BigDecimal("11.00"), new BigDecimal("29.00"), 15);

            // Product 10: Bohemian Tiered Maxi Skirt
            Product p10 = createProduct("Bohemian Tiered Maxi Skirt", catWomen, "Flowy tiered summer skirt with elasticated waistband.", "https://images.unsplash.com/photo-1583496661160-fb5886a0aaaa?w=600&auto=format&fit=crop&q=80");
            createVariant(p10, sizeS, colAmber, "SKT-AMB-S", new BigDecimal("14.00"), new BigDecimal("35.00"), 20);
            createVariant(p10, sizeM, colOlive, "SKT-OLV-M", new BigDecimal("14.00"), new BigDecimal("35.00"), 24);

            // Product 11: Casual Stretch Chino Shorts
            Product p11 = createProduct("Casual Stretch Chino Shorts", catSummer, "Comfortable cotton-spandex blend flat-front shorts.", "https://images.unsplash.com/photo-1591195853828-11db59a44f6b?w=600&auto=format&fit=crop&q=80");
            createVariant(p11, sizeM, colNavy, "SHT-NAV-M", new BigDecimal("11.00"), new BigDecimal("26.00"), 32);
            createVariant(p11, sizeL, colBeige, "SHT-BEI-L", new BigDecimal("11.00"), new BigDecimal("26.00"), 28);

            // Product 12: Classic Denim Trucker Jacket
            Product p12 = createProduct("Classic Denim Trucker Jacket", catOuterwear, "Iconic heavy vintage wash denim jacket.", "https://images.unsplash.com/photo-1576995853123-5a10305d93c0?w=600&auto=format&fit=crop&q=80");
            createVariant(p12, sizeM, colSky, "JKT-DNM-M", new BigDecimal("30.00"), new BigDecimal("68.00"), 12);
            createVariant(p12, sizeL, colBlack, "JKT-DNM-BLK-L", new BigDecimal("32.00"), new BigDecimal("72.00"), 4); // Low stock
        }

        // 10. Purchases (Seed realistic stock-in purchase orders)
        if (purchaseRepository.count() < 5) {
            log.info("Seeding realistic purchase orders...");
            List<ProductVariant> allVariants = productVariantRepository.findAll();
            if (!allVariants.isEmpty()) {
                createSamplePurchase(supSilk, empManager, LocalDate.now().minusDays(25), allVariants.subList(0, Math.min(4, allVariants.size())));
                createSamplePurchase(supPacific, empInventory, LocalDate.now().minusDays(18), allVariants.subList(2, Math.min(6, allVariants.size())));
                createSamplePurchase(supTokyo, empManager, LocalDate.now().minusDays(10), allVariants.subList(4, Math.min(8, allVariants.size())));
                createSamplePurchase(supEuro, empInventory, LocalDate.now().minusDays(3), allVariants.subList(1, Math.min(5, allVariants.size())));
            }
        }

        // 11. Sales & Transactions (Seed 25+ realistic historical orders)
        if (saleRepository.count() < 10) {
            log.info("Seeding rich customer sales, line items, and payment transactions...");
            List<ProductVariant> allVariants = productVariantRepository.findAll();
            if (!allVariants.isEmpty() && !customers.isEmpty()) {
                Random rnd = new Random(42);
                String[] paymentMethods = {"CASH", "ABA", "CARD", "KHQR", "ACLEDA"};

                for (int dayOffset = 30; dayOffset >= 0; dayOffset--) {
                    int ordersToday = (dayOffset % 3 == 0) ? 2 : 1;
                    for (int i = 0; i < ordersToday; i++) {
                        Customer cust = customers.get(rnd.nextInt(customers.size()));
                        Employee emp = (rnd.nextBoolean()) ? empCashier : empSales;
                        LocalDate saleDate = LocalDate.now().minusDays(dayOffset);

                        Sale sale = new Sale();
                        sale.setCustomer(cust);
                        sale.setEmployee(emp);
                        sale.setSaleDate(saleDate);
                        sale.setStatus("COMPLETED");
                        sale.setNote("In-store purchase #" + (1000 + dayOffset * 10 + i));

                        List<SaleDetail> details = new ArrayList<>();
                        BigDecimal subtotal = BigDecimal.ZERO;

                        int itemCount = 1 + rnd.nextInt(3);
                        for (int k = 0; k < itemCount; k++) {
                            List<ProductVariant> availableVariants = allVariants.stream()
                                    .filter(candidate -> candidate.getStockQty() != null && candidate.getStockQty() > 0)
                                    .toList();
                            if (availableVariants.isEmpty()) {
                                break;
                            }
                            ProductVariant v = availableVariants.get(rnd.nextInt(availableVariants.size()));
                            int qty = Math.min(1 + rnd.nextInt(2), v.getStockQty());
                            BigDecimal unitPrice = v.getSalePrice() != null ? v.getSalePrice() : new BigDecimal("25.00");
                            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));

                            SaleDetail detail = new SaleDetail();
                            detail.setSale(sale);
                            detail.setVariant(v);
                            detail.setQuantity(qty);
                            detail.setUnitPrice(unitPrice);
                            detail.setDiscount(BigDecimal.ZERO);
                            detail.setSubtotal(lineTotal);

                            details.add(detail);
                            subtotal = subtotal.add(lineTotal);
                        }

                        BigDecimal discount = (subtotal.compareTo(new BigDecimal("50.00")) > 0) ? new BigDecimal("5.00") : BigDecimal.ZERO;
                        BigDecimal grandTotal = subtotal.subtract(discount);

                        sale.setSubtotal(subtotal);
                        sale.setDiscount(discount);
                        sale.setGrandTotal(grandTotal);

                        Sale savedSale = saleRepository.save(sale);
                        for (SaleDetail d : details) {
                            d.setSale(savedSale);
                            saleDetailRepository.save(d);

                            // Create stock movement record
                            StockMovement sm = new StockMovement();
                            sm.setVariant(d.getVariant());
                            sm.setMovementType("OUT");
                            sm.setReferenceType("SALE");
                            sm.setReferenceId(savedSale.getSaleId());
                            sm.setQuantity(d.getQuantity());
                            sm.setMovementDate(saleDate.atTime(10 + rnd.nextInt(9), rnd.nextInt(60)));
                            sm.setNote("Sale #" + savedSale.getSaleId());
                            stockMovementRepository.save(sm);

                            d.getVariant().setStockQty(d.getVariant().getStockQty() - d.getQuantity());
                            productVariantRepository.save(d.getVariant());
                        }

                        // Create payment record
                        Payment payment = new Payment();
                        payment.setSale(savedSale);
                        payment.setPaymentDate(saleDate);
                        payment.setAmount(grandTotal);
                        payment.setPaymentMethod(paymentMethods[rnd.nextInt(paymentMethods.length)]);
                        payment.setPaymentStatus("PAID");
                        payment.setReferenceNo("TXN-" + System.currentTimeMillis() + "-" + rnd.nextInt(1000));
                        paymentRepository.save(payment);
                    }
                }
            }
        }

        log.info("Dataset seeding completed successfully! Products: {}, Variants: {}, Sales: {}, Customers: {}",
                productRepository.count(), productVariantRepository.count(), saleRepository.count(), customerRepository.count());
    }

    private AppRole getOrCreateRole(String roleName, String description) {
        return appRoleRepository.findByRoleName(roleName).orElseGet(() -> {
            AppRole r = new AppRole();
            r.setRoleName(roleName);
            r.setDescription(description);
            return appRoleRepository.save(r);
        });
    }

    private Category getOrCreateCategory(String name, String desc) {
        return categoryRepository.findByCategoryName(name).orElseGet(() -> {
            Category c = new Category();
            c.setCategoryName(name);
            c.setDescription(desc);
            return categoryRepository.save(c);
        });
    }

    private ProductSize getOrCreateSize(String name, String desc) {
        return productSizeRepository.findBySizeName(name).orElseGet(() -> {
            ProductSize s = new ProductSize();
            s.setSizeName(name);
            s.setDescription(desc);
            return productSizeRepository.save(s);
        });
    }

    private Color getOrCreateColor(String name, String desc) {
        return colorRepository.findByColorName(name).orElseGet(() -> {
            Color c = new Color();
            c.setColorName(name);
            c.setDescription(desc);
            return colorRepository.save(c);
        });
    }

    private Supplier getOrCreateSupplier(String name, String phone, String email, String address, String status) {
        return supplierRepository.findBySupplierName(name).orElseGet(() -> {
            Supplier s = new Supplier();
            s.setSupplierName(name);
            s.setPhone(phone);
            s.setEmail(email);
            s.setAddress(address);
            s.setStatus(status);
            return supplierRepository.save(s);
        });
    }

    private Employee getOrCreateEmployee(String name, String gender, String phone, String email, String pos, String addr, LocalDate hireDate, String status) {
        return employeeRepository.findByEmail(email).orElseGet(() -> {
            Employee e = new Employee();
            e.setEmployeeName(name);
            e.setGender(gender);
            e.setPhone(phone);
            e.setEmail(email);
            e.setPosition(pos);
            e.setAddress(addr);
            e.setHireDate(hireDate);
            e.setStatus(status);
            return employeeRepository.save(e);
        });
    }

    private Customer getOrCreateCustomer(String name, String gender, String phone, String email, String address) {
        return customerRepository.findByPhone(phone).orElseGet(() -> {
            Customer c = new Customer();
            c.setCustomerName(name);
            c.setGender(gender);
            c.setPhone(phone);
            c.setEmail(email);
            c.setAddress(address);
            return customerRepository.save(c);
        });
    }

    private void getOrCreateUserAccount(String username, String rawPassword, Employee employee, AppRole role) {
        if (!userAccountRepository.existsByUsername(username)) {
            UserAccount account = new UserAccount();
            account.setUsername(username);
            account.setPasswordHash(passwordEncoder.encode(rawPassword));
            account.setEnabled(true);
            account.setEmployee(employee);
            UserAccount saved = userAccountRepository.save(account);

            UserRole userRole = new UserRole();
            userRole.setId(new UserRoleId(saved.getUserId(), role.getRoleId()));
            userRole.setUserAccount(saved);
            userRole.setAppRole(role);
            userRoleRepository.save(userRole);
        }
    }

    private void requireConfiguredPassword(String password, String propertyName) {
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches(".*[a-z].*")
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*\\d.*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalStateException(propertyName
                    + " must be 12-72 bytes with upper-case, lower-case, number, and symbol when demo data is enabled");
        }
    }

    private Product createProduct(String name, Category category, String description, String imageUrl) {
        return productRepository.findByProductNameContainingIgnoreCase(name).stream().findFirst().map(existing -> {
            if (existing.getImageUrl() == null && imageUrl != null) {
                existing.setImageUrl(imageUrl);
                return productRepository.save(existing);
            }
            return existing;
        }).orElseGet(() -> {
            Product p = new Product();
            p.setProductName(name);
            p.setCategory(category);
            p.setDescription(description);
            p.setImageUrl(imageUrl);
            p.setIsActive(true);
            return productRepository.save(p);
        });
    }

    private void createVariant(Product product, ProductSize size, Color color, String sku, BigDecimal cost, BigDecimal sale, int stock) {
        if (productVariantRepository.findBySku(sku).isEmpty()) {
            ProductVariant pv = new ProductVariant();
            pv.setProduct(product);
            pv.setSize(size);
            pv.setColor(color);
            pv.setSku(sku);
            pv.setCostPrice(cost);
            pv.setSalePrice(sale);
            pv.setStockQty(stock);
            ProductVariant saved = productVariantRepository.save(pv);

            StockMovement opening = new StockMovement();
            opening.setVariant(saved);
            opening.setMovementType("IN");
            opening.setReferenceType("OPENING");
            opening.setQuantity(stock);
            opening.setMovementDate(LocalDateTime.now());
            opening.setNote("Demo opening stock");
            stockMovementRepository.save(opening);
        }
    }

    private void createSamplePurchase(Supplier supplier, Employee employee, LocalDate date, List<ProductVariant> variants) {
        Purchase purchase = new Purchase();
        purchase.setSupplier(supplier);
        purchase.setEmployee(employee);
        purchase.setPurchaseDate(date);
        purchase.setStatus("COMPLETED");
        purchase.setNote("Inventory restock batch PO-" + date.toString());

        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseDetail> details = new ArrayList<>();

        for (ProductVariant v : variants) {
            int qty = 20;
            BigDecimal cost = v.getCostPrice() != null ? v.getCostPrice() : new BigDecimal("15.00");
            BigDecimal lineTotal = cost.multiply(BigDecimal.valueOf(qty));

            PurchaseDetail pd = new PurchaseDetail();
            pd.setPurchase(purchase);
            pd.setVariant(v);
            pd.setQuantity(qty);
            pd.setCostPrice(cost);
            pd.setSubtotal(lineTotal);
            details.add(pd);

            total = total.add(lineTotal);
        }

        purchase.setTotalAmount(total);
        Purchase saved = purchaseRepository.save(purchase);

        for (PurchaseDetail pd : details) {
            pd.setPurchase(saved);
            purchaseDetailRepository.save(pd);

            StockMovement sm = new StockMovement();
            sm.setVariant(pd.getVariant());
            sm.setMovementType("IN");
            sm.setReferenceType("PURCHASE");
            sm.setReferenceId(saved.getPurchaseId());
            sm.setQuantity(pd.getQuantity());
            sm.setMovementDate(date.atTime(9, 0));
            sm.setNote("Purchase PO #" + saved.getPurchaseId());
            stockMovementRepository.save(sm);

            pd.getVariant().setStockQty(pd.getVariant().getStockQty() + pd.getQuantity());
            productVariantRepository.save(pd.getVariant());
        }
    }
}
