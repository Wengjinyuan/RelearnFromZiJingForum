package org.example.rebuiltzijingforum.f01_account.interceptor;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.dto.TokenUserDTO;
import org.example.rebuiltzijingforum.f01_account.entity.UserAccountEntity;
import org.example.rebuiltzijingforum.f01_account.mapper.UserAccountMapper;
import org.example.rebuiltzijingforum.f01_account.utils.JwtUtils;
import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 本文件要完成：
 * 在 Controller 前校验 Bearer Token 并写入可信用户属性。
 */
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

    /**
     * 模板                                              案例
     * 从请求头取 Token → 解析载荷                       Authorization → userId=1001
     * 查数据库 → 校验版本和账号状态                     tokenVer=2，dbVer=2
     * request.setAttribute("属性名", 可信DTO)           request.setAttribute("user", userDTO)
     * 成功 return true；失败写响应并 return false       无 Token → 401 + false
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // F03/F04 公开读、公开粉丝列表和匿名 PV 单独放行；写操作与私有读仍需 JWT。
        String path = request.getRequestURI();
        boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        if ((read && (path.equals("/api/posts") || path.startsWith("/api/posts/")
                || path.matches("/api/comments/\\d+/replies")
                || path.matches("/api/follows/users/\\d+/(following|followers)")))
                || ("POST".equals(request.getMethod()) && path.matches("/api/interactions/posts/\\d+/view"))) {
            return true;
        }
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

            /// Controller 只能使用这个由服务端创建的 DTO，不能相信客户端提交的 userId,在此保存用户状态.
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

    /**
     * 模板                                              案例
     * 校验 "Bearer " 前缀                               "Basic abc" → 拒绝
     * 截去前缀并 trim                                   "Bearer abc" → "abc"
     * Token 为空则抛未认证异常                           "Bearer   " → 401
     */
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

    /**
     * 模板                                              案例
     * response.setStatus(状态码)                         response.setStatus(401)
     * 设置 JSON 编码和 Content-Type                      UTF-8 + application/json
     * 序列化统一失败响应                                 ApiResponse.failure("NO_LOGIN", "请登录")
     */
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
