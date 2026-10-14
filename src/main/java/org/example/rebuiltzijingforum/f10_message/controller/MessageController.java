package org.example.rebuiltzijingforum.f10_message.controller;

import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f10_message.service.MessageService;
import org.example.rebuiltzijingforum.f10_message.dto.SendMessageDTO;
import org.example.rebuiltzijingforum.f10_message.dto.MessagePageDTO;
import org.example.rebuiltzijingforum.f10_message.vo.MessageVO;
import org.example.rebuiltzijingforum.f10_message.vo.ConversationVO;
import org.example.rebuiltzijingforum.f10_message.config.WebSocketConfig.MessageSseHub;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** F10 HTTP 入口；所有 currentUser 都来自 F01 的 AuthenticationInterceptor.preHandle。 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService service;
    private final MessageSseHub live;

    /** Spring 注入业务层。 */
    public MessageController(MessageService service, MessageSseHub live) { this.service = service; this.live = live; }

    /** 前端 POST JSON → Service.send 验内容/收件人 → Mapper 写三张表。 */
    @PostMapping("/users/{targetId}")
    public ApiResponse<MessageVO> send(@PathVariable long targetId,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user, @RequestBody SendMessageDTO body) {
        return ApiResponse.success(service.send(user.userId(), targetId, body));
    }

    /** 前端打开私信页 → Service → Mapper JOIN 查询会话卡片。 */
    @GetMapping("/conversations")
    public ApiResponse<List<ConversationVO>> conversations(
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return ApiResponse.success(service.conversations(user.userId()));
    }

    /** 顶部消息红点只统计私信未读，避免把点赞、评论通知误算成私信。 */
    @GetMapping("/unread-count")
    public ApiResponse<MessageUnreadCount> unreadCount(
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return ApiResponse.success(new MessageUnreadCount(service.unreadCount(user.userId())));
    }

    /** 前端上滑加载旧消息 → Service.requireMember → Mapper.history。 */
    @GetMapping("/conversations/{id}")
    public ApiResponse<List<MessageVO>> history(@PathVariable long id,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user,
            @RequestParam(defaultValue = "9223372036854775807") long beforeId, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.history(user.userId(), id, new MessagePageDTO(beforeId, size)));
    }

    /** 前端打开会话 → Service.requireMember → Mapper 仅清当前用户未读。 */
    @PutMapping("/conversations/{id}/read")
    public ApiResponse<Void> read(@PathVariable long id,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        service.read(user.userId(), id); return ApiResponse.success(null);
    }

    /** 登录后打开 SSE 流；收到 message 事件后再用 history 接口补拉，防止断线漏消息。 */
    @GetMapping(path = "/stream", produces = "text/event-stream")
    public SseEmitter stream(@RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return live.subscribe(user.userId());
    }

    public record MessageUnreadCount(long count) {}

}
