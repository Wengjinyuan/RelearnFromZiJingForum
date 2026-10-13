package org.example.rebuiltzijingforum.f08_search.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** ES HTTP 客户端配置；仅在开关打开时创建，默认 MySQL 搜索不依赖 ES。 */
@Configuration
@ConditionalOnProperty(name = "forum.search.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchConfig {
    /** 从配置读取 ES 地址/API Key；默认开关关闭，所以不会在本地启动时强制连接 ES。 */
    @Bean
    public ElasticsearchGateway elasticsearchGateway(ObjectMapper json,
            @Value("${forum.search.elasticsearch.url:http://localhost:9200}") String url,
            @Value("${forum.search.elasticsearch.api-key:}") String apiKey) {
        return new ElasticsearchGateway(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                URI.create(url), apiKey, json);
    }

    /** 只封装 transport：索引规则和数据库回查留在 SearchIndexServiceImpl。 */
    public static final class ElasticsearchGateway {
        private final HttpClient http;
        private final URI base;
        private final String apiKey;
        private final ObjectMapper json;

        /** 保存 HTTP 客户端、根地址、凭证和 JSON 编解码器。 */
        public ElasticsearchGateway(HttpClient http, URI base, String apiKey, ObjectMapper json) {
            this.http = http; this.base = base; this.apiKey = apiKey; this.json = json;
        }

        /** 把 Map/record 转 JSON 请求 ES；非 2xx 或网络错误抛异常让 SearchServiceImpl 回退 MySQL。 */
        public JsonNode request(String method, String path, Object body) {
            try {
                String payload = body == null ? "" : json.writeValueAsString(body);
                HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json");
                if (!apiKey.isBlank()) builder.header("Authorization", "ApiKey " + apiKey);
                builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload));
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) throw new IllegalStateException("ES HTTP " + response.statusCode() + ": " + response.body());
                return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
            } catch (IOException | InterruptedException failed) {
                if (failed instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new IllegalStateException("Elasticsearch 暂不可用", failed);
            }
        }

        /** HEAD 判断别名/文档是否存在；404 是 false，其它错误不能伪装成“不存在”。 */
        public boolean exists(String path) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(3))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody());
                if (!apiKey.isBlank()) builder.header("Authorization", "ApiKey " + apiKey);
                int code = http.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
                if (code == 404) return false;
                if (code >= 400) throw new IllegalStateException("ES HEAD HTTP " + code);
                return true;
            } catch (IOException | InterruptedException failed) {
                if (failed instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new IllegalStateException("Elasticsearch 暂不可用", failed);
            }
        }
    }
}
