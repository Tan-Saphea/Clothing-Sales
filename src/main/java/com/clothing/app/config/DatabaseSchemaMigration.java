package com.clothing.app.config;

import com.clothing.app.service.OracleSessionContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Order(1)
@ConditionalOnProperty(prefix = "app.schema-migration", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaMigration.class);
    private final JdbcTemplate jdbcTemplate;
    private final OracleSessionContextService oracleSessionContextService;

    public DatabaseSchemaMigration(JdbcTemplate jdbcTemplate,
                                   OracleSessionContextService oracleSessionContextService) {
        this.jdbcTemplate = jdbcTemplate;
        this.oracleSessionContextService = oracleSessionContextService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            oracleSessionContextService.applySystemUser("SYSTEM_MIGRATION");
            log.info("Checking database schema for PRODUCT.IMAGE_URL column...");
            String checkSql = "SELECT COUNT(*) FROM USER_TAB_COLS WHERE TABLE_NAME = 'PRODUCT' AND COLUMN_NAME = 'IMAGE_URL'";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class);

            if (count == null || count == 0) {
                log.info("Adding IMAGE_URL column to PRODUCT table...");
                jdbcTemplate.execute("ALTER TABLE PRODUCT ADD (IMAGE_URL VARCHAR2(1000))");
                log.info("Column PRODUCT.IMAGE_URL added successfully.");
            } else {
                log.info("Column PRODUCT.IMAGE_URL already exists.");
            }

            installHardenedProcedures();

            // Backfill images for products with missing/null image URL
            backfillProductImages();
        } catch (Exception ex) {
            throw new IllegalStateException("Database schema migration failed", ex);
        }
    }

    private void installHardenedProcedures() throws java.io.IOException {
        String databaseName = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                connection -> connection.getMetaData().getDatabaseProductName());
        if (!"Oracle".equalsIgnoreCase(databaseName)) {
            return;
        }

        String script = new ClassPathResource("db/oracle-hardening.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        for (String statement : script.split("(?m)^\\s*/\\s*$")) {
            String sql = statement.replaceFirst("(?s)^\\s*--[^\\r\\n]*(?:\\r?\\n)?", "").trim();
            if (!sql.isEmpty()) {
                jdbcTemplate.execute(sql);
            }
        }
        log.info("Oracle purchasing, sales, inventory, and payment procedures hardened successfully.");
    }

    private void backfillProductImages() {
        try {
            java.util.Map<String, String> defaultImages = new java.util.LinkedHashMap<>();
            defaultImages.put("Classic Oxford Cotton Shirt", "https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Slim Fit Linen Button-Down", "https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Floral Silk Summer Dress", "https://images.unsplash.com/photo-1572804013309-59a88b7e92f1?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Bohemian Tiered Maxi Skirt", "https://images.unsplash.com/photo-1583496661160-fb5886a0aaaa?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Tropical Hawaiian Print Shirt", "https://images.unsplash.com/photo-1523381210434-271e8be1f52b?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Casual Stretch Chino Shorts", "https://images.unsplash.com/photo-1591195853828-11db59a44f6b?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Tailored Wool Blend Blazer", "https://images.unsplash.com/photo-1594938298603-c8148c4dae35?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Classic Denim Trucker Jacket", "https://images.unsplash.com/photo-1576995853123-5a10305d93c0?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Raw Selvedge Slim Fit Jeans", "https://images.unsplash.com/photo-1541099649105-f69ad21f3246?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Slim Jeans", "https://images.unsplash.com/photo-1542272604-780c96856592?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Short Jeans", "https://images.unsplash.com/photo-1591195853828-11db59a44f6b?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Shorts", "https://images.unsplash.com/photo-1591195853828-11db59a44f6b?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Casual Jacket", "https://images.unsplash.com/photo-1551028719-00167b16eac5?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Premium Pima Cotton T-Shirt", "https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Classic T-Shirt", "https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Minimalist Wide-Brim Sun Hat", "https://images.unsplash.com/photo-1534215754734-18e55d13e346?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Full-Grain Leather Dress Belt", "https://images.unsplash.com/photo-1624222247344-550fb60583dc?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Zara", "https://images.unsplash.com/photo-1434389677669-e08b4cac3105?w=600&auto=format&fit=crop&q=80");
            defaultImages.put("Top", "https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?w=600&auto=format&fit=crop&q=80");

            for (java.util.Map.Entry<String, String> entry : defaultImages.entrySet()) {
                jdbcTemplate.update(
                        "UPDATE PRODUCT SET IMAGE_URL = ? WHERE PRODUCT_NAME = ? AND (IMAGE_URL IS NULL OR TRIM(IMAGE_URL) = '')",
                        entry.getValue(), entry.getKey()
                );
            }

            // Fallback for any other remaining products with no image
            jdbcTemplate.update(
                    "UPDATE PRODUCT SET IMAGE_URL = 'https://images.unsplash.com/photo-1523381210434-271e8be1f52b?w=600&auto=format&fit=crop&q=80' WHERE IMAGE_URL IS NULL OR TRIM(IMAGE_URL) = ''"
            );
            log.info("Product images backfilled successfully.");
        } catch (Exception ex) {
            throw new IllegalStateException("Product image backfill failed", ex);
        }
    }
}
