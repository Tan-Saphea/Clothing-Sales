package com.clothing.app.repository;

import com.clothing.app.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    @Query("SELECT p FROM Purchase p LEFT JOIN FETCH p.supplier LEFT JOIN FETCH p.employee ORDER BY p.purchaseId DESC")
    List<Purchase> findAllWithDetails();

    @Query("SELECT p FROM Purchase p LEFT JOIN FETCH p.supplier LEFT JOIN FETCH p.employee WHERE p.purchaseId = :purchaseId")
    Optional<Purchase> findByIdWithDetails(@Param("purchaseId") Long purchaseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Purchase p WHERE p.purchaseId = :purchaseId")
    Optional<Purchase> findByIdForUpdate(@Param("purchaseId") Long purchaseId);

    List<Purchase> findByStatus(String status);
    List<Purchase> findByPurchaseDateBetween(LocalDate start, LocalDate end);
    List<Purchase> findBySupplier_SupplierId(Long supplierId);
    List<Purchase> findByEmployee_EmployeeId(Long employeeId);
}
