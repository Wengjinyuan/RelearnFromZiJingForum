package org.example.rebuiltzijingforum.account.dto;

/** JWT 验证通过后，在后端内部传递的当前登录用户 DTO。 */
public record CurrentUserDTO(long userId, String account, String role) {
}
