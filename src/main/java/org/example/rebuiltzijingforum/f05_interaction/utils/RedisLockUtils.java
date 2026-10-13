package org.example.rebuiltzijingforum.f05_interaction.utils;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/** Redis 进阶版可复用的安全锁：唯一 token + 原子比较删除；MySQL 基础版不依赖它。 */
@Component
@ConditionalOnProperty(name = "forum.redis.enabled", havingValue = "true")
public class RedisLockUtils {
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redis;

    /** 开启 forum.redis.enabled 后由 Spring 注入 Redis 连接；未开启时没有此 Bean。 */
    public RedisLockUtils(StringRedisTemplate redis) { this.redis = redis; }

    /** 尝试 SET key token NX PX ttl；拿不到锁返回 null，调用者不得释放。 */
    public String tryLock(String key, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("锁 TTL 必须为正数");
        String token = UUID.randomUUID().toString();
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, ttl)) ? token : null;
    }

    /** Lua 在 Redis 端原子比较 token 并 DEL，避免误删别人后来取得的锁。 */
    public boolean release(String key, String token) {
        if (token == null) return false;
        Long changed = redis.execute(RELEASE, Collections.singletonList(key), token);
        return Long.valueOf(1).equals(changed);
    }
}
