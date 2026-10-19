package org.example.rebuiltzijingforum.f08_search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f08_search.config.AiSearchProperties;
import org.example.rebuiltzijingforum.f08_search.service.impl.DeepSeekAiChatClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekAiChatClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsOpenAiCompatibleRequestAndExtractsAnswer() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        start(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\" 站内答案 \"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        String answer = client().complete("只根据站内内容", "近期有什么活动", 321);

        assertThat(answer).isEqualTo("站内答案");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        JsonNode sent = json.readTree(requestBody.get());
        assertThat(sent.path("model").asText()).isEqualTo("deepseek-chat");
        assertThat(sent.path("max_tokens").asInt()).isEqualTo(321);
        assertThat(sent.path("messages").path(0).path("content").asText()).isEqualTo("只根据站内内容");
    }

    @Test
    void mapsProviderFailureToStableServiceError() throws Exception {
        start(exchange -> {
            byte[] response = "provider secret detail".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        assertThatThrownBy(() -> client().complete("system", "user", 100))
                .isInstanceOfSatisfying(BusinessException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo("AI_SEARCH_UNAVAILABLE");
                    assertThat(failure.getMessage()).isEqualTo("AI 搜索暂时不可用，请稍后重试");
                });
    }

    private DeepSeekAiChatClient client() {
        AiSearchProperties properties = new AiSearchProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setUrl("http://localhost:" + server.getAddress().getPort() + "/chat");
        properties.setModel("deepseek-chat");
        return new DeepSeekAiChatClient(json, properties,
                HttpClient.newBuilder().build());
    }

    private void start(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat", handler);
        server.start();
    }
}
