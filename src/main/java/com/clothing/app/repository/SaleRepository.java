package com.clothing.app.repository;

import com.clothing.app.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    @Query("SELECT s FROM Sale s LEFT JOIN FETCH s.customer LEFT JOIN FETCH s.employee ORDER BY s.saleId DESC")
    List<Sale> findAllWithDetails();

    @Query("SELECT s FROM Sale s LEFT JOIN FETCH s.customer LEFT JOIN FETCH s.employee WHERE s.employee.employeeId = :employeeId ORDER BY s.saleId DESC")
    List<Sale> findAllWithDetailsByEmployeeId(@Param("employeeId") Long employeeId);

    @Query("SELECT s FROM Sale s LEFT JOIN FETCH s.customer LEFT JOIN FETCH s.employee WHERE s.saleId = :saleId")
    Optional<Sale> findByIdWithDetails(@Param("saleId") Long saleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sale s WHERE s.saleId = :saleId")
    Optional<Sale> findByIdForPayment(@Param("saleId") Long saleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sale s WHERE s.saleId = :saleId")
    Optional<Sale> findByIdForUpdate(@Param("saleId") Long saleId);

    List<Sale> findByStatus(String status);
    List<Sale> findBySaleDateBetween(LocalDate start, LocalDate end);
    List<Sale> findByCustomer_CustomerId(Long customerId);
    List<Sale> findByEmployee_EmployeeId(Long employeeId);
}
