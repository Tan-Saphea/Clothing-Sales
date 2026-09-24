package com.clothing.app.controller;

import com.clothing.app.entity.Color;
import com.clothing.app.entity.Employee;
import com.clothing.app.entity.ProductSize;
import com.clothing.app.repository.ColorRepository;
import com.clothing.app.repository.EmployeeRepository;
import com.clothing.app.repository.ProductSizeRepository;
import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.service.CategoryService;
import com.clothing.app.service.CustomerService;
import com.clothing.app.service.ProductService;
import com.clothing.app.service.ProductVariantService;
import com.clothing.app.service.PaymentService;
import com.clothing.app.service.PurchaseService;
import com.clothing.app.service.ReportService;
import com.clothing.app.service.SaleService;
import com.clothing.app.service.SupplierService;
import com.clothing.app.security.CurrentUserAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@Controller
public class PageController {

    private final CategoryService categoryService;
    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final SupplierService supplierService;
    private final CustomerService customerService;
    private final PurchaseService purchaseService;
    private final SaleService saleService;
    private final ProductSizeRepository productSizeRepository;
    private final ColorRepository colorRepository;
    private final EmployeeRepository employeeRepository;
    private final ReportService reportService;
    private final UserAccountRepository userAccountRepository;
    private final PaymentService paymentService;
    private final CurrentUserAccessService currentUserAccessService;

    public PageController(CategoryService categoryService,
                          ProductService productService,
                          ProductVariantService productVariantService,
                          SupplierService supplierService,
                          CustomerService customerService,
                          PurchaseService purchaseService,
                          SaleService saleService,
                          ProductSizeRepository productSizeRepository,
                          ColorRepository colorRepository,
                          EmployeeRepository employeeRepository,
                          ReportService reportService,
                          UserAccountRepository userAccountRepository,
                          PaymentService paymentService,
                          CurrentUserAccessService currentUserAccessService) {
        this.categoryService = categoryService;
        this.productService = productService;
        this.productVariantService = productVariantService;
        this.supplierService = supplierService;
        this.customerService = customerService;
        this.purchaseService = purchaseService;
        this.saleService = saleService;
        this.productSizeRepository = productSizeRepository;
        this.colorRepository = colorRepository;
        this.employeeRepository = employeeRepository;
        this.reportService = reportService;
        this.userAccountRepository = userAccountRepository;
        this.paymentService = paymentService;
        this.currentUserAccessService = currentUserAccessService;
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    @ModelAttribute("isCashier")
    public boolean isCashier(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CASHIER".equals(authority.getAuthority()));
    }

    @ModelAttribute("currentUsername")
    public String currentUsername(Authentication authentication) {
        return authentication != null ? authentication.getName() : "User";
    }

    @GetMapping("/")
    public String home(Authentication authentication) {
        boolean cashier = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CASHIER".equals(authority.getAuthority()));
        return cashier ? "redirect:/sales" : "redirect:/dashboard";
    }

    @Transactional(readOnly = true)
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("categoryCount", categoryService.findAll().size());
        model.addAttribute("productCount", productService.findAll().size());
        model.addAttribute("supplierCount", supplierService.findAll().size());
        model.addAttribute("customerCount", customerService.findAll().size());
        model.addAttribute("purchaseCount", purchaseService.findAll().size());
        model.addAttribute("saleCount", saleService.findAll().size());

        try {
            model.addAttribute("kpi", reportService.getKpiSummary());
            model.addAttribute("dashboardMetrics", reportService.getDashboardMetrics("daily"));
            model.addAttribute("dailySales", reportService.getSalesTrend("daily"));
            model.addAttribute("categorySales", reportService.getCategorySales("daily"));
            model.addAttribute("stockHealth", reportService.getStockHealth());
            model.addAttribute("topSelling", reportService.getTopSellingProducts("daily").stream().limit(6).toList());
            model.addAttribute("recentDailySales", reportService.getDailySales().stream().limit(6).toList());
            model.addAttribute("cashierSales", reportService.getCashierSales("daily"));
            model.addAttribute("lowStockItems", reportService.getLowStock().stream().limit(6).toList());
        } catch (Exception ex) {
            model.addAttribute("kpi", Map.of());
            model.addAttribute("dashboardMetrics", Map.of());
            model.addAttribute("dailySales", List.of());
            model.addAttribute("categorySales", List.of());
            model.addAttribute("stockHealth", Map.of("inStock", 0, "lowStock", 0, "outOfStock", 0));
            model.addAttribute("topSelling", List.of());
            model.addAttribute("recentDailySales", List.of());
            model.addAttribute("cashierSales", List.of());
            model.addAttribute("lowStockItems", List.of());
        }

        return "dashboard/index";
    }

    @Transactional(readOnly = true)
    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        return "category/list";
    }

    @Transactional(readOnly = true)
    @GetMapping("/products")
    public String products(Model model,
                           @RequestParam(name = "showInactive", defaultValue = "false") boolean showInactive,
                           @RequestParam(name = "status", required = false) String statusParam) {
        List<Map<String, Object>> rows = productService.findAll().stream()
                .map(product -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", product.getProductId());
            row.put("productName", product.getProductName());
            row.put("description", product.getDescription());
            row.put("categoryId", product.getCategory() != null ? product.getCategory().getCategoryId() : null);
            row.put("categoryName", product.getCategory() != null ? product.getCategory().getCategoryName() : "Unassigned");
            String imgUrl = product.getImageUrl();
            if (imgUrl != null && !imgUrl.isBlank()) {
                imgUrl = imgUrl.trim();
                if (imgUrl.startsWith("uploads/")) {
                    imgUrl = "/" + imgUrl;
                }
            } else {
                imgUrl = null;
            }
            row.put("imageUrl", imgUrl);
            row.put("isActive", product.getIsActive() != null ? product.getIsActive() : true);
            return row;
        }).toList();

        long activeCount = rows.stream().filter(r -> Boolean.TRUE.equals(r.get("isActive"))).count();
        long inactiveCount = rows.size() - activeCount;

        String initialStatus = "all";
        if ("active".equalsIgnoreCase(statusParam)) {
            initialStatus = "active";
        } else if ("inactive".equalsIgnoreCase(statusParam)) {
            initialStatus = "inactive";
        }

        model.addAttribute("products", rows);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("inactiveCount", inactiveCount);
        model.addAttribute("totalCount", rows.size());
        model.addAttribute("initialStatus", initialStatus);
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("sizes", productSizeRepository.findAll());
        model.addAttribute("colors", colorRepository.findAll());
        return "product/list";
    }

    @Transactional(readOnly = true)
    @GetMapping("/suppliers")
    public String suppliers(Model model) {
        model.addAttribute("suppliers", supplierService.findAll());
        return "supplier/list";
    }

    @Transactional(readOnly = true)
    @GetMapping("/customers")
    public String customers(Model model) {
        model.addAttribute("customers", customerService.findAll());
        return "customer/list";
    }

    @Transactional(readOnly = true)
    @GetMapping("/purchases")
    public String purchases(Model model) {
        List<Map<String, Object>> rows = purchaseService.findAll().stream().map(purchase -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("purchaseId", purchase.getPurchaseId());
            row.put("supplierName", purchase.getSupplier() != null ? purchase.getSupplier().getSupplierName() : "N/A");
            row.put("employeeName", purchase.getEmployee() != null ? purchase.getEmployee().getEmployeeName() : "N/A");
            row.put("purchaseDate", purchase.getPurchaseDate());
            row.put("status", purchase.getStatus());
            row.put("totalAmount", purchase.getTotalAmount());
            row.put("note", purchase.getNote());
            return row;
        }).toList();
        model.addAttribute("purchases", rows);
        model.addAttribute("suppliers", supplierService.findAll().stream()
                .filter(s -> "ACTIVE".equals(s.getStatus())).toList());
        model.addAttribute("employees", employeeRepository.findAll().stream()
                .filter(e -> "ACTIVE".equals(e.getStatus())).toList());
        model.addAttribute("variants", productVariantService.findAll());
        return "purchase/list";
    }

    @Transactional(readOnly = true)
    @GetMapping("/sales")
    public String sales(Model model, Authentication authentication) {
        List<com.clothing.app.entity.Sale> visibleSales = isAdmin(authentication)
                ? saleService.findAll()
                : saleService.findAllForEmployee(currentUserAccessService.resolveCurrentEmployeeId(authentication));
        List<Long> visibleSaleIds = visibleSales.stream()
                .map(com.clothing.app.entity.Sale::getSaleId)
                .toList();
        Map<Long, BigDecimal> paidBySale = paymentService.findPaidAmountsForSales(visibleSaleIds);

        List<Map<String, Object>> rows = visibleSales.stream().map(sale -> {
            Map<String, Object> row = new LinkedHashMap<>();
            BigDecimal grandTotal = sale.getGrandTotal() == null ? BigDecimal.ZERO : sale.getGrandTotal();
            BigDecimal paidAmount = paidBySale.getOrDefault(sale.getSaleId(), BigDecimal.ZERO);
            BigDecimal remainingBalance = grandTotal.subtract(paidAmount).max(BigDecimal.ZERO);
            row.put("saleId", sale.getSaleId());
            row.put("customerName", sale.getCustomer() != null ? sale.getCustomer().getCustomerName() : "Walk-in");
            row.put("employeeName", sale.getEmployee() != null ? sale.getEmployee().getEmployeeName() : "N/A");
            row.put("saleDate", sale.getSaleDate());
            row.put("status", sale.getStatus());
            row.put("subtotal", sale.getSubtotal());
            row.put("discount", sale.getDiscount());
            row.put("grandTotal", grandTotal);
            row.put("paidAmount", paidAmount);
            row.put("remainingBalance", remainingBalance);
            row.put("paymentStatus", paidAmount.signum() == 0 ? "UNPAID"
                    : remainingBalance.signum() == 0 ? "PAID" : "PARTIAL");
            row.put("note", sale.getNote());
            return row;
        }).toList();
        model.addAttribute("sales", rows);
        model.addAttribute("saleOrderCount", rows.size());
        model.addAttribute("saleAwaitingPaymentCount", rows.stream()
                .filter(row -> "COMPLETED".equals(row.get("status")))
                .filter(row -> ((BigDecimal) row.get("remainingBalance")).signum() > 0)
                .count());
        model.addAttribute("saleOutstandingTotal", rows.stream()
                .filter(row -> "COMPLETED".equals(row.get("status")))
                .map(row -> (BigDecimal) row.get("remainingBalance"))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("customers", customerService.findAll());
        List<Employee> activeEmployees = employeeRepository.findAll().stream()
                .filter(e -> "ACTIVE".equalsIgnoreCase(e.getStatus())).toList();
        if (!isAdmin(authentication)) {
            activeEmployees = userAccountRepository.findByUsername(authentication.getName().trim().toLowerCase())
                    .map(account -> account.getEmployee() != null
                            && "ACTIVE".equalsIgnoreCase(account.getEmployee().getStatus())
                            ? List.of(account.getEmployee()) : List.<Employee>of())
                    .orElse(List.of());
        }
        model.addAttribute("employees", activeEmployees);
        model.addAttribute("categories", categoryService.findAll());
        return "sale/list";
    }

    @Transactional(readOnly = true)
    @GetMapping("/stock")
    public String stock(Model model, Authentication authentication) {
        try {
            model.addAttribute("stockList", reportService.getProductStock());
            model.addAttribute("lowStockList", reportService.getLowStock());
            model.addAttribute("movementList", reportService.getStockMovements().stream().limit(50).toList());
        } catch (Exception ex) {
            model.addAttribute("stockList", List.of());
            model.addAttribute("lowStockList", List.of());
            model.addAttribute("movementList", List.of());
        }
        model.addAttribute("suppliers", supplierService.findAll().stream()
                .filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus())).toList());
        List<Employee> activeEmployees = employeeRepository.findAll().stream()
                .filter(e -> "ACTIVE".equalsIgnoreCase(e.getStatus())).toList();
        model.addAttribute("employees", activeEmployees);
        Long currentEmpId = null;
        try {
            if (authentication != null) {
                currentEmpId = currentUserAccessService.resolveCurrentEmployeeId(authentication);
            }
        } catch (Exception ignored) {
            // Admin user might not have a linked employee record
        }
        model.addAttribute("currentEmployeeId", currentEmpId);
        return "stock/list";
    }

    @Transactional(readOnly = true)
    @GetMapping("/reports")
    public String reports(Model model) {
        try {
            model.addAttribute("kpi", reportService.getKpiSummary());
            model.addAttribute("dailySales", reportService.getDailySales());
            model.addAttribute("weeklySales", reportService.getWeeklySales());
            model.addAttribute("monthlySales", reportService.getMonthlySales());
            model.addAttribute("yearlySales", reportService.getYearlySales());
            model.addAttribute("cashierSales", reportService.getCashierSales("all"));
            model.addAttribute("productStock", reportService.getProductStock());
            model.addAttribute("lowStock", reportService.getLowStock());
            model.addAttribute("stockMovements", reportService.getStockMovements().stream().limit(100).toList());
        } catch (Exception ex) {
            model.addAttribute("kpi", Map.of());
            model.addAttribute("dailySales", List.of());
            model.addAttribute("weeklySales", List.of());
            model.addAttribute("monthlySales", List.of());
            model.addAttribute("yearlySales", List.of());
            model.addAttribute("cashierSales", List.of());
            model.addAttribute("productStock", List.of());
            model.addAttribute("lowStock", List.of());
            model.addAttribute("stockMovements", List.of());
        }
        return "report/index";
    }
}
