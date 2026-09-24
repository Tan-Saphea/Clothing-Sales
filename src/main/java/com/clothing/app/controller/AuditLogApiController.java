package com.clothing.app.controller;

import com.clothing.app.entity.AuditLog;
import com.clothing.app.repository.AuditLogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/admin/audit-log")
public class AuditLogApiController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogApiController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public List<Map<String, Object>> listAuditLogs(
            @RequestParam(defaultValue = "200") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        return auditLogRepository.findAll(PageRequest.of(
                        0, safeLimit, Sort.by(Sort.Direction.DESC, "actionDate")))
                .getContent().stream()
                .map(log -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("auditId", log.getAuditId());
                    row.put("tableName", log.getTableName());
                    row.put("actionType", log.getActionType());
                    row.put("recordId", log.getRecordId());
                    row.put("dbUser", log.getDbUser());
                    row.put("actionDate", log.getActionDate() != null ? log.getActionDate().toString() : null);
                    row.put("details", log.getDetails());
                    return row;
                }).toList();
    }
}
