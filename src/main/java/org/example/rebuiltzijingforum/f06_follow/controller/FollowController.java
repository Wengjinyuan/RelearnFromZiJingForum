package org.example.rebuiltzijingforum.f06_follow.controller;

import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f06_follow.service.FollowService;
import org.example.rebuiltzijingforum.f06_follow.dto.FollowPageDTO;
import org.example.rebuiltzijingforum.f06_follow.vo.FollowStatusVO;
import org.example.rebuiltzijingforum.f06_follow.vo.FollowUserVO;
import org.example.rebuiltzijingforum.f06_follow.vo.FollowFeedVO;
import org.springframework.web.bind.annotation.*;

/** 前端个人页把关注按钮和列表请求送到这里；Controller 把用户 id 传给 Service。 */
@RestController
@RequestMapping("/api/follows")
public class FollowController {
    private final FollowService service;

    /** Spring 注入关注业务层。 */
    public FollowController(FollowService service) { this.service = service; }

    /** PUT 表示确保已关注；实际 INSERT 由 FollowMapper.add 执行。 */
    @PutMapping("/{targetId}")
    public ApiResponse<FollowStatusVO> follow(@PathVariable long targetId,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return ApiResponse.success(service.follow(user.userId(), targetId));
    }

    /** DELETE 表示确保未关注；Service 处理重复取关。 */
    @DeleteMapping("/{targetId}")
    public ApiResponse<FollowStatusVO> unfollow(@PathVariable long targetId,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return ApiResponse.success(service.unfollow(user.userId(), targetId));
    }

    /** 打开他人个人页时查询当前用户是否关注了他。 */
    @GetMapping("/{targetId}/status")
    public ApiResponse<FollowStatusVO> status(@PathVariable long targetId,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return ApiResponse.success(service.status(user.userId(), targetId));
    }

    /** 公开展示某用户的关注列表，Mapper 使用 follower_id 过滤。 */
    @GetMapping("/users/{userId}/following")
    public ApiResponse<PageResponse<FollowUserVO>> following(@PathVariable long userId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.following(userId, new FollowPageDTO(page, size)));
    }

    /** 公开展示粉丝列表，Mapper 使用 followed_id 过滤。 */
    @GetMapping("/users/{userId}/followers")
    public ApiResponse<PageResponse<FollowUserVO>> followers(@PathVariable long userId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.followers(userId, new FollowPageDTO(page, size)));
    }

    /** 当前登录用户的关注流：Service.feed → FollowMapper.feed 的 JOIN 查询。 */
    @GetMapping("/feed")
    public ApiResponse<PageResponse<FollowFeedVO>> feed(
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.feed(user.userId(), new FollowPageDTO(page, size)));
    }
}
