package com.clothing.app.controller;

import com.clothing.app.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportApiControllerTest {

    private ReportService reportService;
    private ReportApiController controller;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        controller = new ReportApiController(reportService);
    }

    @Test
    void getKpiDelegatesToReportService() {
        Map<String, Object> mockKpi = Map.of(
                "totalNetSales", new BigDecimal("12500.50"),
                "totalOrders", 45,
                "totalInventoryCostValue", new BigDecimal("45000.00"),
                "totalInventorySaleValue", new BigDecimal("75000.00"),
                "lowStockCount", 3,
                "totalVariants", 20
        );
        when(reportService.getKpiSummary()).thenReturn(mockKpi);

        Map<String, Object> result = controller.getKpi();
        assertEquals(mockKpi, result);
        verify(reportService).getKpiSummary();
    }

    @Test
    void getDailySalesDelegatesToReportService() {
        List<Map<String, Object>> mockDaily = List.of(
                Map.of("SALE_DAY", "2026-09-25", "TOTAL_SALES", 5, "NET_SALES", new BigDecimal("500.00"))
        );
        when(reportService.getDailySales()).thenReturn(mockDaily);

        List<Map<String, Object>> result = controller.getDailySales();
        assertEquals(1, result.size());
        assertEquals("2026-09-25", result.get(0).get("SALE_DAY"));
        verify(reportService).getDailySales();
    }

    @Test
    void getWeeklySalesDelegatesToReportService() {
        List<Map<String, Object>> mockWeekly = List.of(
                Map.of("WEEK_START", "2026-09-21", "WEEK_END", "2026-09-27", "TOTAL_SALES", 20)
        );
        when(reportService.getWeeklySales()).thenReturn(mockWeekly);

        List<Map<String, Object>> result = controller.getWeeklySales();
        assertEquals(1, result.size());
        assertEquals("2026-09-21", result.get(0).get("WEEK_START"));
        verify(reportService).getWeeklySales();
    }

    @Test
    void getMonthlySalesDelegatesToReportService() {
        List<Map<String, Object>> mockMonthly = List.of(
                Map.of("SALE_MONTH", "2026-09", "TOTAL_SALES", 80)
        );
        when(reportService.getMonthlySales()).thenReturn(mockMonthly);

        List<Map<String, Object>> result = controller.getMonthlySales();
        assertEquals(1, result.size());
        assertEquals("2026-09", result.get(0).get("SALE_MONTH"));
        verify(reportService).getMonthlySales();
    }

    @Test
    void getYearlySalesDelegatesToReportService() {
        List<Map<String, Object>> mockYearly = List.of(
                Map.of("SALE_YEAR", "2026", "TOTAL_SALES", 500)
        );
        when(reportService.getYearlySales()).thenReturn(mockYearly);

        List<Map<String, Object>> result = controller.getYearlySales();
        assertEquals(1, result.size());
        assertEquals("2026", result.get(0).get("SALE_YEAR"));
        verify(reportService).getYearlySales();
    }

    @Test
    void getCashierSalesDelegatesWithPeriod() {
        List<Map<String, Object>> mockCashiers = List.of(
                Map.of("EMPLOYEE_ID", 1L, "EMPLOYEE_NAME", "Vicheka Ly", "TOTAL_ORDERS", 15)
        );
        when(reportService.getCashierSales("monthly")).thenReturn(mockCashiers);

        List<Map<String, Object>> result = controller.getCashierSales("monthly");
        assertEquals(1, result.size());
        assertEquals("Vicheka Ly", result.get(0).get("EMPLOYEE_NAME"));
        verify(reportService).getCashierSales("monthly");
    }

    @Test
    void getCashierSalesDetailsDelegatesWithEmployeeIdAndPeriod() {
        List<Map<String, Object>> mockOrders = List.of(
                Map.of("SALE_ID", 101L, "GRAND_TOTAL", new BigDecimal("120.00"))
        );
        when(reportService.getCashierSalesDetails(2L, "weekly")).thenReturn(mockOrders);

        List<Map<String, Object>> result = controller.getCashierSalesDetails(2L, "weekly");
        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).get("SALE_ID"));
        verify(reportService).getCashierSalesDetails(2L, "weekly");
    }

    @Test
    void getDashboardDataAggregatesMetricsTrendsCategoriesAndCashiers() {
        Map<String, Object> metrics = Map.of("totalOrders", 10L, "totalNetSales", new BigDecimal("1000.00"));
        List<Map<String, Object>> trend = List.of(Map.of("LABEL", "12:00", "NET_SALES", new BigDecimal("200.00")));
        List<Map<String, Object>> categories = List.of(Map.of("CATEGORY_NAME", "T-Shirts", "TOTAL_REVENUE", new BigDecimal("500.00")));
        List<Map<String, Object>> cashiers = List.of(Map.of("EMPLOYEE_NAME", "Vicheka Ly", "NET_REVENUE", new BigDecimal("800.00")));
        List<Map<String, Object>> topSelling = List.of(
                Map.of("PRODUCT_NAME", "P1"), Map.of("PRODUCT_NAME", "P2"),
                Map.of("PRODUCT_NAME", "P3"), Map.of("PRODUCT_NAME", "P4"),
                Map.of("PRODUCT_NAME", "P5"), Map.of("PRODUCT_NAME", "P6"),
                Map.of("PRODUCT_NAME", "P7")
        );

        when(reportService.getDashboardMetrics("daily")).thenReturn(metrics);
        when(reportService.getSalesTrend("daily")).thenReturn(trend);
        when(reportService.getCategorySales("daily")).thenReturn(categories);
        when(reportService.getCashierSales("daily")).thenReturn(cashiers);
        when(reportService.getTopSellingProducts("daily")).thenReturn(topSelling);

        Map<String, Object> result = controller.getDashboardData("daily");
        assertNotNull(result);
        assertEquals(metrics, result.get("metrics"));
        assertEquals(trend, result.get("trend"));
        assertEquals(categories, result.get("categorySales"));
        assertEquals(cashiers, result.get("cashierSales"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> limitedTopSelling = (List<Map<String, Object>>) result.get("topSelling");
        assertEquals(6, limitedTopSelling.size(), "Top selling should be capped at 6 items for dashboard widgets");
    }

    @Test
    void getProductStockDelegatesToReportService() {
        when(reportService.getProductStock()).thenReturn(List.of(Map.of("SKU", "TS-01")));
        List<Map<String, Object>> result = controller.getProductStock();
        assertEquals(1, result.size());
        verify(reportService).getProductStock();
    }

    @Test
    void getLowStockDelegatesToReportService() {
        when(reportService.getLowStock()).thenReturn(List.of(Map.of("SKU", "TS-01", "STOCK_QTY", 2)));
        List<Map<String, Object>> result = controller.getLowStock();
        assertEquals(1, result.size());
        verify(reportService).getLowStock();
    }

    @Test
    void getStockMovementsDelegatesToReportService() {
        when(reportService.getStockMovements()).thenReturn(List.of(Map.of("MOVEMENT_ID", 1L)));
        List<Map<String, Object>> result = controller.getStockMovements();
        assertEquals(1, result.size());
        verify(reportService).getStockMovements();
    }

    @Test
    void getTopSellingDelegatesWithPeriod() {
        when(reportService.getTopSellingProducts("yearly")).thenReturn(List.of(Map.of("PRODUCT_NAME", "Shirt")));
        List<Map<String, Object>> result = controller.getTopSelling("yearly");
        assertEquals(1, result.size());
        verify(reportService).getTopSellingProducts("yearly");
    }
}
