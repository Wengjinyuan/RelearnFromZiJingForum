package org.example.rebuiltzijingforum.f11_admin.dto;

import java.time.LocalDateTime;

/** 管理员处罚请求体；目标用户 id 和动作来自 URL，不让 JSON 擅自更改。 */
public record PunishmentDTO(String reason, LocalDateTime expiresAt) {}
