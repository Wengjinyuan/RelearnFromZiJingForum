package org.example.rebuiltzijingforum.f01_account.dto;

/**
 * 本文件要完成：
 * 保存鉴权通过后可信的当前用户信息。
 */
public record CurrentUserDTO(long userId, String account, String role) {
}
