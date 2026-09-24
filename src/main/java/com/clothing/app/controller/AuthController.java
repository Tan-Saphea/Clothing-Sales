package com.clothing.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                       @RequestParam(value = "logout", required = false) String logout,
                       @RequestParam(value = "roleError", required = false) String roleError,
                       @RequestParam(value = "disabled", required = false) String disabled,
                       @RequestParam(value = "locked", required = false) String locked,
                       Model model) {
        if (locked != null) {
            model.addAttribute("errorMessage", "Account is temporarily locked due to multiple failed login attempts. Please try again later.");
        } else if (disabled != null) {
            model.addAttribute("errorMessage", "Your account or employee profile has been deactivated. Please contact an administrator.");
        } else if (roleError != null) {
            model.addAttribute("errorMessage", "You do not have permission to access the selected role workspace.");
        } else if (error != null) {
            model.addAttribute("errorMessage", "Invalid username or password.");
        }
        if (logout != null) {
            model.addAttribute("successMessage", "You have been logged out successfully.");
        }
        return "auth/login";
    }
}
