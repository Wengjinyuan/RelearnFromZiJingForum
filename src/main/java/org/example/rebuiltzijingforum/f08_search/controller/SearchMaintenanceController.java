package org.example.rebuiltzijingforum.f08_search.controller;

import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f08_search.service.SearchIndexService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/** 可选 ES 的教学运维入口；普通用户不能触发耗时重建。 */
@RestController
@ConditionalOnProperty(name = "forum.search.elasticsearch.enabled", havingValue = "true")
public class SearchMaintenanceController {
    private final SearchIndexService indexes;

    public SearchMaintenanceController(SearchIndexService indexes) { this.indexes = indexes; }

    /** 管理员 POST → 验角色 → 新索引导入/数量核对/别名切换；默认 ES 关闭时本接口不存在。 */
    @PostMapping("/api/admin/search/rebuild")
    public ApiResponse<String> rebuild(@RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO actor) {
        if (!"ADMIN".equals(actor.role()))
            throw new BusinessException("ADMIN_REQUIRED", "需要管理员权限", HttpStatus.FORBIDDEN);
        indexes.rebuild();
        return ApiResponse.success("搜索索引重建完成");
    }
}
