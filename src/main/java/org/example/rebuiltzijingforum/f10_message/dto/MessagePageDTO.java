package org.example.rebuiltzijingforum.f10_message.dto;

/** 翻页输入：beforeId 是上页最早消息 id；不使用容易漂移的 OFFSET。 */
public record MessagePageDTO(long beforeId, int size) {}
