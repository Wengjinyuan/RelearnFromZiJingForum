package org.example.rebuiltzijingforum.f11_admin.controller;

import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f11_admin.dto.PunishmentDTO;
import org.example.rebuiltzijingforum.f11_admin.entity.PunishmentEntity;
import org.example.rebuiltzijingforum.f11_admin.service.AdminUserService;
import org.example.rebuiltzijingforum.f11_admin.vo.AdminUserVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** F11 用户治理 HTTP 入口；身份来自 F01 拦截器，角色由 AdminUserServiceImpl 二次验证。 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService service;
    /** 注入用户治理业务接口。 */
    public AdminUserController(AdminUserService service) { this.service = service; }

    /** 后台用户页 GET → Service.users 验 ADMIN → AdminMapper COUNT/SELECT。 */
    @GetMapping
    public ApiResponse<PageResponse<AdminUserVO>> users(@RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor,
            @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.users(actor, keyword, page, size));
    }

    public record RoleBody(String role) {}

    @PutMapping("/{id}/role")
    public ApiResponse<AdminUserVO> changeRole(@PathVariable long id,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor,
            @RequestBody RoleBody body) {
        return ApiResponse.success(service.changeRole(actor, id, body.role()));
    }

    /** 禁言 PUT → Service 验到期时间/目标角色 → Mapper UPDATE + 处罚 + 审计。 */
    @PutMapping("/{id}/mute")
    public ApiResponse<AdminUserVO> mute(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor,
            @RequestBody PunishmentDTO body) { return ApiResponse.success(service.changeUser(actor, id, "MUTE", body)); }

    /** 封禁 PUT → Service 增 token_version，旧 JWT 失效。 */
    @PutMapping("/{id}/ban")
    public ApiResponse<AdminUserVO> ban(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor,
            @RequestBody PunishmentDTO body) { return ApiResponse.success(service.changeUser(actor, id, "BAN", body)); }

    /** 解封 PUT → Service 只允许从 BANNED 转到 NORMAL。 */
    @PutMapping("/{id}/unban")
    public ApiResponse<AdminUserVO> unban(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor,
            @RequestBody PunishmentDTO body) { return ApiResponse.success(service.changeUser(actor, id, "UNBAN", body)); }

    /** 解除禁言 PUT → Service 只允许从 MUTED 转到 NORMAL。 */
    @PutMapping("/{id}/unmute")
    public ApiResponse<AdminUserVO> unmute(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor,
            @RequestBody PunishmentDTO body) { return ApiResponse.success(service.changeUser(actor, id, "UNMUTE", body)); }

    /** 处罚历史 GET → Service 验 ADMIN/目标 → PunishmentMapper 按 user_id 查。 */
    @GetMapping("/{id}/punishments")
    public ApiResponse<List<PunishmentEntity>> punishments(@PathVariable long id,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor) {
        return ApiResponse.success(service.punishments(actor, id));
    }
}
