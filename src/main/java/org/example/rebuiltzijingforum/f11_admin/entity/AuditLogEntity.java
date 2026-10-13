package org.example.rebuiltzijingforum.f11_admin.entity;

import java.time.LocalDateTime;

/** admin_audit_log 表一行；业务更新与审计 INSERT 同一数据库事务。 */
public record AuditLogEntity(long id, long operatorId, String action, String targetType, long targetId,
                             String beforeState, String afterState, String reason, LocalDateTime createdAt) {}
