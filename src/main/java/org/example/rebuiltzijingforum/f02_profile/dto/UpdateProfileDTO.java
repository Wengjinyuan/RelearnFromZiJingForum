package org.example.rebuiltzijingforum.f02_profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 本文件要完成：
 * 接收并校验本人允许修改的资料字段。
 */
public record UpdateProfileDTO(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 32, message = "昵称不能超过 32 个字符")
        String displayName,

//        @Pattern(regexp = "UNKNOWN|MALE|FEMALE", message = "性别取值不正确")
        String gender,

        @Size(max = 50, message = "个人签名不能超过 50 个字符")
        String signature,

        @Size(max = 50, message = "专业不能超过 50 个字符")
        String major,
        LocalDate birthday) {
}
