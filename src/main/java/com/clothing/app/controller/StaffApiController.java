package com.clothing.app.controller;

import com.clothing.app.entity.Employee;
import com.clothing.app.service.StaffService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/staff")
public class StaffApiController {

    private final StaffService staffService;

    public StaffApiController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public Object listStaff() {
        return staffService.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Employee payload) {
        try {
            return ResponseEntity.ok(staffService.save(payload));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Employee payload) {
        try {
            Employee existing = staffService.findById(id);
            existing.setEmployeeName(payload.getEmployeeName());
            existing.setGender(payload.getGender());
            existing.setPhone(payload.getPhone());
            existing.setEmail(payload.getEmail());
            existing.setPosition(payload.getPosition());
            existing.setAddress(payload.getAddress());
            existing.setHireDate(payload.getHireDate());
            existing.setStatus(payload.getStatus());
            return ResponseEntity.ok(staffService.save(existing));
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Staff member not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(staffService.delete(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
