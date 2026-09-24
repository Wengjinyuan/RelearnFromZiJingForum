package org.example.rebuiltzijingforum.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.rebuiltzijingforum.account.entity.UserAccountEntity;

/** user_account 表的 MyBatis-Plus Mapper。 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccountEntity> {
}
