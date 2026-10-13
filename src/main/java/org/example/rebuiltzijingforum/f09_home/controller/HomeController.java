package org.example.rebuiltzijingforum.f09_home.controller;

import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.f09_home.service.HomeService;
import org.example.rebuiltzijingforum.f09_home.vo.HomeStatisticsVO;
import org.example.rebuiltzijingforum.f09_home.vo.HotTagVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 前端首页在页面加载时匿名调用两个 GET 接口。 */
@RestController
@RequestMapping("/api/home")
public class HomeController {
    private final HomeService service;

    /** Spring 注入 Service；Controller 只处理 HTTP 输入输出。 */
    public HomeController(HomeService service) { this.service = service; }

    /** 统计卡片入口：Service.overview → HomeMapper 的四条 COUNT SQL。 */
    @GetMapping
    public ApiResponse<HomeStatisticsVO> overview() { return ApiResponse.success(service.overview()); }

    /** 热门标签入口：Service.hotTags → Mapper 的 JOIN/GROUP BY/ORDER BY。 */
    @GetMapping("/hot-tags")
    public ApiResponse<List<HotTagVO>> hotTags(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(service.hotTags(limit));
    }
}
