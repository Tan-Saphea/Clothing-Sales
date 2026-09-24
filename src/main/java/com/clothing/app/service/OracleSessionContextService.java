package com.clothing.app.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;

@Service
public class OracleSessionContextService {

    private static final int ORACLE_IDENTIFIER_MAX_BYTES = 64;

    private final JdbcTemplate jdbcTemplate;

    public OracleSessionContextService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Makes the authenticated application username available to Oracle audit
     * triggers through SYS_CONTEXT('USERENV', 'CLIENT_IDENTIFIER').
     */
    public void applyCurrentUser() {
        applyIdentifier(currentUsername());
    }

    public void applySystemUser(String identifier) {
        applyIdentifier(identifier == null || identifier.isBlank() ? "SYSTEM" : identifier);
    }

    private void applyIdentifier(String identifier) {
        String safeIdentifier = truncateUtf8(identifier, ORACLE_IDENTIFIER_MAX_BYTES);
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            if (!"Oracle".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                return null;
            }
            try (CallableStatement statement = connection.prepareCall("{call DBMS_SESSION.SET_IDENTIFIER(?)}")) {
                statement.setString(1, safeIdentifier);
                statement.execute();
            }
            return null;
        });
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null || authentication.getName().isBlank()) {
            return "SYSTEM";
        }
        return authentication.getName();
    }

    private String truncateUtf8(String value, int maxBytes) {
        if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        int byteCount = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int characterBytes = character.getBytes(StandardCharsets.UTF_8).length;
            if (byteCount + characterBytes > maxBytes) {
                break;
            }
            result.append(character);
            byteCount += characterBytes;
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }
}
