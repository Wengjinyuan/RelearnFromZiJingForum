package org.example.rebuiltzijingforum.learninglangchian;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.jdbc.Sql;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "forum.learning-ai.enabled=true",
        "forum.learning-ai.api-key=test-key"
})
@AutoConfigureMockMvc
@Sql(statements = {"DELETE FROM user_profile", "DELETE FROM user_account"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class LearningAiEnabledTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
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
                .andExpect(status().isUnauthorized());

        String token = registerAndLogin("learning_user");
        mvc.perform(post("/ai/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"什么是 LangChain4j？\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("这是一个 Java AI 应用开发框架。"));
    }

    @Test
    void getQuestionEndpointIsNotAllowed() throws Exception {
        String token = registerAndLogin("learning_get_user");
        mvc.perform(get("/ai/ask").queryParam("question", "secret")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed());
    }

    private String registerAndLogin(String account) throws Exception {
        mvc.perform(post("/api/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + account
                                + "\",\"password\":\"Password123\",\"email\":\"" + account + "@example.test\"}"))
                .andExpect(status().isCreated());
        MvcResult result = mvc.perform(post("/api/accounts/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + account + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray())
                .path("data").path("accessToken").asText();
    }
}
