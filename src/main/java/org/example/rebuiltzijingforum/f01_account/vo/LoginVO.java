package org.example.rebuiltzijingforum.f01_account.vo;

import java.time.Instant;

import org.example.rebuiltzijingforum.f01_account.dto.TokenDTO;
import org.example.rebuiltzijingforum.f01_account.entity.UserAccountEntity;

/**
 * 本文件要完成：
 * 返回登录 Token、过期时间和安全的账号信息。
 */
public record LoginVO(
        String accessToken,
        String tokenType,
        Instant expiresAt,//过期时间
        AccountVO user) {

    /**
     * 模板                                              案例
     * return new VO(token.值(), 固定类型, 用户VO)       return new LoginVO(token.value(), "Bearer", user)
     * Entity 先转换成不含敏感字段的 VO                  UserVO.from(entity)
     */
    public static LoginVO from(TokenDTO tokenDTO, UserAccountEntity entity) {
        return new LoginVO(tokenDTO.token(), "Bearer", tokenDTO.expiresAt(), AccountVO.from(entity));
    }
}
