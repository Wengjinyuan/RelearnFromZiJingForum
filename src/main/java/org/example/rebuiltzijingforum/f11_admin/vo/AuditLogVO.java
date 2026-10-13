package org.example.rebuiltzijingforum.f11_admin.vo;

import java.time.LocalDateTime;

/** 管理员审计页：谁、何时、为什么，把什么从什么状态改到什么状态。 */
public record AuditLogVO(long id, long operatorId, String action, String targetType, long targetId,
                         String beforeState, String afterState, String reason, LocalDateTime createdAt) {}
