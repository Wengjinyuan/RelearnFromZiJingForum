package org.example.rebuiltzijingforum.f10_message.entity;

import java.time.LocalDateTime;

/** private_message 表的一行；clientMessageId 保证同一次发送重试不会插两条。 */
public record PrivateMessageEntity(long id, long conversationId, long senderId,
                                   String clientMessageId, String type, String content, LocalDateTime createdAt) {}
