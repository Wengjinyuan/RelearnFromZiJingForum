package org.example.rebuiltzijingforum.account.vo;

import java.time.LocalDateTime;

import org.example.rebuiltzijingforum.account.entity.UserAccountEntity;

/** 返回给前端的账号 VO，不包含密码哈希和 Token 版本。 */
public record AccountVO(
        long id,
        String account,
        String email,
        String mobile,
        String role,
        String status,
        LocalDateTime createdAt) {

    public static AccountVO from(UserAccountEntity entity) {
        return new AccountVO(
                entity.getId(),
                entity.getAccount(),
                maskEmail(entity.getEmail()),
                maskMobile(entity.getMobile()),
                entity.getRole(),
                entity.getStatus(),
                entity.getCreatedAt());
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
