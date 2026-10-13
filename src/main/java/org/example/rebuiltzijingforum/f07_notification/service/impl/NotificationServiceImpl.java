package org.example.rebuiltzijingforum.f07_notification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f07_notification.mapper.NotificationMapper;
import org.example.rebuiltzijingforum.f07_notification.mapper.NotificationOutboxMapper;
import org.example.rebuiltzijingforum.f07_notification.dto.NotificationEventDTO;
import org.example.rebuiltzijingforum.f07_notification.service.NotificationService;
import org.example.rebuiltzijingforum.f07_notification.dto.NotificationPageDTO;
import org.example.rebuiltzijingforum.f07_notification.entity.NotificationEntity;
import org.example.rebuiltzijingforum.f07_notification.vo.NotificationVO;
import org.example.rebuiltzijingforum.f07_notification.vo.UnreadCountVO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;

/** F07 MySQL 第一版：其他 Service 调用 emit，用户请求调用 unread/list/read。 */
@Service
public class NotificationServiceImpl implements NotificationService {
    private final NotificationMapper mapper;
    private final Clock clock;
    private final NotificationOutboxMapper outbox;
    private final ObjectMapper json;
    private final boolean rabbitEnabled;
    private final ObjectProvider<NotificationUnreadCache> unreadCache;

    /** Spring 注入数据库层和时间源。 */
    public NotificationServiceImpl(NotificationMapper mapper, Clock clock, NotificationOutboxMapper outbox,
                                   ObjectMapper json, @Value("${forum.rabbit.enabled:false}") boolean rabbitEnabled,
                                   ObjectProvider<NotificationUnreadCache> unreadCache) {
        this.mapper = mapper; this.clock = clock; this.outbox = outbox; this.json = json; this.rabbitEnabled = rabbitEnabled;
        this.unreadCache = unreadCache;
    }

    /** 业务动作调用：过滤自己通知；默认直接 INSERT，Rabbit 版改为同事务写 Outbox。 */
    @Transactional
    @Override
    public void emit(long recipient, long actor, String kind, Long target, String eventKey, String content) {
        if (recipient == actor) return;
        if (rabbitEnabled) {
            if (outbox.hasEvent(eventKey)) return;
            try {
                outbox.insert(eventKey, json.writeValueAsString(new NotificationEventDTO(recipient, actor, kind, target, eventKey, content)), LocalDateTime.now(clock));
            } catch (JsonProcessingException invalid) {
                throw new IllegalStateException("通知事件序列化失败", invalid);
            } catch (DataIntegrityViolationException duplicate) {
                if (!outbox.hasEvent(eventKey)) throw duplicate;
            }
            return;
        }
        persistEvent(new NotificationEventDTO(recipient, actor, kind, target, eventKey, content));
    }

    /** MQ 消费者和同步版共享这一个落库函数；eventKey 唯一约束保证重复投递不生成多条。 */
    @Transactional
    @Override
    public void persistEvent(NotificationEventDTO event) {
        if (event.recipientId() == event.actorId() || mapper.hasEvent(event.eventKey())) return;
        try {
            mapper.insert(event.recipientId(), event.actorId(), event.kind(), event.targetId(), event.eventKey(), event.content(), LocalDateTime.now(clock));
            evictUnread(event.recipientId());
        } catch (DataIntegrityViolationException duplicate) {
            if (!mapper.hasEvent(event.eventKey())) throw duplicate;
        }
    }

    /** 顶部红点调用；Mapper 的 WHERE user_id=? 将不同用户隔离。 */
    @Transactional(readOnly = true)
    @Override
    public UnreadCountVO unread(long userId) {
        NotificationUnreadCache cache = unreadCache.getIfAvailable();
        if (cache != null) try {
            var saved = cache.get(userId);
            if (saved.isPresent()) return new UnreadCountVO(saved.orElseThrow());
        } catch (RuntimeException unavailable) { /* Redis 出错就回源数据库。 */ }
        long count = mapper.unread(userId);
        if (cache != null) try { cache.put(userId, count); }
        catch (RuntimeException unavailable) { /* 不能因为缓存不可用影响真实未读数。 */ }
        return new UnreadCountVO(count);
    }

    /** 消息页调用；验证页码，再按 userId 和可选类型读取一页。 */
    @Transactional(readOnly = true)
    @Override
    public PageResponse<NotificationVO> list(long userId, NotificationPageDTO query) {
        if (query.page() < 1 || query.size() < 1 || query.size() > 100) throw new BusinessException("PAGE_INVALID", "分页参数错误", HttpStatus.BAD_REQUEST);
        return new PageResponse<>(mapper.list(userId, query.kind(), query.size(), (query.page() - 1) * query.size())
                .stream().map(this::toVo).toList(), mapper.count(userId, query.kind()), query.page(), query.size());
    }

    /** 点击通知时调用；受影响行数为 0 表示这条 id 不属于当前用户。 */
    @Transactional
    @Override
    public void read(long userId, long id) {
        if (mapper.markRead(id, userId) == 0) throw new BusinessException("NOTIFICATION_NOT_FOUND", "通知不存在", HttpStatus.NOT_FOUND);
        evictUnread(userId);
    }

    /** 一键已读；数据库只修改 WHERE user_id=当前用户 的行。 */
    @Transactional
    @Override
    public int readAll(long userId) {
        int changed = mapper.readAll(userId);
        evictUnread(userId);
        return changed;
    }

    /** 提交后才删 Redis：避免别的请求在事务提交前读旧 DB、重新填回旧未读数。 */
    private void evictUnread(long userId) {
        NotificationUnreadCache cache = unreadCache.getIfAvailable();
        if (cache == null) return;
        Runnable remove = () -> {
            try { cache.evict(userId); }
            catch (RuntimeException unavailable) { /* MySQL 结果保持有效，TTL 最终会过期。 */ }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { remove.run(); }
            });
        } else remove.run();
    }

    /** Entity 只在数据层流转；给浏览器前去掉接收人 id 和事件去重细节。 */
    private NotificationVO toVo(NotificationEntity row) {
        return new NotificationVO(row.id(), row.actorId(), row.kind(), row.targetId(), row.content(), row.read(), row.createdAt());
    }
}
