package org.example.rebuiltzijingforum.learninglangchian;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 学习实验的模型配置。默认不创建模型，避免缺少密钥影响论坛启动。 */
@Configuration
@ConditionalOnProperty(prefix = "forum.learning-ai", name = "enabled", havingValue = "true")
public class LearningAiConfiguration {
    @Bean
    @ConditionalOnMissingBean(ChatModel.class)
    public ChatModel learningChatModel(
            @Value("${forum.learning-ai.base-url}") String baseUrl,
            @Value("${forum.learning-ai.api-key}") String apiKey,
            @Value("${forum.learning-ai.model}") String modelName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "forum.learning-ai.api-key must be configured when forum.learning-ai.enabled=true");
        }
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey.trim())
                .modelName(modelName)
                .build();
    }
}
