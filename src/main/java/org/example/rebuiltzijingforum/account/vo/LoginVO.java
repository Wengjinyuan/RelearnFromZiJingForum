package org.example.rebuiltzijingforum.account.vo;

import java.time.Instant;

import org.example.rebuiltzijingforum.account.dto.TokenDTO;
import org.example.rebuiltzijingforum.account.entity.UserAccountEntity;

/** 登录成功后返回给前端的 VO。 */
public record LoginVO(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        AccountVO user) {

    public static LoginVO from(TokenDTO tokenDTO, UserAccountEntity entity) {
        return new LoginVO(tokenDTO.token(), "Bearer", tokenDTO.expiresAt(), AccountVO.from(entity));
    }
}
