package org.example.rebuiltzijingforum.f01_account.utils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.rebuiltzijingforum.f01_account.dto.TokenDTO;
import org.example.rebuiltzijingforum.f01_account.dto.TokenUserDTO;
import org.example.rebuiltzijingforum.f01_account.entity.UserAccountEntity;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 本文件要完成：
 * 生成、签名、校验并解析 F01 的 JWT。
 */
@Component
public class JwtUtils {

    private final SecretKey key;
    private final long expirationSeconds;///过期时间
    private final Clock clock;

    public JwtUtils(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-seconds}") long expirationSeconds,
            Clock clock) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("security.jwt.secret 至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
        this.clock = clock;
    }

    /**
     * 模板                                              案例
     * Jwts.builder().subject("用户标识")                Jwts.builder().subject("1001")
     * .claim("键", 值)                                 .claim("role", "USER")
     * .issuedAt(签发时间).expiration(过期时间)           .issuedAt(now).expiration(expireAt)
     * .signWith(密钥).compact()                          .signWith(key).compact()
     */
    public TokenDTO createToken(UserAccountEntity entity) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);///设定失效时间 有现在时间加上持续时间
        String token = Jwts.builder()
                .subject(entity.getId().toString())
                .claim("role", entity.getRole())
                .claim("ver", entity.getTokenVersion())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new TokenDTO(token, expiresAt);
    }

    /**
     * 模板                                              案例
     * Jwts.parser().verifyWith(密钥).build()             Jwts.parser().verifyWith(key).build()
     * .parseSignedClaims(token).getPayload()             .parseSignedClaims("abc.def.xyz").getPayload()
     * claims.get("键", 类型.class)                      claims.get("role", String.class)
     * 解析异常 → 抛统一未认证业务异常                    过期 Token → INVALID_TOKEN
     */
    public TokenUserDTO parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new TokenUserDTO(
                    Long.parseLong(claims.getSubject()),
                    claims.get("ver", Integer.class),
                    claims.get("role", String.class));
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException("INVALID_TOKEN", "登录凭证无效或已过期", HttpStatus.UNAUTHORIZED);
        }
    }
}
