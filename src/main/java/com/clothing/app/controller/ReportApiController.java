package com.clothing.app.controller;

import com.clothing.app.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportApiController {

    private final ReportService reportService;

    public ReportApiController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping({"/kpi", "/financial", "/summary"})
    public Map<String, Object> getKpi() {
        return reportService.getKpiSummary();
    }

    @GetMapping({"/daily-sales", "/daily"})
    public List<Map<String, Object>> getDailySales() {
        return reportService.getDailySales();
    }

    @GetMapping({"/weekly-sales", "/weekly"})
    public List<Map<String, Object>> getWeeklySales() {
        return reportService.getWeeklySales();
    }

    @GetMapping({"/monthly-sales", "/monthly"})
    public List<Map<String, Object>> getMonthlySales() {
        return reportService.getMonthlySales();
    }

    @GetMapping({"/yearly-sales", "/yearly"})
    public List<Map<String, Object>> getYearlySales() {
        return reportService.getYearlySales();
    }

    @GetMapping({"/cashiers", "/cashier-sales"})
    public List<Map<String, Object>> getCashierSales(@RequestParam(name = "period", required = false, defaultValue = "all") String period) {
        return reportService.getCashierSales(period);
    }

    @GetMapping("/cashiers/{employeeId}/sales")
    public List<Map<String, Object>> getCashierSalesDetails(@PathVariable Long employeeId,
                                                           @RequestParam(name = "period", required = false, defaultValue = "all") String period) {
        return reportService.getCashierSalesDetails(employeeId, period);
    }

    @GetMapping("/dashboard-data")
    public Map<String, Object> getDashboardData(@RequestParam(name = "period", required = false, defaultValue = "daily") String period) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metrics", reportService.getDashboardMetrics(period));
        data.put("trend", reportService.getSalesTrend(period));
        data.put("categorySales", reportService.getCategorySales(period));
        data.put("cashierSales", reportService.getCashierSales(period));
        data.put("topSelling", reportService.getTopSellingProducts(period).stream().limit(6).toList());
        return data;
    }

    @GetMapping({"/product-stock", "/stock"})
    public List<Map<String, Object>> getProductStock() {
        return reportService.getProductStock();
    }

    @GetMapping({"/low-stock", "/low"})
    public List<Map<String, Object>> getLowStock() {
        return reportService.getLowStock();
    }

    @GetMapping({"/stock-movements", "/movements"})
    public List<Map<String, Object>> getStockMovements() {
        return reportService.getStockMovements();
    }

    @GetMapping({"/top-selling", "/top-products"})
    public List<Map<String, Object>> getTopSelling(@RequestParam(name = "period", required = false, defaultValue = "all") String period) {
        return reportService.getTopSellingProducts(period);
    }
}
