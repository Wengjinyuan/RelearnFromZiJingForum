package org.example.rebuiltzijingforum.f01_account.dto;

/**
 * 本文件要完成：
 * 保存从 JWT 载荷中解析出的用户信息。
 */
public record TokenUserDTO(long userId, int tokenVersion, String role) {
}
