package org.example.rebuiltzijingforum.f08_search.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f08_search.config.ElasticsearchConfig.ElasticsearchGateway;
import org.example.rebuiltzijingforum.f08_search.dto.SearchDTO;
import org.example.rebuiltzijingforum.f08_search.mapper.SearchMapper;
import org.example.rebuiltzijingforum.f08_search.service.SearchIndexService;
import org.example.rebuiltzijingforum.f08_search.vo.PostSearchVO;
import org.example.rebuiltzijingforum.f08_search.vo.UserSearchVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ES 是 MySQL 的副本：这里管建索引、按 ID 同步、搜索与新索引重建。 */
@Service
@ConditionalOnProperty(name = "forum.search.elasticsearch.enabled", havingValue = "true")
public class SearchIndexServiceImpl implements SearchIndexService {
    private static final String POSTS = "forum_posts";
    private static final String USERS = "forum_users";
    private final ElasticsearchGateway es;
    private final SearchMapper source;

    /** Spring 注入 ES 传输层与 MySQL 事实源 Mapper；ES 不能替代 MySQL 保存原始帖子。 */
    public SearchIndexServiceImpl(ElasticsearchGateway es, SearchMapper source) { this.es = es; this.source = source; }

    /** 帖子已发布就 upsert；被删除/驳回/待审则从索引删掉。 */
    @Override
    public void syncPost(long id) {
        ensure(POSTS, postMapping());
        source.indexPost(id).ifPresentOrElse(doc -> es.request("PUT", "/" + POSTS + "/_doc/" + id + "?refresh=wait_for", doc),
                () -> { if (es.exists("/" + POSTS + "/_doc/" + id)) es.request("DELETE", "/" + POSTS + "/_doc/" + id + "?refresh=wait_for", null); });
    }

    /** 封禁后回查不到该用户，索引随之删除。 */
    @Override
    public void syncUser(long id) {
        ensure(USERS, userMapping());
        source.indexUser(id).ifPresentOrElse(doc -> es.request("PUT", "/" + USERS + "/_doc/" + id + "?refresh=wait_for", doc),
                () -> { if (es.exists("/" + USERS + "/_doc/" + id)) es.request("DELETE", "/" + USERS + "/_doc/" + id + "?refresh=wait_for", null); });
    }

    /** 新索引导入并核对数量后，原子切换别名；旧物理索引留着供人工确认后回收。 */
    @Override
    public void rebuild() {
        rebuildPosts();
        rebuildUsers();
    }

    /** 帖子重建：新物理索引 → 主键游标逐页导入 → _refresh → COUNT 核对 → 切别名。 */
    private void rebuildPosts() {
        String next = POSTS + "_" + System.currentTimeMillis();
        es.request("PUT", "/" + next, postMapping());
        long cursor = 0;
        while (true) {
            List<Long> ids = source.postIdsAfter(cursor, 200);
            if (ids.isEmpty()) break;
            for (Long id : ids) source.indexPost(id).ifPresent(doc -> es.request("PUT", "/" + next + "/_doc/" + id, doc));
            cursor = ids.get(ids.size() - 1);
        }
        es.request("POST", "/" + next + "/_refresh", Map.of());
        long indexed = es.request("GET", "/" + next + "/_count", null).path("count").asLong();
        if (indexed != source.publishedPostCount()) throw new IllegalStateException("帖子索引数量校验未通过，未切换别名");
        switchAlias(POSTS, next);
    }

    /** 用户重建采用同样步骤，只取可公开搜索、未封禁的账号。 */
    private void rebuildUsers() {
        String next = USERS + "_" + System.currentTimeMillis();
        es.request("PUT", "/" + next, userMapping());
        long cursor = 0;
        while (true) {
            List<Long> ids = source.userIdsAfter(cursor, 200);
            if (ids.isEmpty()) break;
            for (Long id : ids) source.indexUser(id).ifPresent(doc -> es.request("PUT", "/" + next + "/_doc/" + id, doc));
            cursor = ids.get(ids.size() - 1);
        }
        es.request("POST", "/" + next + "/_refresh", Map.of());
        long indexed = es.request("GET", "/" + next + "/_count", null).path("count").asLong();
        if (indexed != source.searchableUserCount()) throw new IllegalStateException("用户索引数量校验未通过，未切换别名");
        switchAlias(USERS, next);
    }

    /** _aliases 的 actions 在 ES 中一次执行，读请求不会看到一个短暂不存在的别名。 */
    private void switchAlias(String alias, String next) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (es.exists("/_alias/" + alias)) {
            JsonNode old = es.request("GET", "/_alias/" + alias, null);
            old.fieldNames().forEachRemaining(name -> actions.add(Map.of("remove", Map.of("index", name, "alias", alias))));
        }
        actions.add(Map.of("add", Map.of("index", next, "alias", alias)));
        es.request("POST", "/_aliases", Map.of("actions", actions));
    }

    /** multi_match 区分标题/摘要/标签权重；只在别名已创建后查询。 */
    @Override
    public PageResponse<PostSearchVO> posts(SearchDTO query) {
        JsonNode result = es.request("POST", "/" + POSTS + "/_search", Map.of(
                "from", (query.page() - 1) * query.size(), "size", query.size(),
                "track_total_hits", true,
                "query", Map.of("multi_match", Map.of("query", query.keyword(), "fields", List.of("title^3", "summary", "tags^2"))),
                "sort", "latest".equals(query.sort()) ? List.of(Map.of("createdAt", "desc"), Map.of("id", "desc"))
                        : List.of(Map.of("_score", "desc"), Map.of("createdAt", "desc"), Map.of("id", "desc"))));
        List<PostSearchVO> items = new ArrayList<>();
        for (JsonNode hit : result.path("hits").path("hits")) {
            JsonNode row = hit.path("_source");
            items.add(new PostSearchVO(row.path("id").asLong(), row.path("title").asText(), row.path("summary").asText(),
                    row.path("authorId").asLong(), LocalDateTime.parse(row.path("createdAt").asText()), null, null));
        }
        return new PageResponse<>(items, result.path("hits").path("total").path("value").asLong(), query.page(), query.size());
    }

    /** ES 命中 _source 后只提取 VO 允许展示的字段，不把全文正文返给浏览器。 */
    @Override
    public PageResponse<UserSearchVO> users(SearchDTO query) {
        JsonNode result = es.request("POST", "/" + USERS + "/_search", Map.of(
                "from", (query.page() - 1) * query.size(), "size", query.size(),
                "track_total_hits", true,
                "query", Map.of("multi_match", Map.of("query", query.keyword(), "fields", List.of("account^3", "displayName^2", "signature"))),
                "sort", List.of(Map.of("_score", "desc"), Map.of("id", "desc"))));
        List<UserSearchVO> items = new ArrayList<>();
        for (JsonNode hit : result.path("hits").path("hits")) {
            JsonNode row = hit.path("_source");
            items.add(new UserSearchVO(row.path("id").asLong(), row.path("account").asText(),
                    row.path("displayName").asText(null), row.path("signature").asText(null), null, null));
        }
        return new PageResponse<>(items, result.path("hits").path("total").path("value").asLong(), query.page(), query.size());
    }

    /** 第一次写入时创建带 mapping 的初始物理索引，再给它一个稳定别名。 */
    private void ensure(String alias, Map<String, Object> mapping) {
        if (es.exists("/_alias/" + alias)) return;
        String initial = alias + "_initial";
        if (!es.exists("/" + initial)) es.request("PUT", "/" + initial, mapping);
        switchAlias(alias, initial);
    }

    /** standard 是 ES 内置分析器；生产中文分词可替换为已安装的 IK 等分析器。 */
    private Map<String, Object> postMapping() {
        return Map.of("mappings", Map.of("properties", Map.of(
                "id", Map.of("type", "long"), "title", Map.of("type", "text", "analyzer", "standard"),
                "summary", Map.of("type", "text", "analyzer", "standard"),
                "tags", Map.of("type", "text", "analyzer", "standard"),
                "authorId", Map.of("type", "long"), "createdAt", Map.of("type", "date"),
                "sourceUpdatedAt", Map.of("type", "date"))));
    }

    /** 用户 mapping 不包含邮箱、手机号、密码；公开搜索只索引账号、昵称和签名。 */
    private Map<String, Object> userMapping() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", Map.of("type", "long"));
        fields.put("account", Map.of("type", "text", "analyzer", "standard"));
        fields.put("displayName", Map.of("type", "text", "analyzer", "standard"));
        fields.put("signature", Map.of("type", "text", "analyzer", "standard"));
        fields.put("sourceUpdatedAt", Map.of("type", "date"));
        return Map.of("mappings", Map.of("properties", fields));
    }
}
