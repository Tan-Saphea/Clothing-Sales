package com.clothing.app.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final JdbcTemplate jdbcTemplate;

    public record DateRange(LocalDate startDate, LocalDate endDate) {}

    public ReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DateRange calculateDateRange(String period) {
        if (period == null || period.isBlank() || "all".equalsIgnoreCase(period)) {
            return new DateRange(null, null);
        }
        LocalDate today = LocalDate.now();
        switch (period.toLowerCase().trim()) {
            case "daily":
            case "today":
                return new DateRange(today, today.plusDays(1));
            case "weekly":
            case "week":
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                return new DateRange(monday, monday.plusDays(7));
            case "monthly":
            case "month":
                LocalDate firstDayOfMonth = today.withDayOfMonth(1);
                return new DateRange(firstDayOfMonth, firstDayOfMonth.plusMonths(1));
            case "yearly":
            case "year":
                LocalDate firstDayOfYear = today.withDayOfYear(1);
                return new DateRange(firstDayOfYear, firstDayOfYear.plusYears(1));
            default:
                return new DateRange(null, null);
        }
    }

    public List<Map<String, Object>> getDailySales() {
        String sql = """
            SELECT TO_CHAR(SALE_DAY, 'YYYY-MM-DD') AS SALE_DAY,
                   TOTAL_SALES,
                   SUBTOTAL,
                   ORDER_DISCOUNT,
                   NET_SALES
            FROM V_DAILY_SALES
            ORDER BY SALE_DAY DESC
            """;
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> getWeeklySales() {
        String sql = """
            SELECT TO_CHAR(TRUNC(SALE_DATE, 'IW'), 'YYYY-MM-DD') AS WEEK_START,
                   TO_CHAR(TRUNC(SALE_DATE, 'IW') + 6, 'YYYY-MM-DD') AS WEEK_END,
                   COUNT(*) AS TOTAL_SALES,
                   COALESCE(SUM(SUBTOTAL), 0) AS SUBTOTAL,
                   COALESCE(SUM(DISCOUNT), 0) AS ORDER_DISCOUNT,
                   COALESCE(SUM(GRAND_TOTAL), 0) AS NET_SALES
            FROM SALE
            WHERE STATUS = 'COMPLETED'
            GROUP BY TRUNC(SALE_DATE, 'IW')
            ORDER BY WEEK_START DESC
            """;
        try {
            return jdbcTemplate.queryForList(sql);
        } catch (Exception ex) {
            // Fallback for non-Oracle or test environments
            return jdbcTemplate.queryForList("""
                SELECT TO_CHAR(SALE_DATE, 'YYYY-MM-DD') AS WEEK_START,
                       TO_CHAR(SALE_DATE, 'YYYY-MM-DD') AS WEEK_END,
                       COUNT(*) AS TOTAL_SALES,
                       COALESCE(SUM(SUBTOTAL), 0) AS SUBTOTAL,
                       COALESCE(SUM(DISCOUNT), 0) AS ORDER_DISCOUNT,
                       COALESCE(SUM(GRAND_TOTAL), 0) AS NET_SALES
                FROM SALE
                WHERE STATUS = 'COMPLETED'
                GROUP BY TO_CHAR(SALE_DATE, 'YYYY-MM-DD')
                ORDER BY WEEK_START DESC
                """);
        }
    }

    public List<Map<String, Object>> getMonthlySales() {
        String sql = """
            SELECT SALE_MONTH,
                   TOTAL_SALES,
                   SUBTOTAL,
                   ORDER_DISCOUNT,
                   NET_SALES
            FROM V_MONTHLY_SALES
            ORDER BY SALE_MONTH DESC
            """;
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> getYearlySales() {
        String sql = """
            SELECT TO_CHAR(SALE_DATE, 'YYYY') AS SALE_YEAR,
                   COUNT(*) AS TOTAL_SALES,
                   COALESCE(SUM(SUBTOTAL), 0) AS SUBTOTAL,
                   COALESCE(SUM(DISCOUNT), 0) AS ORDER_DISCOUNT,
                   COALESCE(SUM(GRAND_TOTAL), 0) AS NET_SALES
            FROM SALE
            WHERE STATUS = 'COMPLETED'
            GROUP BY TO_CHAR(SALE_DATE, 'YYYY')
            ORDER BY SALE_YEAR DESC
            """;
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> getCashierSales(String period) {
        DateRange range = calculateDateRange(period);
        StringBuilder sql = new StringBuilder("""
            SELECT e.EMPLOYEE_ID,
                   e.EMPLOYEE_NAME,
                   e.POSITION,
                   COUNT(s.SALE_ID) AS TOTAL_ORDERS,
                   COALESCE(SUM(items.TOTAL_QTY), 0) AS TOTAL_UNITS,
                   COALESCE(SUM(s.SUBTOTAL), 0) AS GROSS_SUBTOTAL,
                   COALESCE(SUM(s.DISCOUNT), 0) AS ORDER_DISCOUNT,
                   COALESCE(SUM(s.GRAND_TOTAL), 0) AS NET_REVENUE,
                   CASE
                       WHEN COUNT(s.SALE_ID) > 0 THEN ROUND(SUM(s.GRAND_TOTAL) / COUNT(s.SALE_ID), 2)
                       ELSE 0
                   END AS AVG_ORDER_VALUE
            FROM EMPLOYEE e
            LEFT JOIN SALE s ON e.EMPLOYEE_ID = s.EMPLOYEE_ID AND s.STATUS = 'COMPLETED'
            """);

        List<Object> params = new ArrayList<>();
        if (range.startDate() != null && range.endDate() != null) {
            sql.append(" AND s.SALE_DATE >= ? AND s.SALE_DATE < ? ");
            params.add(java.sql.Date.valueOf(range.startDate()));
            params.add(java.sql.Date.valueOf(range.endDate()));
        }

        sql.append("""
            LEFT JOIN (
                SELECT SALE_ID, SUM(QUANTITY) AS TOTAL_QTY
                FROM SALE_DETAIL
                GROUP BY SALE_ID
            ) items ON s.SALE_ID = items.SALE_ID
            WHERE e.STATUS = 'ACTIVE' OR s.SALE_ID IS NOT NULL
            GROUP BY e.EMPLOYEE_ID, e.EMPLOYEE_NAME, e.POSITION
            ORDER BY NET_REVENUE DESC, TOTAL_ORDERS DESC
            """);

        if (params.isEmpty()) {
            return jdbcTemplate.queryForList(sql.toString());
        }
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> getCashierSalesDetails(Long employeeId, String period) {
        DateRange range = calculateDateRange(period);
        StringBuilder sql = new StringBuilder("""
            SELECT s.SALE_ID,
                   TO_CHAR(s.SALE_DATE, 'YYYY-MM-DD') AS SALE_DATE,
                   COALESCE(c.CUSTOMER_NAME, 'Walk-in Customer') AS CUSTOMER_NAME,
                   COALESCE(items.TOTAL_QTY, 0) AS TOTAL_UNITS,
                   s.SUBTOTAL,
                   s.DISCOUNT,
                   s.GRAND_TOTAL,
                   s.STATUS,
                   s.NOTE
            FROM SALE s
            LEFT JOIN CUSTOMER c ON s.CUSTOMER_ID = c.CUSTOMER_ID
            LEFT JOIN (
                SELECT SALE_ID, SUM(QUANTITY) AS TOTAL_QTY
                FROM SALE_DETAIL
                GROUP BY SALE_ID
            ) items ON s.SALE_ID = items.SALE_ID
            WHERE s.EMPLOYEE_ID = ? AND s.STATUS = 'COMPLETED'
            """);

        List<Object> params = new ArrayList<>();
        params.add(employeeId);
        if (range.startDate() != null && range.endDate() != null) {
            sql.append(" AND s.SALE_DATE >= ? AND s.SALE_DATE < ? ");
            params.add(java.sql.Date.valueOf(range.startDate()));
            params.add(java.sql.Date.valueOf(range.endDate()));
        }

        sql.append(" ORDER BY s.SALE_DATE DESC, s.SALE_ID DESC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> getProductStock() {
        String sql = """
            SELECT VARIANT_ID,
                   PRODUCT_ID,
                   PRODUCT_NAME,
                   CATEGORY_NAME,
                   SIZE_NAME,
                   COLOR_NAME,
                   SKU,
                   COST_PRICE,
                   SALE_PRICE,
                   STOCK_QTY,
                   STOCK_COST_VALUE,
                   STOCK_SALE_VALUE,
                   IS_ACTIVE
            FROM V_PRODUCT_STOCK
            ORDER BY PRODUCT_NAME, SKU
            """;
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> getLowStock() {
        String sql = """
            SELECT pv.VARIANT_ID,
                   p.PRODUCT_ID,
                   p.PRODUCT_NAME,
                   c.CATEGORY_NAME,
                   ps.SIZE_NAME,
                   co.COLOR_NAME,
                   pv.SKU,
                   pv.COST_PRICE,
                   pv.SALE_PRICE,
                   pv.STOCK_QTY,
                   p.IMAGE_URL
            FROM PRODUCT_VARIANT pv
            JOIN PRODUCT p ON pv.PRODUCT_ID = p.PRODUCT_ID
            JOIN CATEGORY c ON p.CATEGORY_ID = c.CATEGORY_ID
            JOIN PRODUCT_SIZE ps ON pv.SIZE_ID = ps.SIZE_ID
            JOIN COLOR co ON pv.COLOR_ID = co.COLOR_ID
            WHERE pv.STOCK_QTY <= 10
              AND p.IS_ACTIVE = 'Y'
            ORDER BY pv.STOCK_QTY ASC, p.PRODUCT_NAME ASC, pv.SKU ASC
            """;
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> getStockMovements() {
        String sql = """
            SELECT MOVEMENT_ID,
                   TO_CHAR(MOVEMENT_DATE, 'YYYY-MM-DD HH24:MI:SS') AS MOVEMENT_DATE,
                   PRODUCT_NAME,
                   SIZE_NAME,
                   COLOR_NAME,
                   SKU,
                   MOVEMENT_TYPE,
                   REFERENCE_TYPE,
                   REFERENCE_ID,
                   QUANTITY,
                   SIGNED_QUANTITY,
                   NOTE
            FROM V_STOCK_MOVEMENT
            ORDER BY MOVEMENT_DATE DESC
            """;
        return jdbcTemplate.queryForList(sql);
    }

    public Map<String, Object> getKpiSummary() {
        Map<String, Object> kpi = new LinkedHashMap<>();

        Number totalNetSales = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(NET_SALES), 0) FROM V_DAILY_SALES", Number.class);
        Number totalOrders = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(TOTAL_SALES), 0) FROM V_DAILY_SALES", Number.class);
        Number totalCostValue = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(STOCK_COST_VALUE), 0) FROM V_PRODUCT_STOCK WHERE IS_ACTIVE = 'Y'", Number.class);
        Number totalSaleValue = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(STOCK_SALE_VALUE), 0) FROM V_PRODUCT_STOCK WHERE IS_ACTIVE = 'Y'", Number.class);
        Number lowStockCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PRODUCT_VARIANT pv JOIN PRODUCT p ON pv.PRODUCT_ID = p.PRODUCT_ID WHERE pv.STOCK_QTY <= 10 AND p.IS_ACTIVE = 'Y'", Number.class);
        Number totalVariants = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM V_PRODUCT_STOCK WHERE IS_ACTIVE = 'Y'", Number.class);

        kpi.put("totalNetSales", totalNetSales != null ? totalNetSales : 0);
        kpi.put("totalOrders", totalOrders != null ? totalOrders : 0);
        kpi.put("totalInventoryCostValue", totalCostValue != null ? totalCostValue : 0);
        kpi.put("totalInventorySaleValue", totalSaleValue != null ? totalSaleValue : 0);
        kpi.put("lowStockCount", lowStockCount != null ? lowStockCount : 0);
        kpi.put("totalVariants", totalVariants != null ? totalVariants : 0);

        return kpi;
    }

    public Map<String, Object> getDashboardMetrics(String period) {
        DateRange range = calculateDateRange(period);
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(s.SALE_ID) AS TOTAL_ORDERS,
                   COALESCE(SUM(s.SUBTOTAL), 0) AS SUBTOTAL,
                   COALESCE(SUM(s.DISCOUNT), 0) AS TOTAL_DISCOUNT,
                   COALESCE(SUM(s.GRAND_TOTAL), 0) AS NET_SALES,
                   COUNT(DISTINCT s.EMPLOYEE_ID) AS ACTIVE_CASHIERS
            FROM SALE s
            WHERE s.STATUS = 'COMPLETED'
            """);

        List<Object> params = new ArrayList<>();
        if (range.startDate() != null && range.endDate() != null) {
            sql.append(" AND s.SALE_DATE >= ? AND s.SALE_DATE < ? ");
            params.add(java.sql.Date.valueOf(range.startDate()));
            params.add(java.sql.Date.valueOf(range.endDate()));
        }

        Map<String, Object> row = jdbcTemplate.queryForMap(sql.toString(), params.toArray());
        Number totalOrders = (Number) row.get("TOTAL_ORDERS");
        Number netSales = (Number) row.get("NET_SALES");
        Number subtotal = (Number) row.get("SUBTOTAL");
        Number discount = (Number) row.get("TOTAL_DISCOUNT");
        Number activeCashiers = (Number) row.get("ACTIVE_CASHIERS");

        BigDecimal netSalesBd = netSales != null ? new BigDecimal(netSales.toString()) : BigDecimal.ZERO;
        long ordersLong = totalOrders != null ? totalOrders.longValue() : 0L;
        BigDecimal aov = ordersLong > 0 ? netSalesBd.divide(BigDecimal.valueOf(ordersLong), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        List<Map<String, Object>> cashiers = getCashierSales(period);
        Map<String, Object> topCashier = Map.of();
        if (ordersLong > 0 && !cashiers.isEmpty()) {
            Map<String, Object> first = cashiers.get(0);
            Number topRev = (Number) first.get("NET_REVENUE");
            if (topRev != null && topRev.doubleValue() > 0) {
                topCashier = first;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period != null ? period.toLowerCase() : "all");
        result.put("totalNetSales", netSalesBd);
        result.put("totalOrders", ordersLong);
        result.put("subtotal", subtotal != null ? new BigDecimal(subtotal.toString()) : BigDecimal.ZERO);
        result.put("totalDiscount", discount != null ? new BigDecimal(discount.toString()) : BigDecimal.ZERO);
        result.put("avgOrderValue", aov);
        result.put("activeCashiers", activeCashiers != null ? activeCashiers.intValue() : 0);
        result.put("topCashierName", topCashier.getOrDefault("EMPLOYEE_NAME", "N/A"));
        result.put("topCashierRevenue", topCashier.getOrDefault("NET_REVENUE", BigDecimal.ZERO));
        result.put("startDate", range.startDate() != null ? range.startDate().toString() : null);
        result.put("endDate", range.endDate() != null ? range.endDate().toString() : null);

        return result;
    }

    public List<Map<String, Object>> getSalesTrend(String period) {
        String p = (period != null && !period.isBlank()) ? period.toLowerCase().trim() : "all";
        DateRange range = calculateDateRange(p);

        if ("daily".equals(p) || "today".equals(p)) {
            // Hourly breakdown across business hours (08:00 to 21:00)
            Map<String, BigDecimal> revenueByHour = new LinkedHashMap<>();
            Map<String, Long> countByHour = new LinkedHashMap<>();
            for (int h = 8; h <= 21; h++) {
                String hourStr = String.format("%02d:00", h);
                revenueByHour.put(hourStr, BigDecimal.ZERO);
                countByHour.put(hourStr, 0L);
            }

            String sql = """
                SELECT s.SALE_ID,
                       s.GRAND_TOTAL,
                       s.CREATED_AT
                FROM SALE s
                WHERE s.STATUS = 'COMPLETED' AND s.SALE_DATE >= ? AND s.SALE_DATE < ?
                """;

            List<Map<String, Object>> rows;
            try {
                rows = jdbcTemplate.queryForList(sql, java.sql.Date.valueOf(range.startDate()), java.sql.Date.valueOf(range.endDate()));
            } catch (Exception ex) {
                rows = List.of();
            }

            for (Map<String, Object> row : rows) {
                BigDecimal grandTotal = BigDecimal.ZERO;
                Object gt = row.get("GRAND_TOTAL");
                if (gt instanceof BigDecimal bd) {
                    grandTotal = bd;
                } else if (gt instanceof Number num) {
                    grandTotal = BigDecimal.valueOf(num.doubleValue());
                }

                int hour = 12;
                Object createdAtObj = row.get("CREATED_AT");
                if (createdAtObj instanceof java.sql.Timestamp ts) {
                    hour = ts.toLocalDateTime().getHour();
                } else if (createdAtObj instanceof java.time.LocalDateTime ldt) {
                    hour = ldt.getHour();
                } else if (createdAtObj != null) {
                    try {
                        String s = createdAtObj.toString();
                        if (s.contains(" ") || s.contains("T")) {
                            String timePart = s.contains(" ") ? s.split(" ")[1] : s.split("T")[1];
                            hour = Integer.parseInt(timePart.split(":")[0]);
                        }
                    } catch (Exception ignored) {}
                }

                if (hour < 8) hour = 8;
                if (hour > 21) hour = 21;
                String slot = String.format("%02d:00", hour);

                revenueByHour.put(slot, revenueByHour.get(slot).add(grandTotal));
                countByHour.put(slot, countByHour.get(slot) + 1L);
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> entry : revenueByHour.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("LABEL", entry.getKey());
                item.put("TOTAL_SALES", countByHour.get(entry.getKey()));
                item.put("NET_SALES", entry.getValue());
                result.add(item);
            }
            return result;
        }

        if ("weekly".equals(p) || "week".equals(p)) {
            LocalDate start = range.startDate();
            Map<String, BigDecimal> revMap = new LinkedHashMap<>();
            Map<String, Long> countMap = new LinkedHashMap<>();
            Map<String, String> dayLabelMap = new LinkedHashMap<>();

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
            for (int i = 0; i < 7; i++) {
                LocalDate d = start.plusDays(i);
                String key = d.toString();
                String dayName = d.getDayOfWeek().name().substring(0, 3);
                String label = dayName + " (" + d.format(fmt) + ")";
                revMap.put(key, BigDecimal.ZERO);
                countMap.put(key, 0L);
                dayLabelMap.put(key, label);
            }

            String sql = """
                SELECT TO_CHAR(s.SALE_DATE, 'YYYY-MM-DD') AS SALE_DAY,
                       COUNT(*) AS TOTAL_SALES,
                       COALESCE(SUM(s.GRAND_TOTAL), 0) AS NET_SALES
                FROM SALE s
                WHERE s.STATUS = 'COMPLETED' AND s.SALE_DATE >= ? AND s.SALE_DATE < ?
                GROUP BY TO_CHAR(s.SALE_DATE, 'YYYY-MM-DD')
                """;

            List<Map<String, Object>> rows;
            try {
                rows = jdbcTemplate.queryForList(sql, java.sql.Date.valueOf(range.startDate()), java.sql.Date.valueOf(range.endDate()));
            } catch (Exception ex) {
                rows = List.of();
            }

            for (Map<String, Object> r : rows) {
                String day = Objects.toString(r.get("SALE_DAY"), "");
                if (revMap.containsKey(day)) {
                    Object net = r.get("NET_SALES");
                    BigDecimal val = net instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) net).doubleValue());
                    revMap.put(day, val);
                    countMap.put(day, ((Number) r.get("TOTAL_SALES")).longValue());
                }
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (String key : revMap.keySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("LABEL", dayLabelMap.get(key));
                item.put("TOTAL_SALES", countMap.get(key));
                item.put("NET_SALES", revMap.get(key));
                result.add(item);
            }
            return result;
        }

        if ("monthly".equals(p) || "month".equals(p)) {
            LocalDate start = range.startDate();
            LocalDate end = range.endDate();
            Map<String, BigDecimal> revMap = new LinkedHashMap<>();
            Map<String, Long> countMap = new LinkedHashMap<>();

            LocalDate cur = start;
            while (cur.isBefore(end)) {
                String key = cur.toString();
                revMap.put(key, BigDecimal.ZERO);
                countMap.put(key, 0L);
                cur = cur.plusDays(1);
            }

            String sql = """
                SELECT TO_CHAR(s.SALE_DATE, 'YYYY-MM-DD') AS SALE_DAY,
                       COUNT(*) AS TOTAL_SALES,
                       COALESCE(SUM(s.GRAND_TOTAL), 0) AS NET_SALES
                FROM SALE s
                WHERE s.STATUS = 'COMPLETED' AND s.SALE_DATE >= ? AND s.SALE_DATE < ?
                GROUP BY TO_CHAR(s.SALE_DATE, 'YYYY-MM-DD')
                """;

            List<Map<String, Object>> rows;
            try {
                rows = jdbcTemplate.queryForList(sql, java.sql.Date.valueOf(range.startDate()), java.sql.Date.valueOf(range.endDate()));
            } catch (Exception ex) {
                rows = List.of();
            }

            for (Map<String, Object> r : rows) {
                String day = Objects.toString(r.get("SALE_DAY"), "");
                if (revMap.containsKey(day)) {
                    Object net = r.get("NET_SALES");
                    BigDecimal val = net instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) net).doubleValue());
                    revMap.put(day, val);
                    countMap.put(day, ((Number) r.get("TOTAL_SALES")).longValue());
                }
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (String key : revMap.keySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("LABEL", key.substring(5));
                item.put("TOTAL_SALES", countMap.get(key));
                item.put("NET_SALES", revMap.get(key));
                result.add(item);
            }
            return result;
        }

        if ("yearly".equals(p) || "year".equals(p)) {
            int year = range.startDate() != null ? range.startDate().getYear() : LocalDate.now().getYear();
            Map<String, BigDecimal> revMap = new LinkedHashMap<>();
            Map<String, Long> countMap = new LinkedHashMap<>();
            String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

            for (int m = 1; m <= 12; m++) {
                String key = String.format("%04d-%02d", year, m);
                revMap.put(key, BigDecimal.ZERO);
                countMap.put(key, 0L);
            }

            String sql = """
                SELECT TO_CHAR(s.SALE_DATE, 'YYYY-MM') AS LABEL,
                       COUNT(*) AS TOTAL_SALES,
                       COALESCE(SUM(s.GRAND_TOTAL), 0) AS NET_SALES
                FROM SALE s
                WHERE s.STATUS = 'COMPLETED' AND s.SALE_DATE >= ? AND s.SALE_DATE < ?
                GROUP BY TO_CHAR(s.SALE_DATE, 'YYYY-MM')
                """;

            List<Map<String, Object>> rows;
            try {
                rows = jdbcTemplate.queryForList(sql, java.sql.Date.valueOf(range.startDate()), java.sql.Date.valueOf(range.endDate()));
            } catch (Exception ex) {
                rows = List.of();
            }

            for (Map<String, Object> r : rows) {
                String monthKey = Objects.toString(r.get("LABEL"), "");
                if (revMap.containsKey(monthKey)) {
                    Object net = r.get("NET_SALES");
                    BigDecimal val = net instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) net).doubleValue());
                    revMap.put(monthKey, val);
                    countMap.put(monthKey, ((Number) r.get("TOTAL_SALES")).longValue());
                }
            }

            List<Map<String, Object>> result = new ArrayList<>();
            int mIdx = 0;
            for (String key : revMap.keySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("LABEL", monthNames[mIdx++]);
                item.put("TOTAL_SALES", countMap.get(key));
                item.put("NET_SALES", revMap.get(key));
                result.add(item);
            }
            return result;
        }

        if ("all".equals(p)) {
            String sql = """
                SELECT TO_CHAR(SALE_DAY, 'YYYY-MM-DD') AS LABEL,
                       TOTAL_SALES,
                       NET_SALES
                FROM V_DAILY_SALES
                ORDER BY SALE_DAY ASC
                """;
            List<Map<String, Object>> allList;
            try {
                allList = jdbcTemplate.queryForList(sql);
            } catch (Exception ex) {
                allList = List.of();
            }

            if (allList.size() >= 14) {
                return allList;
            }

            Map<String, BigDecimal> revMap = new LinkedHashMap<>();
            Map<String, Long> countMap = new LinkedHashMap<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
            Map<String, String> dayLabelMap = new LinkedHashMap<>();
            LocalDate today = LocalDate.now();

            for (int i = 27; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                String key = d.toString();
                revMap.put(key, BigDecimal.ZERO);
                countMap.put(key, 0L);
                dayLabelMap.put(key, d.format(fmt));
            }

            for (Map<String, Object> r : allList) {
                String day = Objects.toString(r.get("LABEL"), "");
                if (revMap.containsKey(day)) {
                    Object net = r.get("NET_SALES");
                    BigDecimal val = net instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) net).doubleValue());
                    revMap.put(day, val);
                    countMap.put(day, ((Number) r.get("TOTAL_SALES")).longValue());
                }
            }

            List<Map<String, Object>> padded = new ArrayList<>();
            for (String key : revMap.keySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("LABEL", dayLabelMap.get(key));
                item.put("TOTAL_SALES", countMap.get(key));
                item.put("NET_SALES", revMap.get(key));
                padded.add(item);
            }
            return padded;
        }

        return getDailySales();
    }

    public List<Map<String, Object>> getTopSellingProducts() {
        return getTopSellingProducts("all");
    }

    public List<Map<String, Object>> getTopSellingProducts(String period) {
        DateRange range = calculateDateRange(period);
        StringBuilder sql = new StringBuilder("""
            SELECT p.PRODUCT_NAME,
                   pv.SKU,
                   p.IMAGE_URL,
                   c.CATEGORY_NAME,
                   COALESCE(SUM(sd.QUANTITY), 0) AS TOTAL_QTY_SOLD,
                   COALESCE(SUM(CASE
                       WHEN s.SUBTOTAL > 0 THEN sd.SUBTOTAL * s.GRAND_TOTAL / s.SUBTOTAL
                       ELSE 0
                   END), 0) AS TOTAL_REVENUE
            FROM SALE_DETAIL sd
            JOIN SALE s ON sd.SALE_ID = s.SALE_ID AND s.STATUS = 'COMPLETED'
            """);

        List<Object> params = new ArrayList<>();
        if (range.startDate() != null && range.endDate() != null) {
            sql.append(" AND s.SALE_DATE >= ? AND s.SALE_DATE < ? ");
            params.add(java.sql.Date.valueOf(range.startDate()));
            params.add(java.sql.Date.valueOf(range.endDate()));
        }

        sql.append("""
            JOIN PRODUCT_VARIANT pv ON sd.VARIANT_ID = pv.VARIANT_ID
            JOIN PRODUCT p ON pv.PRODUCT_ID = p.PRODUCT_ID
            LEFT JOIN CATEGORY c ON p.CATEGORY_ID = c.CATEGORY_ID
            GROUP BY p.PRODUCT_NAME, pv.SKU, p.IMAGE_URL, c.CATEGORY_NAME
            ORDER BY TOTAL_QTY_SOLD DESC
            """);

        if (params.isEmpty()) {
            return jdbcTemplate.queryForList(sql.toString());
        }
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> getCategorySales() {
        return getCategorySales("all");
    }

    public List<Map<String, Object>> getCategorySales(String period) {
        DateRange range = calculateDateRange(period);
        StringBuilder sql = new StringBuilder("""
            SELECT c.CATEGORY_NAME,
                   COALESCE(SUM(CASE WHEN s.SALE_ID IS NOT NULL THEN sd.QUANTITY ELSE 0 END), 0) AS TOTAL_QTY,
                   COALESCE(SUM(CASE
                       WHEN s.SUBTOTAL > 0 THEN sd.SUBTOTAL * s.GRAND_TOTAL / s.SUBTOTAL
                       ELSE 0
                   END), 0) AS TOTAL_REVENUE
            FROM CATEGORY c
            LEFT JOIN PRODUCT p ON c.CATEGORY_ID = p.CATEGORY_ID
            LEFT JOIN PRODUCT_VARIANT pv ON p.PRODUCT_ID = pv.PRODUCT_ID
            LEFT JOIN SALE_DETAIL sd ON pv.VARIANT_ID = sd.VARIANT_ID
            LEFT JOIN SALE s ON sd.SALE_ID = s.SALE_ID AND s.STATUS = 'COMPLETED'
            """);

        List<Object> params = new ArrayList<>();
        if (range.startDate() != null && range.endDate() != null) {
            sql.append(" AND s.SALE_DATE >= ? AND s.SALE_DATE < ? ");
            params.add(java.sql.Date.valueOf(range.startDate()));
            params.add(java.sql.Date.valueOf(range.endDate()));
        }

        sql.append("""
            GROUP BY c.CATEGORY_NAME
            ORDER BY TOTAL_REVENUE DESC
            """);

        if (params.isEmpty()) {
            return jdbcTemplate.queryForList(sql.toString());
        }
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> getStockHealth() {
        Number inStock = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PRODUCT_VARIANT pv JOIN PRODUCT p ON pv.PRODUCT_ID = p.PRODUCT_ID WHERE p.IS_ACTIVE = 'Y' AND pv.STOCK_QTY > 10", Number.class);
        Number lowStock = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PRODUCT_VARIANT pv JOIN PRODUCT p ON pv.PRODUCT_ID = p.PRODUCT_ID WHERE p.IS_ACTIVE = 'Y' AND pv.STOCK_QTY > 0 AND pv.STOCK_QTY <= 10", Number.class);
        Number outOfStock = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PRODUCT_VARIANT pv JOIN PRODUCT p ON pv.PRODUCT_ID = p.PRODUCT_ID WHERE p.IS_ACTIVE = 'Y' AND pv.STOCK_QTY <= 0", Number.class);
        return Map.of(
                "inStock", inStock != null ? inStock : 0,
                "lowStock", lowStock != null ? lowStock : 0,
                "outOfStock", outOfStock != null ? outOfStock : 0
        );
    }
}
