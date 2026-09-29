package com.clothing.app.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("reporting")
public class ReportingHealthIndicator implements HealthIndicator {
    private final JdbcTemplate jdbcTemplate;
    public ReportingHealthIndicator(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public Health health() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM V_DAILY_SALES WHERE 1 = 0", Long.class);
            return Health.up().withDetail("reportingViews", "available").build();
        } catch (Exception ex) {
            return Health.down().withDetail("reportingViews", "unavailable").withException(ex).build();
        }
    }
}
