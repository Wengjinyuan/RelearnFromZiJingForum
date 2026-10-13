package org.example.rebuiltzijingforum.f10_message.config;

import org.example.rebuiltzijingforum.f10_message.vo.MessageVO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/** F10 实时通道配置：第一版用 SSE 单向推送；历史事实仍在 private_message 表。 */
@Configuration
public class WebSocketConfig {
    /** 提供连接注册中心；重启或断线后客户端仍须按游标补拉数据库历史。 */
    @Bean
    public MessageSseHub messageSseHub() { return new MessageSseHub(); }

    /** 每个登录用户可有多个浏览器标签页，每页对应一个 SseEmitter。 */
    public static final class MessageSseHub {
        private final ConcurrentHashMap<Long, Set<SseEmitter>> clients = new ConcurrentHashMap<>();

        /** Controller 建 SSE 长连接时调用；断开/超时自动从内存注册表移除。 */
        public SseEmitter subscribe(long userId) {
            SseEmitter emitter = new SseEmitter(30L * 60 * 1000);
            clients.computeIfAbsent(userId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
            emitter.onCompletion(() -> remove(userId, emitter));
            emitter.onTimeout(() -> remove(userId, emitter));
            emitter.onError(ignored -> remove(userId, emitter));
            return emitter;
        }

        /** Service 在事务提交后调用；只向收件人连接发送，不负责保存消息。 */
        public void deliver(long recipientId, MessageVO message) {
            for (SseEmitter emitter : clients.getOrDefault(recipientId, Set.of())) {
                try { emitter.send(SseEmitter.event().name("message").id(String.valueOf(message.id())).data(message)); }
                catch (IOException | IllegalStateException failure) { remove(recipientId, emitter); }
            }
        }

        /** 清理关闭的连接，避免用户每次刷新都留下内存对象。 */
        private void remove(long userId, SseEmitter emitter) {
            Set<SseEmitter> connections = clients.get(userId);
            if (connections != null) {
                connections.remove(emitter);
                if (connections.isEmpty()) clients.remove(userId, connections);
            }
        }
    }
}
