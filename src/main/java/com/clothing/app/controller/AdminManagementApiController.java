package com.clothing.app.controller;

import com.clothing.app.service.AdminManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/user-control")
public class AdminManagementApiController {

    private final AdminManagementService adminManagementService;

    public AdminManagementApiController(AdminManagementService adminManagementService) {
        this.adminManagementService = adminManagementService;
    }

    @GetMapping
    public Object listAccounts() {
        return adminManagementService.listAccounts();
    }

    @PostMapping("/cashiers")
    public ResponseEntity<?> createCashier(@RequestBody Map<String, String> payload) {
        try {
            return ResponseEntity.ok(adminManagementService.createCashier(
                    payload.get("username"), payload.get("password"), payload.get("employeeName"),
                    payload.get("gender"), payload.get("phone"), payload.get("email"), payload.get("address")));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/cashiers/link/{employeeId}")
    public ResponseEntity<?> createCashierForExistingEmployee(@PathVariable Long employeeId,
                                                               @RequestBody Map<String, String> payload) {
        try {
            return ResponseEntity.ok(adminManagementService.createCashierForExistingEmployee(
                    payload.get("username"), payload.get("password"), employeeId));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCashierInfo(@PathVariable Long id,
                                                @RequestBody Map<String, String> payload) {
        try {
            return ResponseEntity.ok(adminManagementService.updateCashierInfo(
                    id,
                    payload.get("employeeName"),
                    payload.get("gender"),
                    payload.get("phone"),
                    payload.get("email"),
                    payload.get("address")));
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("User account not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> setStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> payload) {
        try {
            Boolean enabled = payload.get("enabled");
            if (enabled == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "enabled is required"));
            }
            adminManagementService.setAccountEnabled(id, enabled);
            return ResponseEntity.ok(Map.of("message", "Account status updated", "enabled", enabled));
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("User account not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        try {
            String password = payload.get("password");
            adminManagementService.resetPassword(id, password);
            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("User account not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCashier(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adminManagementService.deleteCashier(id));
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("User account not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
