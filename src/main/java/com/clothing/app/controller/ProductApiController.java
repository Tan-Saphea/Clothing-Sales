package com.clothing.app.controller;

import com.clothing.app.dto.ProductDto;
import com.clothing.app.entity.Category;
import com.clothing.app.entity.Product;
import com.clothing.app.repository.CategoryRepository;
import com.clothing.app.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProductApiController {

    private final ProductService productService;
    private final CategoryRepository categoryRepository;

    public ProductApiController(ProductService productService, CategoryRepository categoryRepository) {
        this.productService = productService;
        this.categoryRepository = categoryRepository;
    }

    private ProductDto toDto(Product product) {
        return new ProductDto(
                product.getProductId(),
                product.getCategory() != null ? product.getCategory().getCategoryId() : null,
                product.getCategory() != null ? product.getCategory().getCategoryName() : null,
                product.getProductName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getIsActive()
        );
    }

    @GetMapping("/products")
    public List<ProductDto> getProducts(@RequestParam(required = false) Long categoryId,
                                       @RequestParam(required = false) String search) {
        List<Product> list;
        if (categoryId != null) {
            list = productService.findByCategoryId(categoryId);
        } else if (search != null && !search.trim().isEmpty()) {
            list = productService.searchByName(search.trim());
        } else {
            list = productService.findAll();
        }

        return list.stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<?> getProduct(@PathVariable Long id) {
        return productService.findById(id)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/products")
    public ResponseEntity<?> createProduct(@RequestBody Map<String, Object> payload) {
        String productName = (String) payload.get("productName");
        String description = (String) payload.get("description");
        String imageUrl = (String) payload.get("imageUrl");
        Object catIdObj = payload.get("categoryId");
        Boolean isActive = payload.get("isActive") != null ? Boolean.valueOf(payload.get("isActive").toString()) : true;

        if (productName == null || productName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Product name is required"));
        }
        if (catIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category is required"));
        }

        if (imageUrl != null && !imageUrl.isBlank()) {
            imageUrl = imageUrl.trim();
            if (imageUrl.startsWith("uploads/")) {
                imageUrl = "/" + imageUrl;
            }
        } else {
            imageUrl = null;
        }

        Long categoryId = Long.valueOf(catIdObj.toString());
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        Product product = new Product();
        product.setProductName(productName.trim());
        product.setDescription(description);
        product.setImageUrl(imageUrl);
        product.setCategory(category);
        product.setIsActive(isActive);
        product.setUpdatedAt(LocalDateTime.now());

        Product saved = productService.save(product);
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return productService.findById(id).map(existing -> {
            if (payload.containsKey("productName") && payload.get("productName") != null) {
                existing.setProductName(payload.get("productName").toString().trim());
            }
            if (payload.containsKey("description")) {
                existing.setDescription((String) payload.get("description"));
            }
            if (payload.containsKey("imageUrl")) {
                String img = (String) payload.get("imageUrl");
                if (img != null && !img.isBlank()) {
                    img = img.trim();
                    if (img.startsWith("uploads/")) {
                        img = "/" + img;
                    }
                    existing.setImageUrl(img);
                } else {
                    existing.setImageUrl(null);
                }
            }
            if (payload.containsKey("isActive") && payload.get("isActive") != null) {
                existing.setIsActive(Boolean.valueOf(payload.get("isActive").toString()));
            }
            if (payload.containsKey("categoryId") && payload.get("categoryId") != null) {
                Long catId = Long.valueOf(payload.get("categoryId").toString());
                existing.setCategory(categoryRepository.findById(catId)
                        .orElseThrow(() -> new IllegalArgumentException("Category not found: " + catId)));
            }
            existing.setUpdatedAt(LocalDateTime.now());
            Product saved = productService.save(existing);
            return ResponseEntity.ok(toDto(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        try {
            Map<String, Object> result = productService.deleteOrDeactivate(id);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete product: " + ex.getMessage()));
        }
    }
}
