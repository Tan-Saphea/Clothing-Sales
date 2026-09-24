package com.clothing.app.service;

import com.clothing.app.entity.Category;
import com.clothing.app.entity.Product;
import com.clothing.app.entity.ProductVariant;
import com.clothing.app.repository.CategoryRepository;
import com.clothing.app.repository.ProductRepository;
import com.clothing.app.repository.ProductVariantRepository;
import com.clothing.app.repository.PurchaseDetailRepository;
import com.clothing.app.repository.SaleDetailRepository;
import com.clothing.app.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final SaleDetailRepository saleDetailRepository;
    private final PurchaseDetailRepository purchaseDetailRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OracleSessionContextService oracleSessionContextService;

    public ProductService(ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductVariantRepository productVariantRepository,
            SaleDetailRepository saleDetailRepository,
            PurchaseDetailRepository purchaseDetailRepository,
            StockMovementRepository stockMovementRepository,
            OracleSessionContextService oracleSessionContextService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productVariantRepository = productVariantRepository;
        this.saleDetailRepository = saleDetailRepository;
        this.purchaseDetailRepository = purchaseDetailRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.oracleSessionContextService = oracleSessionContextService;
    }

    public List<Product> findAll() {
        return productRepository.findAllWithCategory();
    }

    public Optional<Product> findById(Long productId) {
        return productRepository.findByIdWithCategory(productId);
    }

    public List<Product> searchByName(String keyword) {
        return productRepository.searchByNameWithCategory(keyword);
    }

    public List<Product> findByCategoryId(Long categoryId) {
        return productRepository.findByCategoryIdWithCategory(categoryId);
    }

    @Transactional
    public Product save(Product product) {
        oracleSessionContextService.applyCurrentUser();
        if (product.getProductName() == null || product.getProductName().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        product.setProductName(product.getProductName().trim());
        if (product.getProductName().length() > 150) {
            throw new IllegalArgumentException("Product name must be 150 characters or fewer");
        }
        if (product.getDescription() != null && product.getDescription().length() > 500) {
            throw new IllegalArgumentException("Product description must be 500 characters or fewer");
        }
        if (product.getImageUrl() != null) {
            String img = product.getImageUrl().trim();
            if (img.isBlank()) {
                product.setImageUrl(null);
            } else {
                if (img.startsWith("uploads/")) {
                    img = "/" + img;
                }
                if (img.length() > 1000) {
                    throw new IllegalArgumentException("Product image URL must be 1000 characters or fewer");
                }
                product.setImageUrl(img);
            }
        }
        if (product.getCategory() == null || product.getCategory().getCategoryId() == null) {
            throw new IllegalArgumentException("Category is required");
        }
        Category category = categoryRepository.findById(product.getCategory().getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Category not found: " + product.getCategory().getCategoryId()));
        product.setCategory(category);
        return productRepository.save(product);
    }

    @Transactional
    public Map<String, Object> deleteOrDeactivate(Long productId) {
        oracleSessionContextService.applyCurrentUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        List<ProductVariant> variants = productVariantRepository.findByProduct_ProductIdWithDetails(productId);
        boolean hasTransactions = false;

        for (ProductVariant v : variants) {
            if (!saleDetailRepository.findByVariant_VariantId(v.getVariantId()).isEmpty() ||
                    !purchaseDetailRepository.findByVariant_VariantId(v.getVariantId()).isEmpty() ||
                    !stockMovementRepository.findByVariant_VariantId(v.getVariantId()).isEmpty()) {
                hasTransactions = true;
                break;
            }
        }

        if (hasTransactions) {
            product.setIsActive(false);
            productRepository.save(product);
            return Map.of(
                    "action", "DEACTIVATED",
                    "message",
                    "Product has historical sales/purchase transactions. It has been deactivated (Inactive) to protect financial records.",
                    "id", productId,
                    "isActive", false);
        } else {
            if (!variants.isEmpty()) {
                productVariantRepository.deleteAll(variants);
                productVariantRepository.flush();
            }
            productRepository.delete(product);
            productRepository.flush();
            return Map.of(
                    "action", "DELETED",
                    "message", "Product and its variants deleted successfully.",
                    "id", productId);
        }
    }

    @Transactional
    public void deleteById(Long productId) {
        deleteOrDeactivate(productId);
    }
}
