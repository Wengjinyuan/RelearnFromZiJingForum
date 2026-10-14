package org.example.rebuiltzijingforum;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(statements = {"DELETE FROM password_reset_request", "DELETE FROM admin_audit_log", "DELETE FROM user_profile", "DELETE FROM user_account"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AccountRecoveryApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    @Test
    void adminApprovesResetAndChangesRoleWhileOldTokenExpires() throws Exception {
        long admin = register("recovery_admin", "recovery-admin@example.test");
        long member = register("recovery_user", "recovery-user@example.test");
        db.update("UPDATE user_account SET role='SUPER_ADMIN' WHERE id=?", admin);
        String adminToken = login("recovery_admin", "Password123");
        String oldMemberToken = login("recovery-user@example.test", "Password123");

        mvc.perform(post("/api/accounts/password-reset-requests").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"recovery_user\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/password-reset-requests").header("Authorization", bearer(oldMemberToken)))
                .andExpect(status().isForbidden());
        MvcResult pending = mvc.perform(get("/api/admin/password-reset-requests")
                .header("Authorization", bearer(adminToken))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].account").value("recovery_user")).andReturn();
        long requestId = json.readTree(pending.getResponse().getContentAsByteArray()).path("data").get(0).path("id").asLong();
        mvc.perform(put("/api/admin/password-reset-requests/{id}/approve", requestId)
                .header("Authorization", bearer(adminToken))).andExpect(status().isOk());
        mvc.perform(get("/api/accounts/me").header("Authorization", bearer(oldMemberToken)))
                .andExpect(status().isUnauthorized());
        String resetToken = login("recovery_user", "123456");
        mvc.perform(put("/api/accounts/me/contact").header("Authorization", bearer(resetToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new-contact@example.test\",\"mobile\":\"13800138000\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value("new-contact@example.test"));
        login("new-contact@example.test", "123456");
        mvc.perform(put("/api/admin/users/{id}/role", member).header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("ADMIN"));
        mvc.perform(get("/api/accounts/me").header("Authorization", bearer(resetToken)))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/users/{id}/role", admin).header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());

        String promotedAdminToken = login("recovery_user", "123456");
        mvc.perform(put("/api/admin/users/{id}/role", admin).header("Authorization", bearer(promotedAdminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SUPER_ADMIN_FORBIDDEN"));
    }

    @Test
    void localAvatarAndPostImageCanBeReadBack() throws Exception {
        long userId = register("image_user", "image-user@example.test");
        String token = login("image_user", "Password123");
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/JxQAAAAASUVORK5CYII=");
        String avatarUrl = null, postImageUrl = null;
        try {
            MvcResult avatar = mvc.perform(multipart("/api/profiles/me/avatar")
                    .file(new MockMultipartFile("file", "local.png", "image/png", png))
                    .header("Authorization", bearer(token)))
                    .andExpect(status().isCreated()).andReturn();
            avatarUrl = json.readTree(avatar.getResponse().getContentAsByteArray()).path("data").asText();
            mvc.perform(get(avatarUrl)).andExpect(status().isOk());

            MvcResult upload = mvc.perform(multipart("/api/posts/images")
                    .file(new MockMultipartFile("file", "local.png", "image/png", png))
                    .header("Authorization", bearer(token)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.errno").value(0)).andReturn();
            postImageUrl = json.readTree(upload.getResponse().getContentAsByteArray()).path("data").path("url").asText();
            mvc.perform(get(postImageUrl)).andExpect(status().isOk());

            String postBody = "{\"category\":\"学习交流\",\"title\":\"本地图片测试\",\"content\":\"<p>这是本地图片正文</p><img src='"
                    + postImageUrl + "'>\",\"tags\":[]}";
            MvcResult created = mvc.perform(post("/api/posts").header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content(postBody))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.coverUrl").value(postImageUrl)).andReturn();
            long postId = json.readTree(created.getResponse().getContentAsByteArray()).path("data").path("id").asLong();
            mvc.perform(post("/api/posts/{id}/comments", postId).header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"本地图片评论\",\"imageUrls\":[\"" + postImageUrl + "\"]}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.imageUrls[0]").value(postImageUrl));
        } finally {
            if (avatarUrl != null) Files.deleteIfExists(Paths.get("." + avatarUrl));
            if (postImageUrl != null) Files.deleteIfExists(Paths.get("." + postImageUrl));
        }
    }

    private long register(String account, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/accounts/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + account + "\",\"password\":\"Password123\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("id").asLong();
    }

    private String login(String account, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/accounts/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("accessToken").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }
}
