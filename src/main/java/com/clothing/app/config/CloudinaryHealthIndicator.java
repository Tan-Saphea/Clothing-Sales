package com.clothing.app.config;

import com.clothing.app.service.CloudinaryService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("cloudinary")
public class CloudinaryHealthIndicator implements HealthIndicator {

    private final CloudinaryService cloudinaryService;

    public CloudinaryHealthIndicator(CloudinaryService cloudinaryService) {
        this.cloudinaryService = cloudinaryService;
    }

    @Override
    public Health health() {
        if (cloudinaryService == null || !cloudinaryService.isConfigured()) {
            return Health.down().withDetail("cloudinary", "not configured (using local storage fallback)").build();
        }
        Map<String, Object> test = cloudinaryService.testConnection();
        if ("connected".equals(test.get("status"))) {
            return Health.up().withDetails(test).build();
        } else {
            return Health.down().withDetails(test).build();
        }
    }
}
