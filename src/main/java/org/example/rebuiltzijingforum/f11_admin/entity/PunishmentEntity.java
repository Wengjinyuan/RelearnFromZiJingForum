package org.example.rebuiltzijingforum.f11_admin.entity;

import java.time.LocalDateTime;

/** user_punishment 表一行；每次处罚或解除都追加，不覆盖历史。 */
public record PunishmentEntity(long id, long userId, long operatorId, String action, String reason,
                               LocalDateTime expiresAt, LocalDateTime createdAt) {}
