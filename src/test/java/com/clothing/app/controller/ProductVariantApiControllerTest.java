package com.clothing.app.controller;

import com.clothing.app.dto.ProductVariantDto;
import com.clothing.app.entity.Category;
import com.clothing.app.entity.Color;
import com.clothing.app.entity.Product;
import com.clothing.app.entity.ProductSize;
import com.clothing.app.entity.ProductVariant;
import com.clothing.app.repository.ColorRepository;
import com.clothing.app.repository.ProductRepository;
import com.clothing.app.repository.ProductSizeRepository;
import com.clothing.app.service.ProductVariantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductVariantApiControllerTest {

    private ProductVariantService variantService;
    private ProductRepository productRepository;
    private ProductSizeRepository sizeRepository;
    private ColorRepository colorRepository;
    private ProductVariantApiController controller;

    @BeforeEach
    void setUp() {
        variantService = mock(ProductVariantService.class);
        productRepository = mock(ProductRepository.class);
        sizeRepository = mock(ProductSizeRepository.class);
        colorRepository = mock(ColorRepository.class);
        controller = new ProductVariantApiController(variantService, productRepository, sizeRepository, colorRepository);
    }

    @Test
    void createVariant_Success() {
        Category category = new Category();
        category.setCategoryId(1L);
        category.setCategoryName("Shirts");

        Product product = new Product();
        product.setProductId(10L);
        product.setProductName("Oxford Shirt");
        product.setCategory(category);
        product.setIsActive(true);

        ProductSize size = new ProductSize();
        size.setSizeId(2L);
        size.setSizeName("M");

        Color color = new Color();
        color.setColorId(3L);
        color.setColorName("Blue");

        when(productRepository.findByIdWithCategory(10L)).thenReturn(Optional.of(product));
        when(sizeRepository.findById(2L)).thenReturn(Optional.of(size));
        when(colorRepository.findById(3L)).thenReturn(Optional.of(color));

        ProductVariant saved = new ProductVariant();
        saved.setVariantId(100L);
        saved.setProduct(product);
        saved.setSize(size);
        saved.setColor(color);
        saved.setSku("OXF-BLU-M");
        saved.setCostPrice(new BigDecimal("15.00"));
        saved.setSalePrice(new BigDecimal("30.00"));
        saved.setStockQty(0);

        when(variantService.save(any(ProductVariant.class))).thenReturn(saved);

        Map<String, Object> payload = Map.of(
                "productId", 10,
                "sizeId", 2,
                "colorId", 3,
                "sku", "OXF-BLU-M",
                "costPrice", "15.00",
                "salePrice", "30.00",
                "stockQty", 0
        );

        ResponseEntity<?> response = controller.createVariant(payload);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        ProductVariantDto dto = (ProductVariantDto) response.getBody();
        assertEquals(100L, dto.getVariantId());
        assertEquals("OXF-BLU-M", dto.getSku());
        assertEquals("Shirts", dto.getCategoryName());
        assertEquals("M", dto.getSizeName());
        assertEquals("Blue", dto.getColorName());
        assertEquals(new BigDecimal("30.00"), dto.getSalePrice());
    }

    @Test
    void createVariant_WhenCategoryProxyThrows_ReturnsDtoGracefullyWithout500() {
        Category throwingCategory = mock(Category.class);
        when(throwingCategory.getCategoryName()).thenThrow(new RuntimeException("Lazy init error"));

        Product product = new Product();
        product.setProductId(10L);
        product.setProductName("Oxford Shirt");
        product.setCategory(throwingCategory);
        product.setIsActive(true);

        ProductSize size = new ProductSize();
        size.setSizeId(2L);

        Color color = new Color();
        color.setColorId(3L);

        when(productRepository.findByIdWithCategory(10L)).thenReturn(Optional.of(product));
        when(sizeRepository.findById(2L)).thenReturn(Optional.of(size));
        when(colorRepository.findById(3L)).thenReturn(Optional.of(color));

        ProductVariant saved = new ProductVariant();
        saved.setVariantId(101L);
        saved.setProduct(product);
        saved.setSize(size);
        saved.setColor(color);
        saved.setSku("OXF-BLU-L");
        saved.setCostPrice(new BigDecimal("15.00"));
        saved.setSalePrice(new BigDecimal("30.00"));
        saved.setStockQty(0);

        when(variantService.save(any(ProductVariant.class))).thenReturn(saved);

        Map<String, Object> payload = Map.of(
                "productId", 10,
                "sizeId", 2,
                "colorId", 3,
                "sku", "OXF-BLU-L",
                "costPrice", "15.00",
                "salePrice", "30.00",
                "stockQty", 0
        );

        ResponseEntity<?> response = controller.createVariant(payload);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        ProductVariantDto dto = (ProductVariantDto) response.getBody();
        assertEquals(101L, dto.getVariantId());
        assertEquals(null, dto.getCategoryName());
    }

    @Test
    void createVariant_WithMissingFields_ReturnsBadRequest() {
        Map<String, Object> payload = Map.of(
                "productId", 10
        );

        ResponseEntity<?> response = controller.createVariant(payload);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createVariant_WithNonZeroInitialStock_ReturnsBadRequest() {
        Map<String, Object> payload = Map.of(
                "productId", 10,
                "sizeId", 2,
                "colorId", 3,
                "sku", "OXF-BLU-XL",
                "costPrice", "15.00",
                "salePrice", "30.00",
                "stockQty", 5
        );

        ResponseEntity<?> response = controller.createVariant(payload);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals("Cannot create variant: New variants start at zero stock; receive inventory through a purchase order", body.get("error"));
    }
}
