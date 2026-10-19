package org.example.rebuiltzijingforum.f08_search.service;

/** AI 供应商边界；论坛检索编排不依赖具体 HTTP SDK。 */
public interface AiChatClient {
    String complete(String systemPrompt, String userPrompt, int maxTokens);
}
