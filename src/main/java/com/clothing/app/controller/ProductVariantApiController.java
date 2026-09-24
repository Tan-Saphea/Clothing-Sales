package com.clothing.app.controller;

import com.clothing.app.dto.ProductVariantDto;
import com.clothing.app.entity.Color;
import com.clothing.app.entity.Product;
import com.clothing.app.entity.ProductSize;
import com.clothing.app.entity.ProductVariant;
import com.clothing.app.repository.ColorRepository;
import com.clothing.app.repository.ProductRepository;
import com.clothing.app.repository.ProductSizeRepository;
import com.clothing.app.service.ProductVariantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProductVariantApiController {

    private final ProductVariantService productVariantService;
    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;
    private final ColorRepository colorRepository;

    public ProductVariantApiController(ProductVariantService productVariantService,
                                      ProductRepository productRepository,
                                      ProductSizeRepository productSizeRepository,
                                      ColorRepository colorRepository) {
        this.productVariantService = productVariantService;
        this.productRepository = productRepository;
        this.productSizeRepository = productSizeRepository;
        this.colorRepository = colorRepository;
    }

    private ProductVariantDto toDto(ProductVariant v, boolean includeCostPrice) {
        Long catId = null;
        String catName = null;
        try {
            if (v.getProduct() != null && v.getProduct().getCategory() != null) {
                catId = v.getProduct().getCategory().getCategoryId();
                catName = v.getProduct().getCategory().getCategoryName();
            }
        } catch (Exception ignored) {
            // Defensive in case proxy cannot be initialized outside transaction
        }

        return new ProductVariantDto(
                v.getVariantId(),
                v.getProduct() != null ? v.getProduct().getProductId() : null,
                v.getProduct() != null ? v.getProduct().getProductName() : null,
                catId,
                catName,
                v.getSize() != null ? v.getSize().getSizeId() : null,
                v.getSize() != null ? v.getSize().getSizeName() : null,
                v.getColor() != null ? v.getColor().getColorId() : null,
                v.getColor() != null ? v.getColor().getColorName() : null,
                v.getSku(),
                includeCostPrice ? v.getCostPrice() : null,
                v.getSalePrice(),
                v.getStockQty(),
                v.getProduct() != null ? v.getProduct().getIsActive() : false,
                v.getProduct() != null ? v.getProduct().getImageUrl() : null
        );
    }

    @GetMapping("/variants")
    public List<ProductVariantDto> getVariants(Authentication authentication) {
        boolean includeCostPrice = isAdmin(authentication);
        return productVariantService.findAll().stream()
                .map(v -> toDto(v, includeCostPrice))
                .toList();
    }

    @GetMapping("/variants/{id}")
    public ResponseEntity<ProductVariantDto> getVariant(@PathVariable Long id, Authentication authentication) {
        boolean includeCostPrice = isAdmin(authentication);
        return productVariantService.findById(id)
                .map(v -> toDto(v, includeCostPrice))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/variants/by-product/{productId}")
    public List<ProductVariantDto> getVariantsByProduct(@PathVariable Long productId, Authentication authentication) {
        boolean includeCostPrice = isAdmin(authentication);
        return productVariantService.findByProductId(productId).stream()
                .map(v -> toDto(v, includeCostPrice))
                .toList();
    }

    @PostMapping("/variants")
    public ResponseEntity<?> createVariant(@RequestBody Map<String, Object> payload) {
        try {
            if (payload == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Request payload is required"));
            }
            if (payload.get("productId") == null || payload.get("sizeId") == null || payload.get("colorId") == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Product, size, and color are required"));
            }
            if (payload.get("costPrice") == null || payload.get("salePrice") == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cost price and sale price are required"));
            }

            Long productId = Long.valueOf(payload.get("productId").toString());
            Long sizeId = Long.valueOf(payload.get("sizeId").toString());
            Long colorId = Long.valueOf(payload.get("colorId").toString());
            String sku = (String) payload.get("sku");
            BigDecimal costPrice = new BigDecimal(payload.get("costPrice").toString());
            BigDecimal salePrice = new BigDecimal(payload.get("salePrice").toString());
            Integer stockQty = payload.containsKey("stockQty") && payload.get("stockQty") != null
                    ? Integer.valueOf(payload.get("stockQty").toString()) : 0;
            if (stockQty != 0) {
                throw new IllegalArgumentException("New variants start at zero stock; receive inventory through a purchase order");
            }

            Product product = productRepository.findByIdWithCategory(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
            ProductSize size = productSizeRepository.findById(sizeId)
                    .orElseThrow(() -> new IllegalArgumentException("Size not found: " + sizeId));
            Color color = colorRepository.findById(colorId)
                    .orElseThrow(() -> new IllegalArgumentException("Color not found: " + colorId));

            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setSize(size);
            variant.setColor(color);
            variant.setSku(sku != null && !sku.trim().isEmpty() ? sku.trim().toUpperCase() : product.getProductName().substring(0, Math.min(3, product.getProductName().length())).toUpperCase() + "-" + System.currentTimeMillis() % 10000);
            variant.setCostPrice(costPrice);
            variant.setSalePrice(salePrice);
            variant.setStockQty(stockQty);
            variant.setUpdatedAt(LocalDateTime.now());

            ProductVariant saved = productVariantService.save(variant);
            return ResponseEntity.ok(toDto(saved, true));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot create variant: " + ex.getMessage()));
        }
    }

    @PutMapping("/variants/{id}")
    public ResponseEntity<?> updateVariant(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        if (payload.containsKey("stockQty")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Stock changes must be recorded through sales or purchases"));
        }
        return productVariantService.findById(id).map(existing -> {
            if (payload.containsKey("sku") && payload.get("sku") != null) {
                existing.setSku(payload.get("sku").toString().trim().toUpperCase());
            }
            if (payload.containsKey("costPrice") && payload.get("costPrice") != null) {
                existing.setCostPrice(new BigDecimal(payload.get("costPrice").toString()));
            }
            if (payload.containsKey("salePrice") && payload.get("salePrice") != null) {
                existing.setSalePrice(new BigDecimal(payload.get("salePrice").toString()));
            }
            existing.setUpdatedAt(LocalDateTime.now());
            ProductVariant saved = productVariantService.save(existing);
            return ResponseEntity.ok(toDto(saved, true));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/variants/{id}")
    public ResponseEntity<?> deleteVariant(@PathVariable Long id) {
        try {
            Map<String, Object> result = productVariantService.deleteOrDeactivate(id);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
