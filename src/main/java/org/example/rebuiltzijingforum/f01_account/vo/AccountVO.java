package org.example.rebuiltzijingforum.f01_account.vo;

import java.time.LocalDateTime;

import org.example.rebuiltzijingforum.f01_account.entity.UserAccountEntity;

/**
 * 本文件要完成：
 * 把账号实体转换成不含密码和 Token 版本的响应数据。
 */
public record AccountVO(
        long id,
        String account,
        String email,
        String mobile,
        String role,
        String status,
        LocalDateTime createdAt) {

    /**
     * 模板                                              案例
     * new VO(entity.公开字段(), 脱敏(entity.敏感字段())) new UserVO(entity.getId(), mask(entity.getEmail()))
     * 不把密码、内部版本等字段放入构造参数               不传 passwordHash、tokenVersion
     */
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

    /**
     * 模板                                              案例
     * 找到分隔符位置                                    int at = value.indexOf('@')
     * 保留少量可识别字符，其余替换                       a@example.com → a***@example.com
     */
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /**
     * 模板                                              案例
     * 空值或长度不足时直接返回                           null → null
     * 前缀 + 掩码 + 后缀                                13800138000 → 138****8000
     */
    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
