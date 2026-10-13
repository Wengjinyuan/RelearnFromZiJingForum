package org.example.rebuiltzijingforum.f07_notification.dto;

/** 通知领域事件；eventKey 是重试去重键，不能每次投递重新随机生成。 */
public record NotificationEventDTO(long recipientId, long actorId, String kind, Long targetId,
                                   String eventKey, String content) {}
