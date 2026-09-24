package com.clothing.app.repository;

import com.clothing.app.entity.ProductSize;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductSizeRepository extends JpaRepository<ProductSize, Long> {
    Optional<ProductSize> findBySizeName(String sizeName);
}
