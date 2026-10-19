package org.example.rebuiltzijingforum.learninglangchian;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "forum.learning-ai.enabled=true",
        "forum.learning-ai.api-key=test-key"
})
@AutoConfigureMockMvc
class LearningAiEnabledTest {
    @Autowired MockMvc mvc;
    @MockBean ChatModel model;

    @Test
    void pageRouteAndPostJsonChatAreAvailableWhenEnabled() throws Exception {
        when(model.chat("什么是 LangChain4j？")).thenReturn("这是一个 Java AI 应用开发框架。");

        mvc.perform(get("/ai/chat"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/langchian/index.html"));
        mvc.perform(post("/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"什么是 LangChain4j？\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("这是一个 Java AI 应用开发框架。"));
    }

    @Test
    void getQuestionEndpointIsNotAllowed() throws Exception {
        mvc.perform(get("/ai/ask").queryParam("question", "secret"))
                .andExpect(status().isMethodNotAllowed());
    }
}
