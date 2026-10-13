package org.example.rebuiltzijingforum.f10_message.dto;

/** 浏览器发送私信的 JSON；senderId 必须从 JWT 而不是请求体取得。 */
public record SendMessageDTO(String clientMessageId, String type, String content) {}
