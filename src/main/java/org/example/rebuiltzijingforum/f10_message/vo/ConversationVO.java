package org.example.rebuiltzijingforum.f10_message.vo;

import java.time.LocalDateTime;

/** 会话列表卡片；unreadCount 属于当前登录用户而不是整个会话。 */
public record ConversationVO(long id, long peerId, String peerAccount, int unreadCount,
                             Long lastMessageId, String lastContent, LocalDateTime lastMessageAt) {}
