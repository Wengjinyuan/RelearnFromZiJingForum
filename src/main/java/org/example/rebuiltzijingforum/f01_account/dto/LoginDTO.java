package org.example.rebuiltzijingforum.f01_account.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 本文件要完成：
 * 接收并校验登录账号和密码。
 */
public record LoginDTO(
        @NotBlank(message = "账号不能为空") String account,
        @NotBlank(message = "密码不能为空") String password) {
}
