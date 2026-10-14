package org.example.rebuiltzijingforum.f08_search.controller;

import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f08_search.service.SearchService;
import org.example.rebuiltzijingforum.f08_search.dto.SearchDTO;
import org.example.rebuiltzijingforum.f08_search.vo.PostSearchVO;
import org.example.rebuiltzijingforum.f08_search.vo.UserSearchVO;
import org.springframework.web.bind.annotation.*;

/** 前端搜索框发 GET 请求；无需 JWT，因为搜索公开内容。 */
@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService service;

    /** Spring 注入 Service，Controller 不拼 SQL。 */
    public SearchController(SearchService service) { this.service = service; }

    /** 帖子搜索入口：Service.posts 调用 Mapper 的 COUNT 与 SELECT。 */
    @GetMapping("/posts")
    public ApiResponse<PageResponse<PostSearchVO>> posts(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(required = false) String category) {
        return ApiResponse.success(service.posts(new SearchDTO(q == null ? keyword : q, page, size, sort, category)));
    }

    /** 用户搜索入口：只搜账号、昵称和签名，返回公开字段。 */
    @GetMapping("/users")
    public ApiResponse<PageResponse<UserSearchVO>> users(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.users(new SearchDTO(q == null ? keyword : q, page, size, "relevance", null)));
    }
}
