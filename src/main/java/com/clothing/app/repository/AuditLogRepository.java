package com.clothing.app.repository;

import com.clothing.app.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByTableName(String tableName);
    List<AuditLog> findByActionDateBetween(LocalDateTime start, LocalDateTime end);
    List<AuditLog> findAllByOrderByActionDateDesc();
}
