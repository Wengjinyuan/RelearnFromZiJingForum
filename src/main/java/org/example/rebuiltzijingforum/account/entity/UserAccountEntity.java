package org.example.rebuiltzijingforum.account.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
@lombok.Data
/** user_account 表对应的实体类。 */
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
