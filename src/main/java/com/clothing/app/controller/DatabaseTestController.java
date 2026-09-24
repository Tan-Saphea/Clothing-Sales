package com.clothing.app.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class DatabaseTestController {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseTestController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/test-db")
    public Map<String, Object> testDatabase() {
        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();

        try {
            String user = jdbcTemplate.queryForObject(
                    "SELECT USER FROM DUAL",
                    String.class
            );

            String service = jdbcTemplate.queryForObject(
                    "SELECT SYS_CONTEXT('USERENV','SERVICE_NAME') FROM DUAL",
                    String.class
            );

            Integer tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM USER_TABLES",
                    Integer.class
            );

            Integer viewCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM USER_VIEWS",
                    Integer.class
            );

            Integer routineCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM USER_OBJECTS WHERE OBJECT_TYPE IN ('PROCEDURE', 'PACKAGE')",
                    Integer.class
            );

            String dbTime = jdbcTemplate.queryForObject(
                    "SELECT TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM DUAL",
                    String.class
            );

            String banner = "Oracle Database";
            try {
                banner = jdbcTemplate.queryForObject(
                        "SELECT BANNER FROM V$VERSION WHERE ROWNUM = 1",
                        String.class
                );
            } catch (Exception ignored) {
            }

            long latency = System.currentTimeMillis() - start;

            result.put("status", "success");
            result.put("connectionState", "CONNECTED");
            result.put("database", "Oracle");
            result.put("version", banner);
            result.put("user", user);
            result.put("service", service);
            result.put("tables", tableCount != null ? tableCount : 0);
            result.put("views", viewCount != null ? viewCount : 0);
            result.put("routines", routineCount != null ? routineCount : 0);
            result.put("serverTime", dbTime);
            result.put("latencyMs", latency);
            result.put("message", "Oracle Database connection is healthy and responsive.");

        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            result.put("status", "error");
            result.put("connectionState", "DISCONNECTED");
            result.put("database", "Oracle");
            result.put("latencyMs", latency);
            result.put("message", "Database is unavailable. Check the server connection.");
        }

        return result;
    }
}
