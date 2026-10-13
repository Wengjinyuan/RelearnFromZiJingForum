package org.example.rebuiltzijingforum.f03_post.controller;

import jakarta.validation.Valid;
import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f03_post.dto.CreatePostDTO;
import org.example.rebuiltzijingforum.f03_post.dto.PostPageDTO;
import org.example.rebuiltzijingforum.f03_post.dto.UpdatePostDTO;
import org.example.rebuiltzijingforum.f03_post.service.PostService;
import org.example.rebuiltzijingforum.f03_post.vo.PostDetailVO;
import org.example.rebuiltzijingforum.f03_post.vo.PostSummaryVO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** F03 帖子 HTTP 接口。读接口公开，写接口由鉴权拦截器注入可信用户。 */
@RestController
@RequestMapping("/api/posts")
public class PostController {
    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostDetailVO> createPost(
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO currentUser,
            @Valid @RequestBody CreatePostDTO dto) {
        return ApiResponse.success(postService.createPost(currentUser, dto));
    }

    @PutMapping("/{postId}")
    public ApiResponse<PostDetailVO> updatePost(
            @PathVariable long postId,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO currentUser,
            @Valid @RequestBody UpdatePostDTO dto) {
        return ApiResponse.success(postService.updatePost(postId, currentUser, dto));
    }

    @GetMapping
    public ApiResponse<PageResponse<PostSummaryVO>> getPosts(@ModelAttribute PostPageDTO query) {
        return ApiResponse.success(postService.getPosts(query));
    }

    @GetMapping("/top")
    public ApiResponse<List<PostSummaryVO>> getTopPosts() {
        return ApiResponse.success(postService.getTopPosts());
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostDetailVO> getPost(@PathVariable long postId) {
        return ApiResponse.success(postService.getPost(postId));
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(
            @PathVariable long postId,
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO currentUser) {
        postService.deletePost(postId, currentUser);
    }
}
