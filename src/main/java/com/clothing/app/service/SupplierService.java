package com.clothing.app.service;

import com.clothing.app.entity.Supplier;
import com.clothing.app.repository.SupplierRepository;
import com.clothing.app.repository.PurchaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final PurchaseRepository purchaseRepository;
    private final AuditTrailService auditTrailService;

    public SupplierService(SupplierRepository supplierRepository,
                           PurchaseRepository purchaseRepository,
                           AuditTrailService auditTrailService) {
        this.supplierRepository = supplierRepository;
        this.purchaseRepository = purchaseRepository;
        this.auditTrailService = auditTrailService;
    }

    public List<Supplier> findAll() {
        return supplierRepository.findAll();
    }

    public Optional<Supplier> findById(Long supplierId) {
        return supplierRepository.findById(supplierId);
    }

    public List<Supplier> searchByName(String keyword) {
        return supplierRepository.findBySupplierNameContainingIgnoreCase(keyword);
    }

    @Transactional
    public Supplier save(Supplier supplier) {
        boolean creating = supplier.getSupplierId() == null;
        if (supplier.getSupplierName() == null || supplier.getSupplierName().isBlank()) {
            throw new IllegalArgumentException("Supplier name is required");
        }
        String name = supplier.getSupplierName().trim();
        validateLength(name, 150, "Supplier name");
        validateLength(supplier.getPhone(), 20, "Phone");
        validateLength(supplier.getEmail(), 150, "Email");
        validateLength(supplier.getAddress(), 255, "Address");
        String status = supplier.getStatus() == null || supplier.getStatus().isBlank()
                ? "ACTIVE" : supplier.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            throw new IllegalArgumentException("Supplier status must be ACTIVE or INACTIVE");
        }
        supplierRepository.findBySupplierNameIgnoreCase(name).ifPresent(existing -> {
            if (!existing.getSupplierId().equals(supplier.getSupplierId())) {
                throw new IllegalArgumentException("Supplier name already exists");
            }
        });
        supplier.setSupplierName(name);
        supplier.setStatus(status);
        Supplier saved = supplierRepository.save(supplier);
        auditTrailService.record("SUPPLIER", creating ? "INSERT" : "UPDATE", saved.getSupplierId(),
                (creating ? "Supplier created: " : "Supplier updated: ") + saved.getSupplierName());
        return saved;
    }

    @Transactional
    public void deleteById(Long supplierId) {
        deleteOrDeactivate(supplierId);
    }

    @Transactional
    public Map<String, Object> deleteOrDeactivate(Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + supplierId));
        if (!purchaseRepository.findBySupplier_SupplierId(supplierId).isEmpty()) {
            supplier.setStatus("INACTIVE");
            supplierRepository.save(supplier);
            auditTrailService.record("SUPPLIER", "UPDATE", supplierId,
                    "Supplier deactivated because purchase history exists: " + supplier.getSupplierName());
            return Map.of("action", "DEACTIVATED", "message",
                    "Supplier has purchase history and was deactivated", "id", supplierId);
        }
        supplierRepository.delete(supplier);
        auditTrailService.record("SUPPLIER", "DELETE", supplierId, "Supplier deleted: " + supplier.getSupplierName());
        return Map.of("action", "DELETED", "message", "Supplier deleted successfully", "id", supplierId);
    }

    private void validateLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " must be " + max + " characters or fewer");
        }
    }
}
