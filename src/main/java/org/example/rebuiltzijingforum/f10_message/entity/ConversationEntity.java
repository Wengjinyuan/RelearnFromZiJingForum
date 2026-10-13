package org.example.rebuiltzijingforum.f10_message.entity;

import java.time.LocalDateTime;

/** conversation 表的一行；pairKey 排序两个 userId，使 A-B 与 B-A 相同。 */
public record ConversationEntity(long id, String pairKey, Long lastMessageId,
                                 LocalDateTime lastMessageAt, LocalDateTime createdAt) {}
