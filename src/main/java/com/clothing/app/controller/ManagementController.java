package com.clothing.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ManagementController {

    @GetMapping("/user-control")
    public String userControl(Model model, Authentication authentication) {
        model.addAttribute("isAdmin", isAdmin(authentication));
        model.addAttribute("currentUsername", authentication != null ? authentication.getName() : "User");
        return "management/user-control";
    }

    @GetMapping("/staff")
    public String staff(Model model, Authentication authentication) {
        model.addAttribute("isAdmin", isAdmin(authentication));
        model.addAttribute("currentUsername", authentication != null ? authentication.getName() : "User");
        return "management/staff";
    }

    @GetMapping("/audit-log")
    public String auditLog(Model model, Authentication authentication) {
        model.addAttribute("isAdmin", isAdmin(authentication));
        model.addAttribute("currentUsername", authentication != null ? authentication.getName() : "User");
        return "management/audit-log";
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
