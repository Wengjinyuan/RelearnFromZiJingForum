package org.example.rebuiltzijingforum.f05_interaction.controller;

import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f05_interaction.service.InteractionService;
import org.example.rebuiltzijingforum.f05_interaction.dto.InteractionDTO;
import org.example.rebuiltzijingforum.f05_interaction.vo.InteractionVO;
import org.example.rebuiltzijingforum.f05_interaction.vo.PostInteractionVO;
import org.springframework.web.bind.annotation.*;

/** 前端按钮发送 PUT/DELETE；Controller 提取 JWT 对应用户并把操作交给 Service。 */
@RestController
@RequestMapping("/api/interactions")
public class InteractionController {
    private final InteractionService service;

    /** 注入业务层；此处不直接改计数。 */
    public InteractionController(InteractionService service) { this.service = service; }

    /** 点赞帖子：Service.like → Mapper INSERT like_record → UPDATE post.like_count。 */
    @PutMapping("/posts/{id}/like")
    public ApiResponse<InteractionVO> likePost(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return ApiResponse.success(service.like(user.userId(), new InteractionDTO("POST", id)));
    }

    /** 取消帖子点赞；只有关系真实存在，计数才会减一。 */
    @DeleteMapping("/posts/{id}/like")
    public ApiResponse<InteractionVO> unlikePost(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return ApiResponse.success(service.unlike(user.userId(), new InteractionDTO("POST", id)));
    }

    /** 点赞评论，关系表 target_type=COMMENT，计数落到 comment 表。 */
    @PutMapping("/comments/{id}/like")
    public ApiResponse<InteractionVO> likeComment(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return ApiResponse.success(service.like(user.userId(), new InteractionDTO("COMMENT", id)));
    }

    /** 取消评论点赞。 */
    @DeleteMapping("/comments/{id}/like")
    public ApiResponse<InteractionVO> unlikeComment(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return ApiResponse.success(service.unlike(user.userId(), new InteractionDTO("COMMENT", id)));
    }

    /** 收藏帖子：Service 验证帖子后写 favorite 关系和冗余计数。 */
    @PutMapping("/posts/{id}/favorite")
    public ApiResponse<InteractionVO> favorite(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return ApiResponse.success(service.favorite(user.userId(), id));
    }

    /** 取消收藏。 */
    @DeleteMapping("/posts/{id}/favorite")
    public ApiResponse<InteractionVO> unfavorite(@PathVariable long id, @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return ApiResponse.success(service.unfavorite(user.userId(), id));
    }

    /** 详情页的 PV 上报；匿名可调用，不能当作独立访客 UV。 */
    @PostMapping("/posts/{id}/view")
    public ApiResponse<Void> view(@PathVariable long id) { service.view(id); return ApiResponse.success(null); }

    /** 页面刷新时读当前用户的点赞/收藏状态和帖子计数。 */
    @GetMapping("/posts/{id}/state")
    public ApiResponse<PostInteractionVO> state(@PathVariable long id,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) {
        return ApiResponse.success(service.postState(user.userId(), id));
    }
}
