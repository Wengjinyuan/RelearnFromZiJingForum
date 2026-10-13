package org.example.rebuiltzijingforum.f11_admin.controller;

import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f11_admin.dto.PostAuditDTO;
import org.example.rebuiltzijingforum.f11_admin.service.AdminPostService;
import org.example.rebuiltzijingforum.f11_admin.vo.AdminPostVO;
import org.example.rebuiltzijingforum.f11_admin.vo.AuditLogVO;
import org.springframework.web.bind.annotation.*;

/** F11 帖子治理 HTTP 入口；Service 执行状态机和管理员二次授权。 */
@RestController
@RequestMapping("/api/admin")
public class AdminPostController {
    private final AdminPostService service;
    /** 注入帖子治理业务接口。 */
    public AdminPostController(AdminPostService service) { this.service = service; }

    /** 待审/已发布/已驳回列表 GET → Service.posts → AdminMapper 查询。 */
    @GetMapping("/posts")
    public ApiResponse<PageResponse<AdminPostVO>> posts(@RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) { return ApiResponse.success(service.posts(actor, status, page, size)); }

    /** 审核通过 PUT → 只允许 PENDING → PUBLISHED，状态与审计同事务。 */
    @PutMapping("/posts/{id}/approve")
    public ApiResponse<AdminPostVO> approve(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor,
            @RequestBody PostAuditDTO body) { return ApiResponse.success(service.changeStatus(actor, id, "APPROVE", body)); }

    /** 审核拒绝 PUT → 只允许 PENDING → REJECTED。 */
    @PutMapping("/posts/{id}/reject")
    public ApiResponse<AdminPostVO> reject(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor,
            @RequestBody PostAuditDTO body) { return ApiResponse.success(service.changeStatus(actor, id, "REJECT", body)); }

    /** 管理删除 PUT → 帖子不再进入普通列表/搜索。 */
    @PutMapping("/posts/{id}/delete")
    public ApiResponse<AdminPostVO> delete(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor,
            @RequestBody PostAuditDTO body) { return ApiResponse.success(service.changeStatus(actor, id, "DELETE", body)); }

    /** 置顶 PUT → Service.setFlag → Mapper 只改 is_top。 */
    @PutMapping("/posts/{id}/top")
    public ApiResponse<AdminPostVO> top(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor,
            @RequestBody PostAuditDTO body) { return ApiResponse.success(service.setFlag(actor, id, "TOP", body)); }

    /** 精华 PUT → Service.setFlag → Mapper 只改 is_essence。 */
    @PutMapping("/posts/{id}/essence")
    public ApiResponse<AdminPostVO> essence(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor,
            @RequestBody PostAuditDTO body) { return ApiResponse.success(service.setFlag(actor, id, "ESSENCE", body)); }

    /** 审计页 GET → Service.admin → AuditLogMapper 的分页 SELECT。 */
    @GetMapping("/audit")
    public ApiResponse<PageResponse<AuditLogVO>> audits(@RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.audits(actor, page, size));
    }
}
