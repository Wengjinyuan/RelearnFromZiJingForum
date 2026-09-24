package org.example.rebuiltzijingforum.account.utils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.rebuiltzijingforum.account.dto.TokenDTO;
import org.example.rebuiltzijingforum.account.dto.TokenUserDTO;
import org.example.rebuiltzijingforum.account.entity.UserAccountEntity;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** JWT 生成和解析工具类。 */
@Component
public class JwtUtils {

    private final SecretKey key;
    private final long expirationSeconds;
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

    /** 根据用户实体生成带签名和过期时间的 JWT。 */
    public TokenDTO createToken(UserAccountEntity entity) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);
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

    /** 验证 JWT 签名和过期时间，并返回其中的用户 DTO。 */
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
