package org.example.rebuiltzijingforum.account.dto;

import java.time.Instant;

/** JwtUtils 生成的 Token 数据 DTO。 */
public record TokenDTO(String token, Instant expiresAt) {
}
