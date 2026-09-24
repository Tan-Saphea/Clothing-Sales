package com.clothing.app.service;

import com.clothing.app.entity.Color;
import com.clothing.app.entity.Product;
import com.clothing.app.entity.ProductSize;
import com.clothing.app.entity.ProductVariant;
import com.clothing.app.repository.ColorRepository;
import com.clothing.app.repository.ProductRepository;
import com.clothing.app.repository.ProductSizeRepository;
import com.clothing.app.repository.ProductVariantRepository;
import com.clothing.app.repository.PurchaseDetailRepository;
import com.clothing.app.repository.SaleDetailRepository;
import com.clothing.app.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ProductVariantService {

    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;
    private final ColorRepository colorRepository;
    private final SaleDetailRepository saleDetailRepository;
    private final PurchaseDetailRepository purchaseDetailRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditTrailService auditTrailService;

    public ProductVariantService(ProductVariantRepository productVariantRepository,
                                ProductRepository productRepository,
                                ProductSizeRepository productSizeRepository,
                                ColorRepository colorRepository,
                                SaleDetailRepository saleDetailRepository,
                                PurchaseDetailRepository purchaseDetailRepository,
                                StockMovementRepository stockMovementRepository,
                                AuditTrailService auditTrailService) {
        this.productVariantRepository = productVariantRepository;
        this.productRepository = productRepository;
        this.productSizeRepository = productSizeRepository;
        this.colorRepository = colorRepository;
        this.saleDetailRepository = saleDetailRepository;
        this.purchaseDetailRepository = purchaseDetailRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.auditTrailService = auditTrailService;
    }

    public List<ProductVariant> findAll() {
        return productVariantRepository.findAllWithDetails();
    }

    public Optional<ProductVariant> findById(Long variantId) {
        return productVariantRepository.findByIdWithDetails(variantId);
    }

    public Optional<ProductVariant> findBySku(String sku) {
        return productVariantRepository.findBySku(sku);
    }

    public List<ProductVariant> findByProductId(Long productId) {
        return productVariantRepository.findByProduct_ProductIdWithDetails(productId);
    }

    @Transactional
    public ProductVariant save(ProductVariant variant) {
        boolean creating = variant.getVariantId() == null;
        if (variant.getSku() == null || variant.getSku().isBlank()) {
            throw new IllegalArgumentException("SKU is required");
        }
        variant.setSku(variant.getSku().trim().toUpperCase(Locale.ROOT));
        if (variant.getSku().length() > 60) {
            throw new IllegalArgumentException("SKU must be 60 characters or fewer");
        }
        productVariantRepository.findBySku(variant.getSku()).ifPresent(existing -> {
            if (!existing.getVariantId().equals(variant.getVariantId())) {
                throw new IllegalArgumentException("SKU already exists");
            }
        });
        validatePrice(variant.getCostPrice(), "Cost price");
        validatePrice(variant.getSalePrice(), "Sale price");
        if (variant.getStockQty() == null || variant.getStockQty() < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }
        if (variant.getProduct() == null || variant.getProduct().getProductId() == null) {
            throw new IllegalArgumentException("Product is required");
        }
        Product product = productRepository.findByIdWithCategory(variant.getProduct().getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + variant.getProduct().getProductId()));
        if (creating && !Boolean.TRUE.equals(product.getIsActive())) {
            throw new IllegalArgumentException("Cannot add a variant to an inactive product");
        }
        variant.setProduct(product);

        if (variant.getSize() == null || variant.getSize().getSizeId() == null) {
            throw new IllegalArgumentException("Size is required");
        }
        ProductSize size = productSizeRepository.findById(variant.getSize().getSizeId())
                .orElseThrow(() -> new IllegalArgumentException("Size not found: " + variant.getSize().getSizeId()));
        variant.setSize(size);

        if (variant.getColor() == null || variant.getColor().getColorId() == null) {
            throw new IllegalArgumentException("Color is required");
        }
        Color color = colorRepository.findById(variant.getColor().getColorId())
                .orElseThrow(() -> new IllegalArgumentException("Color not found: " + variant.getColor().getColorId()));
        variant.setColor(color);

        productVariantRepository.findByProduct_ProductIdAndSize_SizeIdAndColor_ColorId(
                product.getProductId(), size.getSizeId(), color.getColorId()).ifPresent(existing -> {
            if (!existing.getVariantId().equals(variant.getVariantId())) {
                throw new IllegalArgumentException("A variant with this product, size, and color already exists");
            }
        });

        ProductVariant saved = productVariantRepository.save(variant);
        auditTrailService.record("PRODUCT_VARIANT", creating ? "INSERT" : "UPDATE", saved.getVariantId(),
                (creating ? "Variant created: " : "Variant pricing updated: ") + saved.getSku());
        return productVariantRepository.findByIdWithDetails(saved.getVariantId()).orElse(saved);
    }

    private void validatePrice(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.scale() > 2 || value.compareTo(MAX_MONEY) > 0) {
            throw new IllegalArgumentException(field + " must be a non-negative amount with at most two decimal places");
        }
    }

    @Transactional
    public Map<String, Object> deleteOrDeactivate(Long variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + variantId));

        boolean hasTransactions = !saleDetailRepository.findByVariant_VariantId(variantId).isEmpty() ||
                                  !purchaseDetailRepository.findByVariant_VariantId(variantId).isEmpty() ||
                                  !stockMovementRepository.findByVariant_VariantId(variantId).isEmpty();

        if (hasTransactions) {
            throw new IllegalArgumentException("Cannot delete variant " + variant.getSku() + " because it is linked to completed sales or purchase orders. You can set its stock quantity to 0 instead.");
        }

        productVariantRepository.delete(variant);
        auditTrailService.record("PRODUCT_VARIANT", "DELETE", variantId, "Variant deleted: " + variant.getSku());
        return Map.of(
                "action", "DELETED",
                "message", "Variant " + variant.getSku() + " deleted successfully.",
                "id", variantId
        );
    }

    @Transactional
    public void deleteById(Long variantId) {
        deleteOrDeactivate(variantId);
    }
}
