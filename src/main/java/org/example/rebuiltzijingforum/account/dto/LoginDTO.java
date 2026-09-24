package org.example.rebuiltzijingforum.account.dto;

import jakarta.validation.constraints.NotBlank;

/** 登录接口接收的 DTO。 */
public record LoginDTO(
        @NotBlank(message = "账号不能为空") String account,
        @NotBlank(message = "密码不能为空") String password) {
}
