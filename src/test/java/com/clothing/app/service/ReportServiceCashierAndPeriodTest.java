package com.clothing.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ReportServiceCashierAndPeriodTest {

    private JdbcTemplate jdbcTemplate;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        reportService = new ReportService(jdbcTemplate);
    }

    @Test
    void testCalculateDateRange_Periods() {
        LocalDate today = LocalDate.now();

        // Daily
        ReportService.DateRange daily = reportService.calculateDateRange("daily");
        assertEquals(today, daily.startDate());
        assertEquals(today.plusDays(1), daily.endDate());

        // Weekly
        ReportService.DateRange weekly = reportService.calculateDateRange("weekly");
        LocalDate expectedMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertEquals(expectedMonday, weekly.startDate());
        assertEquals(expectedMonday.plusDays(7), weekly.endDate());

        // Monthly
        ReportService.DateRange monthly = reportService.calculateDateRange("monthly");
        assertEquals(today.withDayOfMonth(1), monthly.startDate());
        assertEquals(today.withDayOfMonth(1).plusMonths(1), monthly.endDate());

        // Yearly
        ReportService.DateRange yearly = reportService.calculateDateRange("yearly");
        assertEquals(today.withDayOfYear(1), yearly.startDate());
        assertEquals(today.withDayOfYear(1).plusYears(1), yearly.endDate());

        // All Time
        ReportService.DateRange all = reportService.calculateDateRange("all");
        assertNull(all.startDate());
        assertNull(all.endDate());
    }

    @Test
    void testGetCashierSales_AllTime() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of(
                        "EMPLOYEE_ID", 1L,
                        "EMPLOYEE_NAME", "Vicheka Ly",
                        "POSITION", "Lead Cashier",
                        "TOTAL_ORDERS", 10,
                        "TOTAL_UNITS", 25,
                        "GROSS_SUBTOTAL", new BigDecimal("500.00"),
                        "ORDER_DISCOUNT", new BigDecimal("50.00"),
                        "NET_REVENUE", new BigDecimal("450.00"),
                        "AVG_ORDER_VALUE", new BigDecimal("45.00")
                )
        ));

        List<Map<String, Object>> result = reportService.getCashierSales("all");
        assertEquals(1, result.size());
        assertEquals("Vicheka Ly", result.get(0).get("EMPLOYEE_NAME"));
        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("SELECT e.EMPLOYEE_ID")
                && sql.contains("FROM EMPLOYEE e")
                && sql.contains("GROUP BY e.EMPLOYEE_ID")));
    }

    @Test
    void testGetCashierSalesDetails() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of(
                        "SALE_ID", 101L,
                        "SALE_DATE", "2026-09-21",
                        "CUSTOMER_NAME", "Sophea Meas",
                        "TOTAL_UNITS", 3,
                        "SUBTOTAL", new BigDecimal("60.00"),
                        "DISCOUNT", new BigDecimal("0.00"),
                        "GRAND_TOTAL", new BigDecimal("60.00"),
                        "STATUS", "COMPLETED"
                )
        ));

        List<Map<String, Object>> details = reportService.getCashierSalesDetails(2L, "monthly");
        assertEquals(1, details.size());
        assertEquals(101L, details.get(0).get("SALE_ID"));
        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("WHERE s.EMPLOYEE_ID = ?")
                && sql.contains("s.STATUS = 'COMPLETED'")), any(Object[].class));
    }

    @Test
    void testGetWeeklySales() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("WEEK_START", "2026-09-14", "WEEK_END", "2026-09-20", "TOTAL_SALES", 5, "NET_SALES", new BigDecimal("350.00"))
        ));

        List<Map<String, Object>> weekly = reportService.getWeeklySales();
        assertEquals(1, weekly.size());
        assertEquals("2026-09-14", weekly.get(0).get("WEEK_START"));
        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("WEEK_START") && sql.contains("FROM SALE")));
    }

    @Test
    void testGetYearlySales() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("SALE_YEAR", "2026", "TOTAL_SALES", 120, "NET_SALES", new BigDecimal("12500.00"))
        ));

        List<Map<String, Object>> yearly = reportService.getYearlySales();
        assertEquals(1, yearly.size());
        assertEquals("2026", yearly.get(0).get("SALE_YEAR"));
        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("SALE_YEAR") && sql.contains("FROM SALE")));
    }

    @Test
    void testGetDashboardMetrics() {
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
                "TOTAL_ORDERS", 8L,
                "SUBTOTAL", new BigDecimal("800.00"),
                "TOTAL_DISCOUNT", new BigDecimal("80.00"),
                "NET_SALES", new BigDecimal("720.00"),
                "ACTIVE_CASHIERS", 2
        ));

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of(
                        "EMPLOYEE_ID", 1L,
                        "EMPLOYEE_NAME", "Vicheka Ly",
                        "NET_REVENUE", new BigDecimal("500.00")
                )
        ));

        Map<String, Object> metrics = reportService.getDashboardMetrics("daily");
        assertNotNull(metrics);
        assertEquals("daily", metrics.get("period"));
        assertEquals(8L, metrics.get("totalOrders"));
        assertEquals(new BigDecimal("720.00"), metrics.get("totalNetSales"));
        assertEquals(new BigDecimal("90.00"), metrics.get("avgOrderValue"));
        assertEquals(2, metrics.get("activeCashiers"));
        assertEquals("Vicheka Ly", metrics.get("topCashierName"));
    }

    @Test
    void testSalesRankingsWithPeriodPreservesCompatibility() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        reportService.getTopSellingProducts();
        reportService.getCategorySales();

        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("s.STATUS = 'COMPLETED'")
                && sql.contains("s.GRAND_TOTAL / s.SUBTOTAL")
                && sql.contains("FROM SALE_DETAIL")));
        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("s.STATUS = 'COMPLETED'")
                && sql.contains("s.GRAND_TOTAL / s.SUBTOTAL")
                && sql.contains("FROM CATEGORY")));
    }

    @Test
    void testSalesTrend_PaddedAcrossPeriods() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        List<Map<String, Object>> dailyTrend = reportService.getSalesTrend("daily");
        assertEquals(14, dailyTrend.size(), "Daily trend should provide 14 business hour slots");
        assertEquals("08:00", dailyTrend.get(0).get("LABEL"));
        assertEquals("21:00", dailyTrend.get(13).get("LABEL"));

        List<Map<String, Object>> weeklyTrend = reportService.getSalesTrend("weekly");
        assertEquals(7, weeklyTrend.size(), "Weekly trend should provide 7 days");

        List<Map<String, Object>> monthlyTrend = reportService.getSalesTrend("monthly");
        LocalDate now = LocalDate.now();
        assertEquals(now.lengthOfMonth(), monthlyTrend.size(), "Monthly trend should match month days");

        List<Map<String, Object>> yearlyTrend = reportService.getSalesTrend("yearly");
        assertEquals(12, yearlyTrend.size(), "Yearly trend should provide 12 months");
        assertEquals("Jan", yearlyTrend.get(0).get("LABEL"));
        assertEquals("Dec", yearlyTrend.get(11).get("LABEL"));
    }
}
