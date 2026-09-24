package com.clothing.app.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    @Test
    void salesRankingsOnlyUseCompletedSalesAndAllocateOrderDiscount() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
        ReportService service = new ReportService(jdbcTemplate);

        service.getTopSellingProducts();
        service.getCategorySales();

        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("s.STATUS = 'COMPLETED'")
                && sql.contains("s.GRAND_TOTAL / s.SUBTOTAL")
                && sql.contains("FROM SALE_DETAIL")));
        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("s.STATUS = 'COMPLETED'")
                && sql.contains("s.GRAND_TOTAL / s.SUBTOTAL")
                && sql.contains("FROM CATEGORY")));
    }
}
