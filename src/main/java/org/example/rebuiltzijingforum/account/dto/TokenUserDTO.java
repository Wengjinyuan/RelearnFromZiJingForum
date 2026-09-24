package org.example.rebuiltzijingforum.account.dto;

/** 从 JWT 中解析出的用户数据 DTO。 */
public record TokenUserDTO(long userId, int tokenVersion, String role) {
}
