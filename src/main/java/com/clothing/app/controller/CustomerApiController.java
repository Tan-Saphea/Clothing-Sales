package com.clothing.app.controller;

import com.clothing.app.entity.Customer;
import com.clothing.app.service.CustomerService;
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
@RequestMapping("/api/customers")
public class CustomerApiController {

    private final CustomerService customerService;

    public CustomerApiController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<Customer> getAll(@RequestParam(required = false) String search) {
        if (search != null && !search.trim().isEmpty()) {
            return customerService.search(search.trim());
        }
        return customerService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getById(@PathVariable Long id) {
        return customerService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Customer customer) {
        if (customer.getCustomerName() == null || customer.getCustomerName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Customer name is required"));
        }
        Customer saved = customerService.save(customer);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Customer payload) {
        return customerService.findById(id).map(existing -> {
            if (payload.getCustomerName() != null && !payload.getCustomerName().trim().isEmpty()) {
                existing.setCustomerName(payload.getCustomerName().trim());
            }
            existing.setPhone(payload.getPhone());
            existing.setEmail(payload.getEmail());
            existing.setGender(payload.getGender());
            existing.setAddress(payload.getAddress());
            existing.setUpdatedAt(LocalDateTime.now());
            Customer saved = customerService.save(existing);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            customerService.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Customer deleted successfully", "id", id));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete customer: " + ex.getMessage()));
        }
    }
}
