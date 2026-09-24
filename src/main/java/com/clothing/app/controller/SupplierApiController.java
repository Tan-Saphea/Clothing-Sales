package com.clothing.app.controller;

import com.clothing.app.entity.Supplier;
import com.clothing.app.service.SupplierService;
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
@RequestMapping("/api/suppliers")
public class SupplierApiController {

    private final SupplierService supplierService;

    public SupplierApiController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public List<Supplier> getAll(@RequestParam(required = false) String search) {
        if (search != null && !search.trim().isEmpty()) {
            return supplierService.searchByName(search.trim());
        }
        return supplierService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Supplier> getById(@PathVariable Long id) {
        return supplierService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Supplier supplier) {
        if (supplier.getSupplierName() == null || supplier.getSupplierName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Supplier name is required"));
        }
        if (supplier.getStatus() == null || supplier.getStatus().trim().isEmpty()) {
            supplier.setStatus("ACTIVE");
        }
        Supplier saved = supplierService.save(supplier);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Supplier payload) {
        return supplierService.findById(id).map(existing -> {
            if (payload.getSupplierName() != null && !payload.getSupplierName().trim().isEmpty()) {
                existing.setSupplierName(payload.getSupplierName().trim());
            }
            existing.setPhone(payload.getPhone());
            existing.setEmail(payload.getEmail());
            existing.setAddress(payload.getAddress());
            if (payload.getStatus() != null && !payload.getStatus().trim().isEmpty()) {
                existing.setStatus(payload.getStatus().trim().toUpperCase());
            }
            existing.setUpdatedAt(LocalDateTime.now());
            Supplier saved = supplierService.save(existing);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(supplierService.deleteOrDeactivate(id));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete supplier: " + ex.getMessage()));
        }
    }
}
