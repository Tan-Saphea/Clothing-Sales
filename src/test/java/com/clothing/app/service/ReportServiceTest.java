package com.clothing.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ReportService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new ReportService(jdbcTemplate);
    }

    @Test
    void salesRankingsOnlyUseCompletedSalesAndAllocateOrderDiscount() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        service.getTopSellingProducts();
        service.getCategorySales();

        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("s.STATUS = 'COMPLETED'")
                && sql.contains("s.GRAND_TOTAL / s.SUBTOTAL")
                && sql.contains("FROM SALE_DETAIL")));
        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("s.STATUS = 'COMPLETED'")
                && sql.contains("s.GRAND_TOTAL / s.SUBTOTAL")
                && sql.contains("FROM CATEGORY")));
    }

    @Test
    void getKpiSummaryCalculatesAllMetricsCorrectly() {
        when(jdbcTemplate.queryForObject(eq("SELECT COALESCE(SUM(NET_SALES), 0) FROM V_DAILY_SALES"), eq(Number.class)))
                .thenReturn(new BigDecimal("9046.91"));
        when(jdbcTemplate.queryForObject(eq("SELECT COALESCE(SUM(TOTAL_SALES), 0) FROM V_DAILY_SALES"), eq(Number.class)))
                .thenReturn(62);
        when(jdbcTemplate.queryForObject(eq("SELECT COALESCE(SUM(STOCK_COST_VALUE), 0) FROM V_PRODUCT_STOCK WHERE IS_ACTIVE = 'Y'"), eq(Number.class)))
                .thenReturn(new BigDecimal("88493.00"));
        when(jdbcTemplate.queryForObject(eq("SELECT COALESCE(SUM(STOCK_SALE_VALUE), 0) FROM V_PRODUCT_STOCK WHERE IS_ACTIVE = 'Y'"), eq(Number.class)))
                .thenReturn(new BigDecimal("161353.72"));
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM PRODUCT_VARIANT pv JOIN PRODUCT p ON pv.PRODUCT_ID = p.PRODUCT_ID WHERE pv.STOCK_QTY <= 10 AND p.IS_ACTIVE = 'Y'"), eq(Number.class)))
                .thenReturn(15);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM V_PRODUCT_STOCK WHERE IS_ACTIVE = 'Y'"), eq(Number.class)))
                .thenReturn(48);

        Map<String, Object> kpi = service.getKpiSummary();

        assertNotNull(kpi);
        assertEquals(new BigDecimal("9046.91"), kpi.get("totalNetSales"));
        assertEquals(62, kpi.get("totalOrders"));
        assertEquals(new BigDecimal("88493.00"), kpi.get("totalInventoryCostValue"));
        assertEquals(new BigDecimal("161353.72"), kpi.get("totalInventorySaleValue"));
        assertEquals(15, kpi.get("lowStockCount"));
        assertEquals(48, kpi.get("totalVariants"));
    }

    @Test
    void getKpiSummaryHandlesNullDatabaseResultsGracefully() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Number.class))).thenReturn(null);

        Map<String, Object> kpi = service.getKpiSummary();

        assertNotNull(kpi);
        assertEquals(0, kpi.get("totalNetSales"));
        assertEquals(0, kpi.get("totalOrders"));
        assertEquals(0, kpi.get("totalInventoryCostValue"));
        assertEquals(0, kpi.get("totalInventorySaleValue"));
        assertEquals(0, kpi.get("lowStockCount"));
        assertEquals(0, kpi.get("totalVariants"));
    }

    @Test
    void getDailySalesQueriesViewSortedDesc() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("SALE_DAY", "2026-09-25", "TOTAL_SALES", 1, "NET_SALES", new BigDecimal("1200.00"))
        ));

        List<Map<String, Object>> result = service.getDailySales();

        assertEquals(1, result.size());
        assertEquals("2026-09-25", result.get(0).get("SALE_DAY"));
        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("FROM V_DAILY_SALES")
                && sql.contains("ORDER BY SALE_DAY DESC")));
    }

    @Test
    void getMonthlySalesQueriesViewSortedDesc() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("SALE_MONTH", "2026-09", "TOTAL_SALES", 43, "NET_SALES", new BigDecimal("6557.91"))
        ));

        List<Map<String, Object>> result = service.getMonthlySales();

        assertEquals(1, result.size());
        assertEquals("2026-09", result.get(0).get("SALE_MONTH"));
        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("FROM V_MONTHLY_SALES")
                && sql.contains("ORDER BY SALE_MONTH DESC")));
    }

    @Test
    void getWeeklySalesFallsBackToPortableQueryOnOracleDialectFailure() {
        when(jdbcTemplate.queryForList(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("TRUNC(SALE_DATE, 'IW')")) {
                throw new RuntimeException("TRUNC with 'IW' format specifier not supported in non-Oracle DB");
            }
            return List.of(Map.of("WEEK_START", "2026-09-25", "TOTAL_SALES", 2));
        });

        List<Map<String, Object>> result = service.getWeeklySales();

        assertEquals(1, result.size());
        assertEquals("2026-09-25", result.get(0).get("WEEK_START"));
    }

    @Test
    void getProductStockQueriesActiveAndInactiveCatalogWithValuations() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("SKU", "TS-BLK-M", "STOCK_QTY", 18, "STOCK_COST_VALUE", new BigDecimal("90.00"))
        ));

        List<Map<String, Object>> result = service.getProductStock();

        assertEquals(1, result.size());
        assertEquals("TS-BLK-M", result.get(0).get("SKU"));
        verify(jdbcTemplate).queryForList(argThat((String sql) -> sql != null && sql.contains("FROM V_PRODUCT_STOCK")
                && sql.contains("ORDER BY PRODUCT_NAME, SKU")));
    }

    @Test
    void getLowStockFiltersByThresholdTenAndActiveProductsOnly() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("SKU", "JN-BLU-M", "STOCK_QTY", 0, "PRODUCT_NAME", "Slim Jeans")
        ));

        List<Map<String, Object>> result = service.getLowStock();

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).get("STOCK_QTY"));
        verify(jdbcTemplate).queryForList(argThat((String sql) -> sql != null && sql.contains("pv.STOCK_QTY <= 10")
                && sql.contains("p.IS_ACTIVE = 'Y'")
                && sql.contains("ORDER BY pv.STOCK_QTY ASC")));
    }

    @Test
    void getStockMovementsIncludesSignedQuantityAndSortsByLatestFirst() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("MOVEMENT_ID", 100L, "SIGNED_QUANTITY", -3, "MOVEMENT_TYPE", "OUT")
        ));

        List<Map<String, Object>> result = service.getStockMovements();

        assertEquals(1, result.size());
        assertEquals(-3, result.get(0).get("SIGNED_QUANTITY"));
        verify(jdbcTemplate).queryForList(argThat((String sql) -> sql != null && sql.contains("FROM V_STOCK_MOVEMENT")
                && sql.contains("ORDER BY MOVEMENT_DATE DESC")));
    }

    @Test
    void getStockHealthCountsStockBucketsCorrectly() {
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM PRODUCT_VARIANT pv JOIN PRODUCT p ON pv.PRODUCT_ID = p.PRODUCT_ID WHERE p.IS_ACTIVE = 'Y' AND pv.STOCK_QTY > 10"), eq(Number.class))).thenReturn(35);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM PRODUCT_VARIANT pv JOIN PRODUCT p ON pv.PRODUCT_ID = p.PRODUCT_ID WHERE p.IS_ACTIVE = 'Y' AND pv.STOCK_QTY > 0 AND pv.STOCK_QTY <= 10"), eq(Number.class))).thenReturn(8);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM PRODUCT_VARIANT pv JOIN PRODUCT p ON pv.PRODUCT_ID = p.PRODUCT_ID WHERE p.IS_ACTIVE = 'Y' AND pv.STOCK_QTY <= 0"), eq(Number.class))).thenReturn(5);

        Map<String, Object> health = service.getStockHealth();

        assertNotNull(health);
        assertEquals(35, health.get("inStock"));
        assertEquals(8, health.get("lowStock"));
        assertEquals(5, health.get("outOfStock"));
    }

    @Test
    void getDashboardMetricsHandlesZeroOrdersWithoutDivisionByZero() {
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
                "TOTAL_ORDERS", 0L,
                "SUBTOTAL", BigDecimal.ZERO,
                "TOTAL_DISCOUNT", BigDecimal.ZERO,
                "NET_SALES", BigDecimal.ZERO,
                "ACTIVE_CASHIERS", 0
        ));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        Map<String, Object> metrics = service.getDashboardMetrics("daily");

        assertNotNull(metrics);
        assertEquals(0L, metrics.get("totalOrders"));
        assertEquals(BigDecimal.ZERO, metrics.get("totalNetSales"));
        assertEquals(BigDecimal.ZERO, metrics.get("avgOrderValue"));
        assertEquals("N/A", metrics.get("topCashierName"));
        assertEquals(BigDecimal.ZERO, metrics.get("topCashierRevenue"));
    }
}
