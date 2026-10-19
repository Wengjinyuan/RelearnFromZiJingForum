package org.example.rebuiltzijingforum.f08_search.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f08_search.config.AiSearchProperties;
import org.example.rebuiltzijingforum.f08_search.service.AiChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** DeepSeek 的 OpenAI 兼容 HTTP 适配器。只负责组请求、解析响应和稳定化异常。 */
@Component
public class DeepSeekAiChatClient implements AiChatClient {
    private final ObjectMapper json;
    private final AiSearchProperties properties;
    private final HttpClient http;

    @Autowired
    public DeepSeekAiChatClient(ObjectMapper json, AiSearchProperties properties) {
        this(json, properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build());
    }

    public DeepSeekAiChatClient(ObjectMapper json, AiSearchProperties properties, HttpClient http) {
        this.json = json;
        this.properties = properties;
        this.http = http;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, int maxTokens) {
        try {
            String body = json.writeValueAsString(Map.of(
                    "model", properties.getModel(),
                    "temperature", 0.1,
                    "max_tokens", maxTokens,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt))));
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getUrl()))
                    .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw unavailable();
            String content = json.readTree(response.body()).path("choices").path(0)
                    .path("message").path("content").asText("").trim();
            if (content.isBlank()) throw unavailable();
            return content;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (BusinessException stable) {
            throw stable;
        } catch (Exception failure) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException("AI_SEARCH_UNAVAILABLE",
                "AI 搜索暂时不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
