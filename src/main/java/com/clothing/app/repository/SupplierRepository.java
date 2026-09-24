package com.clothing.app.repository;

import com.clothing.app.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findBySupplierName(String supplierName);
    Optional<Supplier> findBySupplierNameIgnoreCase(String supplierName);
    List<Supplier> findBySupplierNameContainingIgnoreCase(String keyword);
    List<Supplier> findByStatus(String status);
}
