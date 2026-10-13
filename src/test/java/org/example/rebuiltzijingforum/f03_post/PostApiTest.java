package org.example.rebuiltzijingforum.f03_post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.rebuiltzijingforum.f03_post.entity.TagEntity;
import org.example.rebuiltzijingforum.f03_post.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** F03 接口级测试：真实经过鉴权、Controller、Service、MyBatis 和 H2。 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql(statements = {
        "DELETE FROM post_tag",
        "DELETE FROM tag",
        "DELETE FROM post",
        "DELETE FROM user_profile",
        "DELETE FROM user_account"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PostApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TagMapper tagMapper;

    @Test
    void ownerCanCreateReadUpdateAndSoftDeletePost() throws Exception {
        register("alice_post", "alice-post@example.com");
        String token = login("alice_post");

        MvcResult created = createPost(token, "原始标题",
                "<p onclick='evil()'>正文内容</p><script>alert(1)</script>"
                        + "<img src='https://example.com/cover.png' onerror='evil()'>",
                "[\"Java\",\"java\",\"#Spring\"]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("原始标题"))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("script"))))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("onerror"))))
                .andExpect(jsonPath("$.data.coverUrl").value("https://example.com/cover.png"))
                .andExpect(jsonPath("$.data.tags.length()").value(2))
                .andReturn();
        long postId = readDataId(created);

        mockMvc.perform(get("/api/posts?page=1&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(postId))
                .andExpect(jsonPath("$.data.items[0].summary").value("正文内容"))
                .andExpect(jsonPath("$.data.items[0].content").doesNotExist())
                .andReturn();

        mockMvc.perform(put("/api/posts/{id}", postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("更新后的标题", "<p>更新后的正文</p>", "[\"Spring\",\"MyBatis\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("更新后的标题"))
                .andExpect(jsonPath("$.data.tags[0]").value("spring"))
                .andExpect(jsonPath("$.data.tags[1]").value("mybatis"));

        TagEntity javaTag = tagMapper.selectOne(new LambdaQueryWrapper<TagEntity>()
                .eq(TagEntity::getName, "java"));
        assertThat(javaTag.getUseCount()).isZero();

        mockMvc.perform(delete("/api/posts/{id}", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    void anonymousWriteAndOtherUserUpdateAreRejected() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("无 Token", "<p>正文</p>", "[]")))
                .andExpect(status().isUnauthorized());

        register("owner_01", "owner@example.com");
        register("other_01", "other@example.com");
        String ownerToken = login("owner_01");
        String otherToken = login("other_01");
        long postId = readDataId(createPost(ownerToken, "作者帖子", "<p>原正文</p>", "[]")
                .andExpect(status().isCreated()).andReturn());

        mockMvc.perform(put("/api/posts/{id}", postId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("越权修改", "<p>不允许</p>", "[]")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POST_FORBIDDEN"));
    }

    private org.springframework.test.web.servlet.ResultActions createPost(
            String token, String title, String content, String tagsJson) throws Exception {
        return mockMvc.perform(post("/api/posts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(postBody(title, content, tagsJson)));
    }

    private String postBody(String title, String content, String tagsJson) throws Exception {
        return """
                {"category":"学习交流","title":%s,"content":%s,"tags":%s}
                """.formatted(objectMapper.writeValueAsString(title), objectMapper.writeValueAsString(content), tagsJson);
    }

    private void register(String account, String email) throws Exception {
        mockMvc.perform(post("/api/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"%s","password":"Password123","email":"%s"}
                                """.formatted(account, email)))
                .andExpect(status().isCreated());
    }

    private String login(String account) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/accounts/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"%s","password":"Password123"}
                                """.formatted(account)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("data").path("accessToken").asText();
    }

    private long readDataId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return root.path("data").path("id").asLong();
    }
}
