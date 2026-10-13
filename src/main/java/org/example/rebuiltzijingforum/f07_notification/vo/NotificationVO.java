package org.example.rebuiltzijingforum.f07_notification.vo;

import java.time.LocalDateTime;

/** 浏览器消息列表的一项；不会暴露 eventKey 之类内部去重字段。 */
public record NotificationVO(long id, long actorId, String kind, Long targetId,
                             String content, boolean read, LocalDateTime createdAt) {}
