package org.example.rebuiltzijingforum.common.web;

import java.util.List;

import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/features")
public class FeatureCatalogController {

    private static final List<FeatureView> FEATURES = List.of(
            new FeatureView("F01", "account", "账号注册、登录、鉴权与重置密码"),
            new FeatureView("F02", "profile", "个人资料与头像"),
            new FeatureView("F03", "post", "帖子、标签、分类、列表与详情"),
            new FeatureView("F04", "comment", "评论与楼中楼回复"),
            new FeatureView("F05", "interaction", "点赞、收藏与浏览计数"),
            new FeatureView("F06", "follow", "关注、粉丝与关注流"),
            new FeatureView("F07", "notification", "通知中心"),
            new FeatureView("F08", "search", "帖子与用户搜索"),
            new FeatureView("F09", "home", "首页统计与热门标签"),
            new FeatureView("F10", "message", "私信"),
            new FeatureView("F11", "admin", "后台用户与帖子治理"));

    @GetMapping
    public ApiResponse<List<FeatureView>> list() {
        return ApiResponse.success(FEATURES);
    }

    public record FeatureView(String id, String packageName, String responsibility) {
    }
}

