package com.clothing.app.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.InputStream;

@Controller
public class FaviconController {

    @GetMapping(value = {"/favicon.ico", "/favicon.png"}, produces = "image/png")
    @ResponseBody
    public ResponseEntity<byte[]> getFavicon() {
        try {
            ClassPathResource resource = new ClassPathResource("static/images/mystyle-logo-dark-crop.png");
            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    return ResponseEntity.ok()
                            .contentType(MediaType.IMAGE_PNG)
                            .body(in.readAllBytes());
                }
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.noContent().build();
    }
}
