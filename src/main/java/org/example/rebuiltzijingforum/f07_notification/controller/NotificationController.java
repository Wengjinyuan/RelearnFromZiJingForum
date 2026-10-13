package org.example.rebuiltzijingforum.f07_notification.controller;

import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f07_notification.service.NotificationService;
import org.example.rebuiltzijingforum.f07_notification.dto.NotificationPageDTO;
import org.example.rebuiltzijingforum.f07_notification.vo.NotificationVO;
import org.example.rebuiltzijingforum.f07_notification.vo.UnreadCountVO;
import org.springframework.web.bind.annotation.*;

/** 前端消息页的 HTTP 入口；所有接口都从 JWT 属性取 userId。 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;

    /** Spring 注入通知业务层。 */
    public NotificationController(NotificationService service) { this.service = service; }

    /** 顶部红点：Controller → Service.unread → Mapper COUNT。 */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountVO> unread(@RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return ApiResponse.success(service.unread(user.userId()));
    }

    /** 消息列表：Service 校验页码，Mapper SQL 按 user_id/kind 分页。 */
    @GetMapping
    public ApiResponse<PageResponse<NotificationVO>> list(
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user,
            @RequestParam(required = false) String kind, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(user.userId(), new NotificationPageDTO(kind, page, size)));
    }

    /** 点击单条消息，Service.read 再用 Mapper 的 user_id 条件验证所有权。 */
    @PutMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable long id,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        service.read(user.userId(), id); return ApiResponse.success(null);
    }

    /** 一键已读，返回本次实际修改了多少条。 */
    @PutMapping("/read-all")
    public ApiResponse<Integer> readAll(@RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return ApiResponse.success(service.readAll(user.userId()));
    }
}
