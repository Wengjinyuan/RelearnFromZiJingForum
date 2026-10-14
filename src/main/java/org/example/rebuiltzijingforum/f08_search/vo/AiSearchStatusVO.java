package org.example.rebuiltzijingforum.f08_search.vo;

/** 前端据此决定是否启用 AI 搜索按钮，不返回 API Key 等敏感配置。 */
public record AiSearchStatusVO(boolean available, String provider, String model) {}
