package org.example.rebuiltzijingforum.f02_profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.rebuiltzijingforum.f02_profile.entity.UserProfileEntity;

/**
 * 本文件要完成：
 * 提供 user_profile 表的基础增删改查操作。
 * 接上@MAPPER就可以了
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfileEntity> {
}
