package org.example.rebuiltzijingforum.f10_message.entity;

/** conversation_member 表的一行；会话成员资格是历史查询权限的事实来源。 */
public record ConversationMemberEntity(long conversationId, long userId, int unreadCount, Long lastReadMessageId) {}
