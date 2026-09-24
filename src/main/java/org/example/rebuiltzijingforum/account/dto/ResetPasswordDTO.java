package org.example.rebuiltzijingforum.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 修改密码接口接收的 DTO。 */
public record ResetPasswordDTO(
        @NotBlank(message = "当前密码不能为空") String currentPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 72, message = "新密码长度必须为 8-72")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]+$", message = "新密码须为可见 ASCII 字符，且至少包含一个字母和一个数字")
        String newPassword) {
}
