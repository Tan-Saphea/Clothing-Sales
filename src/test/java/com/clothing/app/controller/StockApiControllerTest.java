package com.clothing.app.controller;

import com.clothing.app.dto.StockAdjustmentRequestDto;
import com.clothing.app.entity.ProductVariant;
import com.clothing.app.service.ReportService;
import com.clothing.app.service.StockAdjustmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockApiControllerTest {

    private ReportService reportService;
    private StockAdjustmentService stockAdjustmentService;
    private StockApiController controller;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        stockAdjustmentService = mock(StockAdjustmentService.class);
        controller = new StockApiController(reportService, stockAdjustmentService);
    }

    @Test
    void getProductStockDelegatesToReportService() {
        when(reportService.getProductStock()).thenReturn(List.of(Map.of("SKU", "TS-01")));
        List<Map<String, Object>> result = controller.getProductStock();
        assertEquals(1, result.size());
        assertEquals("TS-01", result.get(0).get("SKU"));
        verify(reportService).getProductStock();
    }

    @Test
    void adjustStockDelegatesToStockAdjustmentServiceAndReturnsDetails() {
        ProductVariant variant = new ProductVariant();
        variant.setVariantId(5L);
        variant.setSku("TS-WHT-S");
        variant.setStockQty(42);

        when(stockAdjustmentService.adjustStock(any(StockAdjustmentRequestDto.class))).thenReturn(variant);

        StockAdjustmentRequestDto dto = new StockAdjustmentRequestDto(5L, 42, null, "Stock reconciliation");
        ResponseEntity<Map<String, Object>> response = controller.adjustStock(dto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("success", body.get("status"));
        assertEquals(5L, body.get("variantId"));
        assertEquals("TS-WHT-S", body.get("sku"));
        assertEquals(42, body.get("newStock"));
    }
}
