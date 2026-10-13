package org.example.rebuiltzijingforum.f07_notification.entity;

import java.time.LocalDateTime;

/** notification 表的一行；userId 是接收人，actorId 是触发动作的人。 */
public record NotificationEntity(long id, long userId, long actorId, String kind, Long targetId,
                                 String content, boolean read, LocalDateTime createdAt) {}
