package com.clothing.app.service;

import com.clothing.app.entity.AuditLog;
import com.clothing.app.repository.AuditLogRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuditTrailService {

    private final AuditLogRepository auditLogRepository;

    public AuditTrailService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String tableName, String actionType, Long recordId, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setTableName(truncate(tableName, 50));
        auditLog.setActionType(truncate(actionType, 20));
        auditLog.setRecordId(recordId);
        auditLog.setDbUser(truncate(currentUsername(), 80));
        auditLog.setActionDate(LocalDateTime.now());
        auditLog.setDetails(truncate(details, 1000));
        auditLogRepository.save(auditLog);
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

    private String truncate(String value, int length) {
        if (value == null || value.length() <= length) {
            return value;
        }
        return value.substring(0, length);
    }
}
