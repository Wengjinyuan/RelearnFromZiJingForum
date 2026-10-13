package org.example.rebuiltzijingforum.f10_message.vo;

import java.time.LocalDateTime;

/** 网页显示的一条消息；谁发送、属于哪个会话及正文都来自服务器查询。 */
public record MessageVO(long id, long conversationId, long senderId, String type, String content, LocalDateTime createdAt) {}
