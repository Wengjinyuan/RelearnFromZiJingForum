package org.example.rebuiltzijingforum.f02_profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * 本文件要完成：
 * 映射 user_profile 个人资料表。
 */
@Data
@TableName("user_profile")
public class UserProfileEntity {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;
    private String displayName;
    private String avatarUrl;
    private String gender;
    private String signature;
    private String major;
    private LocalDate birthday;
    private LocalDateTime updatedAt;
}
