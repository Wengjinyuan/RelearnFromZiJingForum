package org.example.rebuiltzijingforum.f07_notification.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** 可丢弃的未读数缓存：key 必须包含 userId；MySQL notification 始终是事实源。 */
@Component
@ConditionalOnProperty(name = "forum.redis.enabled", havingValue = "true")
public class NotificationUnreadCache {
    private final StringRedisTemplate redis;

    /** 仅启用 Redis 开关时创建；默认本地 H2 测试完全不连接 Redis。 */
    public NotificationUnreadCache(StringRedisTemplate redis) { this.redis = redis; }

    /** 缓存缺失返回 empty，Service 必须回 MySQL COUNT，不能当成 0。 */
    public Optional<Long> get(long userId) {
        String value = redis.opsForValue().get(key(userId));
        return value == null ? Optional.empty() : Optional.of(Long.parseLong(value));
    }

    /** 把数据库 COUNT 快照缓存 60 秒；短 TTL 限制并发失效竞态的持续时间。 */
    public void put(long userId, long count) {
        redis.opsForValue().set(key(userId), Long.toString(count), Duration.ofSeconds(60));
    }

    /** 新通知或已读操作后删除当前用户的旧快照，下次读取从数据库重建。 */
    public void evict(long userId) { redis.delete(key(userId)); }

    /** userId 放进 key，防止 A 的未读数被 B 读到。 */
    private String key(long userId) { return "notification:unread:" + userId; }
}
