package org.example.rebuiltzijingforum.f01_account.dto;

import java.time.Instant;

/**
 * 本文件要完成：
 * 保存生成后的 JWT 和过期时间。
 */
public record TokenDTO(String token, Instant expiresAt) {
}
