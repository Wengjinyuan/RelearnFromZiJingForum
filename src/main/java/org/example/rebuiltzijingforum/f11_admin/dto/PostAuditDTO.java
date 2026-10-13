package org.example.rebuiltzijingforum.f11_admin.dto;

/** 帖子审核/置顶请求体；enabled 只用于 TOP/ESSENCE 开关。 */
public record PostAuditDTO(String reason, Boolean enabled) {}
