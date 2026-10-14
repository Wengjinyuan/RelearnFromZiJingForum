package org.example.rebuiltzijingforum.f08_search.dto;

/** AI 搜索只接收自然语言问题，用户身份来自拦截器。 */
public record AiSearchRequest(String question) {}
