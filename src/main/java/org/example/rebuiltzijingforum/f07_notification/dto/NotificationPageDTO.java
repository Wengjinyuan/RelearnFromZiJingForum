package org.example.rebuiltzijingforum.f07_notification.dto;

/** 消息页查询条件；kind 不填表示所有类别。 */
public record NotificationPageDTO(String kind, int page, int size) {}
