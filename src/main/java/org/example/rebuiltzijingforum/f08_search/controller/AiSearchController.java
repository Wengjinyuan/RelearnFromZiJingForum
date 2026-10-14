package org.example.rebuiltzijingforum.f08_search.controller;

import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f08_search.dto.AiSearchRequest;
import org.example.rebuiltzijingforum.f08_search.service.AiSearchService;
import org.example.rebuiltzijingforum.f08_search.vo.AiSearchResponse;
import org.example.rebuiltzijingforum.f08_search.vo.AiSearchStatusVO;
import org.springframework.web.bind.annotation.*;

/** 登录用户才能调用会产生外部 API 成本的 AI 搜索。 */
@RestController
@RequestMapping("/api/search/ai")
public class AiSearchController {
    private final AiSearchService service;

    public AiSearchController(AiSearchService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public ApiResponse<AiSearchStatusVO> status(
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO ignored) {
        return ApiResponse.success(service.status());
    }

    @PostMapping
    public ApiResponse<AiSearchResponse> answer(
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO ignored,
            @RequestBody AiSearchRequest request) {
        return ApiResponse.success(service.answer(request.question()));
    }
}
