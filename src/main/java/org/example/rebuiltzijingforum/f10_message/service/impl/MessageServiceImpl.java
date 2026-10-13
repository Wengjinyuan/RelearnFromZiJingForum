package org.example.rebuiltzijingforum.f10_message.service.impl;

import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f10_message.mapper.ConversationMapper;
import org.example.rebuiltzijingforum.f10_message.mapper.ConversationMemberMapper;
import org.example.rebuiltzijingforum.f10_message.mapper.PrivateMessageMapper;
import org.example.rebuiltzijingforum.f10_message.entity.PrivateMessageEntity;
import org.example.rebuiltzijingforum.f10_message.dto.SendMessageDTO;
import org.example.rebuiltzijingforum.f10_message.dto.MessagePageDTO;
import org.example.rebuiltzijingforum.f10_message.vo.MessageVO;
import org.example.rebuiltzijingforum.f10_message.vo.ConversationVO;
import org.example.rebuiltzijingforum.f10_message.service.MessageService;
import org.example.rebuiltzijingforum.f10_message.config.WebSocketConfig.MessageSseHub;
import org.example.rebuiltzijingforum.f07_notification.service.NotificationService;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** F10 业务层：Controller 传可信 senderId，本类验证并组织多次 SQL 为一个事务。 */
@Service
public class MessageServiceImpl implements MessageService {
    private final ConversationMapper conversations;
    private final ConversationMemberMapper members;
    private final PrivateMessageMapper messages;
    private final Clock clock;
    private final NotificationService notifications;
    private final MessageSseHub live;

    /** Spring 注入 Mapper 与时间源。 */
    public MessageServiceImpl(ConversationMapper conversations, ConversationMemberMapper members,
                              PrivateMessageMapper messages, Clock clock, NotificationService notifications, MessageSseHub live) {
        this.conversations = conversations; this.members = members; this.messages = messages;
        this.clock = clock; this.notifications = notifications; this.live = live;
    }

    /** 发送：校验两人和内容 → 找/建会话 → INSERT 消息 → 更新会话与收件人未读。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public MessageVO send(long sender, long recipient, SendMessageDTO input) {
        String clientId = input.clientMessageId(), type = input.type(), raw = input.content();
        if (sender == recipient) throw bad("MESSAGE_SELF", "不能给自己发私信");
        if (!conversations.activeUser(recipient)) throw new BusinessException("USER_NOT_FOUND", "收件人不存在", HttpStatus.NOT_FOUND);
        if (clientId == null || clientId.isBlank() || clientId.length() > 80) throw bad("CLIENT_ID_INVALID", "clientMessageId 须为1到80字");
        if (!"TEXT".equals(type)) throw bad("MESSAGE_TYPE_INVALID", "当前教学版只支持 TEXT 私信");
        String content = Jsoup.clean(raw == null ? "" : raw, Safelist.none()).trim();
        if (content.isEmpty() || content.length() > 4000) throw bad("MESSAGE_INVALID", "内容须为1到4000字");
        String key = Math.min(sender, recipient) + ":" + Math.max(sender, recipient);
        PrivateMessageEntity previous = messages.findByClientId(sender, clientId).orElse(null);
        if (previous != null) {
            Long expected = conversations.findByPair(key).map(row -> row.id()).orElse(null);
            return checkedPrevious(previous, expected, content, type);
        }
        Long conversationId = conversations.findByPair(key).map(row -> row.id()).orElse(null);
        if (conversationId == null) {
            boolean created = false;
            try {
                conversationId = conversations.insert(key, LocalDateTime.now(clock));
                created = true;
            } catch (DataIntegrityViolationException concurrentCreate) {
                // A/B 同时发第一条消息：pair_key 唯一键只允许一人建会话；另一人复用已提交的会话。
                conversationId = conversations.findByPair(key).map(row -> row.id()).orElseThrow(() -> concurrentCreate);
            }
            if (created) {
                // 成员 INSERT 失败不能伪装成“重复创建会话”；事务必须整体回滚。
                members.insert(conversationId, sender);
                members.insert(conversationId, recipient);
            }
        }
        LocalDateTime now = LocalDateTime.now(clock);
        long id;
        try {
            id = messages.insert(conversationId, sender, clientId, type, content, now);
        } catch (DataIntegrityViolationException duplicateSend) {
            // 同一 clientMessageId 的并发重试：唯一约束挡住重复，落后请求读回旧消息且不再加未读。
            PrivateMessageEntity saved = messages.findByClientId(sender, clientId).orElseThrow(() -> duplicateSend);
            return checkedPrevious(saved, conversationId, content, type);
        }
        conversations.touch(conversationId, id, now);
        members.incrementUnread(conversationId, recipient);
        // F07：私信的红点与会话未读各有用途，事件键保证重试不重复通知。
        notifications.emit(recipient, sender, "MESSAGE", id, "MESSAGE:" + sender + ":" + clientId, "你收到一条新私信");
        MessageVO output = new MessageVO(id, conversationId, sender, type, content, now);
        // 数据库事务提交后再推实时事件；回滚时不能让前端看到一条不存在的消息。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { live.deliver(recipient, output); }
            });
        }
        return output;
    }

    /** 左侧列表：Mapper 只查询当前用户参加的会话。 */
    @Transactional(readOnly = true)
    @Override
    public List<ConversationVO> conversations(long userId) { return conversations.listFor(userId); }

    /** 聊天记录：先检查会话成员资格，再读游标页，避免越权。 */
    @Transactional(readOnly = true)
    @Override
    public List<MessageVO> history(long userId, long conversationId, MessagePageDTO query) {
        requireMember(userId, conversationId);
        if (query.size() < 1 || query.size() > 100 || query.beforeId() < 1) throw bad("PAGE_INVALID", "游标或每页大小错误");
        return messages.history(conversationId, query.beforeId(), query.size()).stream().map(this::toVo).toList();
    }

    /** 已读：先验成员，后 UPDATE 自己的 member 行。 */
    @Transactional
    @Override
    public void read(long userId, long conversationId) { requireMember(userId, conversationId); members.markRead(conversationId, userId); }

    /** F10 的资源所有权校验点，不在 Controller 相信 URL 中的会话 ID。 */
    private void requireMember(long userId, long conversationId) {
        if (members.find(conversationId, userId).isEmpty()) throw new BusinessException("CONVERSATION_NOT_FOUND", "会话不存在", HttpStatus.NOT_FOUND);
    }

    /** Entity → VO：clientMessageId 是幂等内部字段，不需要在消息列表给其他用户。 */
    private MessageVO toVo(PrivateMessageEntity row) {
        return new MessageVO(row.id(), row.conversationId(), row.senderId(), row.type(), row.content(), row.createdAt());
    }

    /** 重试只能复用“同一会话、同一正文、同一类型”的请求 ID，不能拿旧 ID 发新内容。 */
    private MessageVO checkedPrevious(PrivateMessageEntity saved, Long conversationId, String content, String type) {
        if (conversationId == null || saved.conversationId() != conversationId)
            throw bad("CLIENT_ID_REUSED", "请求 ID 已用于另一会话");
        if (!saved.content().equals(content) || !saved.type().equals(type))
            throw bad("CLIENT_ID_REUSED", "同一请求 ID 不能用于不同内容");
        return toVo(saved);
    }

    /** 统一包装参数错误，交给全局异常处理器返回 JSON。 */
    private BusinessException bad(String code, String message) { return new BusinessException(code, message, HttpStatus.BAD_REQUEST); }
}
