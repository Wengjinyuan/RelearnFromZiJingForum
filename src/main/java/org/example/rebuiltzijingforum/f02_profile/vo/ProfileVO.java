package org.example.rebuiltzijingforum.f02_profile.vo;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * 本文件要完成：
 * 返回可公开展示的个人资料，不暴露账号敏感字段。
 */
public record ProfileVO(
        long userId,
        String displayName,
        String avatarUrl,
        String gender,
        String signature,
        String major,
        LocalDate birthday,
        LocalDateTime updatedAt) {
}
