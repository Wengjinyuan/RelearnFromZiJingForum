package org.example.rebuiltzijingforum.f02_profile.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本文件要完成：
 * 接收并校验头像上传文件。
 */
public record AvatarUploadDTO(
        @NotNull(message = "请选择头像文件") MultipartFile file) {
}
