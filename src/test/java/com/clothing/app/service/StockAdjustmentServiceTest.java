package com.clothing.app.service;

import com.clothing.app.dto.StockAdjustmentRequestDto;
import com.clothing.app.entity.Product;
import com.clothing.app.entity.ProductVariant;
import com.clothing.app.repository.ProductVariantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockAdjustmentServiceTest {

    private ProductVariantRepository productVariantRepository;
    private OracleProcedureService oracleProcedureService;
    private AuditTrailService auditTrailService;
    private EntityManager entityManager;
    private StockAdjustmentService stockAdjustmentService;

    @BeforeEach
    void setUp() {
        productVariantRepository = mock(ProductVariantRepository.class);
        oracleProcedureService = mock(OracleProcedureService.class);
        auditTrailService = mock(AuditTrailService.class);
        entityManager = mock(EntityManager.class);
        stockAdjustmentService = new StockAdjustmentService(
                productVariantRepository,
                oracleProcedureService,
                auditTrailService,
                entityManager
        );
    }

    @Test
    void adjustStockWithNewQuantityCallsStoredProcedureAndAudits() {
        Product product = new Product();
        product.setIsActive(true);

        ProductVariant variant = new ProductVariant();
        variant.setVariantId(10L);
        variant.setSku("TS-BLU-L");
        variant.setStockQty(15);
        variant.setProduct(product);

        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));

        StockAdjustmentRequestDto request = new StockAdjustmentRequestDto(10L, 25, null, "Cycle count correction");
        ProductVariant result = stockAdjustmentService.adjustStock(request);

        assertNotNull(result);
        assertEquals(10L, result.getVariantId());

        verify(oracleProcedureService).executePackageProcedure(
                eq("PKG_INVENTORY"),
                eq("ADJUST_STOCK"),
                any(MapSqlParameterSource.class)
        );
        verify(auditTrailService).record(
                eq("PRODUCT_VARIANT"),
                eq("ADJUST_STOCK"),
                eq(10L),
                any(String.class)
        );
    }

    @Test
    void adjustStockWithQuantityChangeComputesCorrectTarget() {
        Product product = new Product();
        product.setIsActive(true);

        ProductVariant variant = new ProductVariant();
        variant.setVariantId(11L);
        variant.setSku("TS-RED-M");
        variant.setStockQty(20);
        variant.setProduct(product);

        when(productVariantRepository.findById(11L)).thenReturn(Optional.of(variant));

        StockAdjustmentRequestDto request = new StockAdjustmentRequestDto(11L, null, -5, "Damaged stock");
        ProductVariant result = stockAdjustmentService.adjustStock(request);

        assertNotNull(result);
        verify(oracleProcedureService).executePackageProcedure(
                eq("PKG_INVENTORY"),
                eq("ADJUST_STOCK"),
                any(MapSqlParameterSource.class)
        );
    }

    @Test
    void adjustStockThrowsExceptionWhenProductInactive() {
        Product product = new Product();
        product.setIsActive(false);

        ProductVariant variant = new ProductVariant();
        variant.setVariantId(12L);
        variant.setStockQty(10);
        variant.setProduct(product);

        when(productVariantRepository.findById(12L)).thenReturn(Optional.of(variant));

        StockAdjustmentRequestDto request = new StockAdjustmentRequestDto(12L, 5, null, "Count");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> stockAdjustmentService.adjustStock(request));
        assertEquals("Cannot adjust stock for an inactive product", ex.getMessage());
    }

    @Test
    void adjustStockThrowsExceptionWhenNegativeTarget() {
        Product product = new Product();
        product.setIsActive(true);

        ProductVariant variant = new ProductVariant();
        variant.setVariantId(13L);
        variant.setStockQty(5);
        variant.setProduct(product);

        when(productVariantRepository.findById(13L)).thenReturn(Optional.of(variant));

        StockAdjustmentRequestDto request = new StockAdjustmentRequestDto(13L, -2, null, "Negative test");
        assertThrows(IllegalArgumentException.class, () -> stockAdjustmentService.adjustStock(request));

        StockAdjustmentRequestDto deltaRequest = new StockAdjustmentRequestDto(13L, null, -10, "Delta test");
        assertThrows(IllegalArgumentException.class, () -> stockAdjustmentService.adjustStock(deltaRequest));
    }

    @Test
    void adjustStockThrowsExceptionWhenVariantNotFound() {
        when(productVariantRepository.findById(999L)).thenReturn(Optional.empty());

        StockAdjustmentRequestDto request = new StockAdjustmentRequestDto(999L, 10, null, "Test");
        assertThrows(IllegalArgumentException.class, () -> stockAdjustmentService.adjustStock(request));
    }

    @Test
    void adjustStockThrowsExceptionWhenMissingQuantity() {
        Product product = new Product();
        product.setIsActive(true);

        ProductVariant variant = new ProductVariant();
        variant.setVariantId(14L);
        variant.setProduct(product);

        when(productVariantRepository.findById(14L)).thenReturn(Optional.of(variant));

        StockAdjustmentRequestDto request = new StockAdjustmentRequestDto(14L, null, null, "No qty");
        assertThrows(IllegalArgumentException.class, () -> stockAdjustmentService.adjustStock(request));
    }
}
