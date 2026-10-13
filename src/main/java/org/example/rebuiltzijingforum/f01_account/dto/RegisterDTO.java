package org.example.rebuiltzijingforum.f01_account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 本文件要完成：
 * 接收并校验注册账号、密码和联系方式。
 */
public record RegisterDTO(
        @NotBlank(message = "账号不能为空")
        @Pattern(regexp = "[A-Za-z0-9_]{1,32}", message = "账号只能包含字母、数字、下划线，长度 1-32")
        String account,

        @NotBlank(message = "密码不能为空")
        @Size(min = 1, max = 72, message = "密码长度必须为 1-72")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]+$", message = "密码须为可见 ASCII 字符，且至少包含一个字母和一个数字")
        String password,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱不能超过 128 个字符")
        String email,

        @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
        String mobile) {
}
