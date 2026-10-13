package org.example.rebuiltzijingforum.f01_account.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 本文件要完成：
 * 映射 F01 的 user_account 数据表。
 */
@lombok.Data
@TableName("user_account")
public class UserAccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String account;
    /** BCrypt 哈希，不是明文密码。 */
    private String passwordHash;
    private String email;
    private String mobile;
    private String role;
    private String status;
    /** 修改密码时递增，用于让旧 JWT 失效。 */
    private Integer tokenVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isBanned() {
        return "BANNED".equals(status);
    }


}
