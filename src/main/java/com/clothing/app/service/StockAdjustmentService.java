package com.clothing.app.service;

import com.clothing.app.dto.StockAdjustmentRequestDto;
import com.clothing.app.entity.ProductVariant;
import com.clothing.app.repository.ProductVariantRepository;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockAdjustmentService {

    private final ProductVariantRepository productVariantRepository;
    private final OracleProcedureService oracleProcedureService;
    private final AuditTrailService auditTrailService;
    private final EntityManager entityManager;

    public StockAdjustmentService(ProductVariantRepository productVariantRepository,
                                  OracleProcedureService oracleProcedureService,
                                  AuditTrailService auditTrailService,
                                  EntityManager entityManager) {
        this.productVariantRepository = productVariantRepository;
        this.oracleProcedureService = oracleProcedureService;
        this.auditTrailService = auditTrailService;
        this.entityManager = entityManager;
    }

    @Transactional
    public ProductVariant adjustStock(StockAdjustmentRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Adjustment request is required");
        }
        if (request.getVariantId() == null) {
            throw new IllegalArgumentException("Variant ID is required");
        }

        ProductVariant variant = productVariantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new IllegalArgumentException("Product variant not found: " + request.getVariantId()));

        if (variant.getProduct() == null || !Boolean.TRUE.equals(variant.getProduct().getIsActive())) {
            throw new IllegalArgumentException("Cannot adjust stock for an inactive product");
        }

        int currentQty = variant.getStockQty() != null ? variant.getStockQty() : 0;
        int targetQty;

        if (request.getNewQuantity() != null) {
            targetQty = request.getNewQuantity();
        } else if (request.getQuantityChange() != null) {
            if (request.getQuantityChange() == 0) {
                throw new IllegalArgumentException("Quantity change cannot be zero");
            }
            targetQty = currentQty + request.getQuantityChange();
        } else {
            throw new IllegalArgumentException("Either newQuantity or quantityChange must be specified");
        }

        if (targetQty < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative (target: " + targetQty + ")");
        }

        int diff = targetQty - currentQty;
        String reason = (request.getReason() == null || request.getReason().isBlank())
                ? "Manual stock adjustment"
                : request.getReason().trim();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("P_VARIANT_ID", variant.getVariantId())
                .addValue("P_NEW_QUANTITY", targetQty)
                .addValue("P_NOTE", reason);

        oracleProcedureService.executePackageProcedure("PKG_INVENTORY", "ADJUST_STOCK", params);

        if (entityManager != null) {
            entityManager.flush();
            entityManager.refresh(variant);
        }

        auditTrailService.record("PRODUCT_VARIANT", "ADJUST_STOCK", variant.getVariantId(),
                String.format("Adjusted stock from %d to %d (delta: %+d). Reason: %s",
                        currentQty, targetQty, diff, reason));

        return variant;
    }
}
