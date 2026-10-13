package org.example.rebuiltzijingforum.f09_home.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.rebuiltzijingforum.f09_home.vo.HomeStatisticsVO;
import org.example.rebuiltzijingforum.f09_home.vo.HotTagVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** F09 可选 Redis 快照：缓存可丢弃，MySQL 聚合始终是真相。 */
@Component
@ConditionalOnProperty(name = "forum.redis.enabled", havingValue = "true")
public class HomeCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    /** 注入 Redis 和 JSON 编解码器；默认本地 H2 模式不会创建该 Bean。 */
    public HomeCache(StringRedisTemplate redis, ObjectMapper json) { this.redis = redis; this.json = json; }

    /** 读 60 秒新鲜首页快照；key 不存在返回 empty 让 Service 回源。 */
    public Optional<HomeStatisticsVO> overview() { return read("home:overview", HomeStatisticsVO.class); }
    /** 新鲜值之外保留 10 分钟旧快照，抢不到刷新锁时可先展示旧值。 */
    public Optional<HomeStatisticsVO> staleOverview() { return read("home:overview:stale", HomeStatisticsVO.class); }

    /** 写新鲜/旧快照；Redis 故障由 Service 捕获并直接读 MySQL。 */
    public void saveOverview(HomeStatisticsVO value) {
        write("home:overview", value, Duration.ofSeconds(60));
        write("home:overview:stale", value, Duration.ofMinutes(10));
    }

    /** 按 limit 分开缓存热门标签，不能让 limit=5 误拿 limit=20 的结果。 */
    public Optional<List<HotTagVO>> hotTags(int limit) {
        String raw = redis.opsForValue().get("home:tags:" + limit);
        if (raw == null) return Optional.empty();
        try { return Optional.of(json.readValue(raw, new TypeReference<List<HotTagVO>>() {})); }
        catch (Exception broken) { throw new IllegalStateException("热门标签缓存无法解码", broken); }
    }

    /** 热门标签缓存可随时从 SQL 重建。 */
    public void saveHotTags(int limit, List<HotTagVO> tags) { write("home:tags:" + limit, tags, Duration.ofSeconds(60)); }

    /** 统一 JSON 读取；类型由调用方明确指定。 */
    private <T> Optional<T> read(String key, Class<T> type) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) return Optional.empty();
        try { return Optional.of(json.readValue(raw, type)); }
        catch (Exception broken) { throw new IllegalStateException("首页缓存无法解码", broken); }
    }

    /** 统一 JSON 写入并设置 TTL，避免旧统计永久留存。 */
    private void write(String key, Object value, Duration ttl) {
        try { redis.opsForValue().set(key, json.writeValueAsString(value), ttl); }
        catch (Exception broken) { throw new IllegalStateException("首页缓存无法写入", broken); }
    }
}
