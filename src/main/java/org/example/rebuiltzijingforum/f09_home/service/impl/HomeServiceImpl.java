package org.example.rebuiltzijingforum.f09_home.service.impl;

import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f09_home.mapper.HomeMapper;
import org.example.rebuiltzijingforum.f09_home.service.HomeService;
import org.example.rebuiltzijingforum.f09_home.vo.HomeStatisticsVO;
import org.example.rebuiltzijingforum.f09_home.vo.HotTagVO;
import org.example.rebuiltzijingforum.f05_interaction.utils.RedisLockUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** F09 默认 MySQL 实时聚合；启用 forum.redis.enabled 后才先读可丢弃的 Redis 快照。 */
@Service
public class HomeServiceImpl implements HomeService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final HomeMapper mapper;
    private final Clock clock;
    private final ObjectProvider<HomeCache> cache;
    private final ObjectProvider<RedisLockUtils> locks;

    /** 注入聚合 Mapper 与时间源；固定 Clock 可以测试跨日边界。 */
    public HomeServiceImpl(HomeMapper mapper, Clock clock, ObjectProvider<HomeCache> cache, ObjectProvider<RedisLockUtils> locks) {
        this.mapper = mapper; this.clock = clock; this.cache = cache; this.locks = locks;
    }

    /** 首页请求：算上海当天起点，再依次取得用户总数、帖子总数、今日和昨日帖子数。 */
    @Transactional(readOnly = true)
    @Override
    public HomeStatisticsVO overview() {
        HomeCache saved = cache.getIfAvailable();
        try {
            if (saved != null) {
                var fresh = saved.overview();
                if (fresh.isPresent()) return fresh.orElseThrow();
            }
        }
        catch (RuntimeException unavailable) { /* Redis 故障不伪造零值，下面回源 MySQL。 */ }
        RedisLockUtils lock = locks.getIfAvailable();
        String token = null;
        try { if (lock != null) token = lock.tryLock("lock:home:overview", java.time.Duration.ofSeconds(5)); }
        catch (RuntimeException unavailable) { lock = null; }
        if (saved != null && lock != null && token == null) {
            try {
                var stale = saved.staleOverview();
                if (stale.isPresent()) return stale.orElseThrow();
            }
            catch (RuntimeException unavailable) { /* 没有可用旧值则回源。 */ }
        }
        try {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        LocalDateTime start = today.atStartOfDay();
        HomeStatisticsVO result = new HomeStatisticsVO(mapper.users(), mapper.posts(), mapper.postsBetween(start, start.plusDays(1)),
                mapper.postsBetween(start.minusDays(1), start), LocalDateTime.now(clock.withZone(BUSINESS_ZONE)));
        try { if (saved != null) saved.saveOverview(result); } catch (RuntimeException unavailable) { /* 返回真实 SQL 结果。 */ }
        return result;
        } finally { if (lock != null && token != null) try { lock.release("lock:home:overview", token); } catch (RuntimeException ignored) {} }
    }

    /** 热门标签查询先限制数量，再从 Mapper 的 JOIN/GROUP BY 结果返回排名。 */
    @Transactional(readOnly = true)
    @Override
    public List<HotTagVO> hotTags(int limit) {
        if (limit < 1 || limit > 50) throw new BusinessException("LIMIT_INVALID", "limit 必须在1到50之间", HttpStatus.BAD_REQUEST);
        HomeCache saved = cache.getIfAvailable();
        try {
            if (saved != null) {
                var fresh = saved.hotTags(limit);
                if (fresh.isPresent()) return fresh.orElseThrow();
            }
        }
        catch (RuntimeException unavailable) { /* Redis 停机时回源。 */ }
        List<HotTagVO> result = mapper.hotTags(limit);
        try { if (saved != null) saved.saveHotTags(limit, result); } catch (RuntimeException unavailable) { /* 保留真实结果。 */ }
        return result;
    }

}
