package com.clothing.app.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PermissionProbeController {

    @GetMapping({"/sales", "/customers", "/dashboard", "/user-control", "/api/variants", "/api/sales"})
    String readable() {
        return "ok";
    }

    @PostMapping({"/api/sales", "/api/products", "/api/variants", "/api/user-control/cashiers"})
    String writable() {
        return "ok";
    }
}
