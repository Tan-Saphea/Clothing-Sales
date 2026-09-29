package com.clothing.app.controller;

import com.clothing.app.service.AccountPasswordService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountApiController {
    private final AccountPasswordService accountPasswordService;
    public AccountApiController(AccountPasswordService accountPasswordService) {
        this.accountPasswordService = accountPasswordService;
    }

    @PutMapping("/password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> payload,
            Authentication authentication, HttpServletRequest request) {
        accountPasswordService.changeOwnPassword(authentication,
                payload.get("currentPassword"), payload.get("newPassword"));
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return ResponseEntity.ok(Map.of(
                "message", "Password changed successfully. Sign in again with your new password.",
                "reauthenticationRequired", true));
    }
}
