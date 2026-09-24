package com.clothing.app.repository;

import com.clothing.app.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByVariant_VariantId(Long variantId);
    List<StockMovement> findByMovementType(String movementType);
    List<StockMovement> findByMovementDateBetween(LocalDateTime start, LocalDateTime end);
}
