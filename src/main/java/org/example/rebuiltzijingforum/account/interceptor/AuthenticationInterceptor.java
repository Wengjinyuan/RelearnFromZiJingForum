package org.example.rebuiltzijingforum.account.interceptor;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.rebuiltzijingforum.account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.account.dto.TokenUserDTO;
import org.example.rebuiltzijingforum.account.entity.UserAccountEntity;
import org.example.rebuiltzijingforum.account.mapper.UserAccountMapper;
import org.example.rebuiltzijingforum.account.utils.JwtUtils;
import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 在 Controller 执行前验证 Bearer Token。 */
@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER = "currentUser";

    private final JwtUtils jwtUtils;
    private final UserAccountMapper userAccountMapper;
    private final ObjectMapper objectMapper;

    public AuthenticationInterceptor(
            JwtUtils jwtUtils,
            UserAccountMapper userAccountMapper,
            ObjectMapper objectMapper) {
        this.jwtUtils = jwtUtils;
        this.userAccountMapper = userAccountMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            TokenUserDTO tokenUserDTO = jwtUtils.parseToken(extractToken(request.getHeader("Authorization")));
            UserAccountEntity entity = userAccountMapper.selectById(tokenUserDTO.userId());
            if (entity == null) {
                throw unauthorized("账号不存在");
            }
            if (!entity.getTokenVersion().equals(tokenUserDTO.tokenVersion())) {
                throw new BusinessException(
                        "TOKEN_REVOKED", "登录凭证已失效，请重新登录", HttpStatus.UNAUTHORIZED);
            }
            if (entity.isBanned()) {
                throw new BusinessException("ACCOUNT_BANNED", "账号已被禁用", HttpStatus.FORBIDDEN);
            }

            // Controller 只能使用这个由服务端创建的 DTO，不能相信客户端提交的 userId。
            request.setAttribute(CURRENT_USER,
                    new CurrentUserDTO(entity.getId(), entity.getAccount(), entity.getRole()));
            return true;
        } catch (BusinessException exception) {
            writeError(response, exception.getStatus(), exception.getCode(), exception.getMessage());
            return false;
        } catch (RuntimeException exception) {
            writeError(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误");
            return false;
        }
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw unauthorized("请先登录");
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            throw unauthorized("请先登录");
        }
        return token;
    }

    private BusinessException unauthorized(String message) {
        return new BusinessException("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message) {
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try {
            objectMapper.writeValue(response.getWriter(), ApiResponse.failure(code, message));
        } catch (IOException exception) {
            throw new IllegalStateException("无法写出鉴权错误响应", exception);
        }
    }
}
