package com.clothing.app.repository;

import com.clothing.app.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findBySku(String sku);

    Optional<ProductVariant> findByProduct_ProductIdAndSize_SizeIdAndColor_ColorId(
            Long productId, Long sizeId, Long colorId);

    @Query("SELECT pv FROM ProductVariant pv LEFT JOIN FETCH pv.product p LEFT JOIN FETCH p.category LEFT JOIN FETCH pv.size LEFT JOIN FETCH pv.color WHERE pv.variantId = :variantId")
    Optional<ProductVariant> findByIdWithDetails(@Param("variantId") Long variantId);

    @Query("SELECT pv FROM ProductVariant pv LEFT JOIN FETCH pv.product p LEFT JOIN FETCH p.category LEFT JOIN FETCH pv.size LEFT JOIN FETCH pv.color")
    List<ProductVariant> findAllWithDetails();

    @Query("SELECT pv FROM ProductVariant pv LEFT JOIN FETCH pv.product p LEFT JOIN FETCH p.category LEFT JOIN FETCH pv.size LEFT JOIN FETCH pv.color WHERE pv.product.productId = :productId")
    List<ProductVariant> findByProduct_ProductIdWithDetails(@Param("productId") Long productId);

    List<ProductVariant> findByStockQtyLessThan(Integer qty);
}
