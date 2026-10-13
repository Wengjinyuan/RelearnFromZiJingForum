package org.example.rebuiltzijingforum.f04_comment.controller;

import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f04_comment.service.CommentService;
import org.example.rebuiltzijingforum.f04_comment.dto.CreateCommentDTO;
import org.example.rebuiltzijingforum.f04_comment.dto.CommentPageDTO;
import org.example.rebuiltzijingforum.f04_comment.vo.CommentVO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 前端发送 /api/... 请求到这里；Controller 提取参数和可信用户，交 Service 做业务。 */
@RestController
public class CommentController {
    private final CommentService service;

    /** Spring 注入业务层；Controller 不直接写 SQL。 */
    public CommentController(CommentService service) { this.service = service; }

    /** 前端提交正文和可选 parentId；用户 id 来自拦截器，不从 JSON 里取。 */
    @PostMapping("/api/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentVO> create(@PathVariable long postId,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user,
            @RequestBody CreateCommentDTO request) {
        return ApiResponse.success(service.create(postId, user, request));
    }

    /** 前端加载帖子下的一级评论；Service 的 roots 再调用 Mapper 查询总数和当前页。 */
    @GetMapping("/api/posts/{postId}/comments")
    public ApiResponse<PageResponse<CommentVO>> roots(@PathVariable long postId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "hot") String sort) {
        return ApiResponse.success(service.roots(postId, new CommentPageDTO(page, size, sort)));
    }

    /** 展开楼中楼时，用 beforeId 取更早的回复，默认从最大 id 开始。 */
    @GetMapping("/api/comments/{parentId}/replies")
    public ApiResponse<List<CommentVO>> replies(@PathVariable long parentId,
            @RequestParam(defaultValue = "9223372036854775807") long beforeId,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.replies(parentId, beforeId, size));
    }

    /** 删除请求携带 JWT；Service.delete 再检查该评论是否属于当前用户。 */
    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO user) { service.delete(id, user); }

}
