package com.clothing.app.repository;

import com.clothing.app.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category")
    List<Product> findAllWithCategory();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.productId = :productId")
    Optional<Product> findByIdWithCategory(@Param("productId") Long productId);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Product> searchByNameWithCategory(@Param("keyword") String keyword);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.category.categoryId = :categoryId")
    List<Product> findByCategoryIdWithCategory(@Param("categoryId") Long categoryId);

    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.imageUrl = :newUrl WHERE p.imageUrl = :oldUrl")
    int updateImageUrl(@Param("oldUrl") String oldUrl, @Param("newUrl") String newUrl);

    List<Product> findByProductNameContainingIgnoreCase(String keyword);
    List<Product> findByCategory_CategoryId(Long categoryId);
    boolean existsByProductName(String productName);
}
