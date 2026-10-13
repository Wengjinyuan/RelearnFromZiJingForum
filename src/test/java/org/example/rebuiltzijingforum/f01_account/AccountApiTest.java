package org.example.rebuiltzijingforum.f01_account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.rebuiltzijingforum.f01_account.entity.UserAccountEntity;
import org.example.rebuiltzijingforum.f01_account.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * account 的接口级集成测试。
 *
 * <p>MockMvc 会真实经过 Controller、Service、拦截器、MyBatis 和 H2，
 * 因而能验证完整调用链，而不仅是单个方法。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
// 每个测试前清空账号表，避免测试之间互相依赖。
@Sql(statements = "DELETE FROM user_account", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AccountApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountMapper mapper;

    /**
     * 覆盖主路径：注册 -> 密码哈希 -> 登录 -> 当前用户 -> 改密 -> 旧 Token 失效。
     */
    @Test
    void completeAccountLifecycleAndRevokeOldTokenAfterPasswordReset() throws Exception {
        register("alice_01", "Password123", "alice@example.com", "13800138000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.account").value("alice_01"))
                .andExpect(jsonPath("$.data.email").value("a***@example.com"))
                .andExpect(jsonPath("$.data.mobile").value("138****8000"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        // 直接读取数据库，证明保存的是 BCrypt 哈希而不是原密码。
        UserAccountEntity stored = mapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getAccount, "alice_01"));
        assertThat(stored.getPasswordHash()).isNotEqualTo("Password123").startsWith("$2");

        String oldToken = login("alice_01", "Password123");
        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account").value("alice_01"));

        MvcResult resetResult = mockMvc.perform(post("/api/accounts/reset-password")
                        .header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Password123","newPassword":"NewPassword456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String newToken = readToken(resetResult);

        // 修改密码后，旧 Token 的 ver 小于数据库 token_version，因此必须返回 401。
        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));

        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/accounts/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"alice_01","password":"Password123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        login("alice_01", "NewPassword456");
    }

    /** 唯一数据重复时应返回 409 Conflict，而不是 500 或伪装成成功。 */
    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        register("bob_0001", "Password123", "bob@example.com", null)
                .andExpect(status().isCreated());

        register("bob_0001", "Password123", "other@example.com", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_CONFLICT"));
    }

    /** 验证输入错误使用 400，缺少 Token 使用 401。 */
    @Test
    void validationAndAuthenticationFailuresUseCorrectStatusCodes() throws Exception {
        mockMvc.perform(post("/api/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"x","password":"123","email":"bad-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /** 测试辅助方法：构造注册请求并返回 ResultActions，便于继续追加断言。 */
    private org.springframework.test.web.servlet.ResultActions register(
            String account, String password, String email, String mobile) throws Exception {
        String mobileJson = mobile == null ? "null" : "\"" + mobile + "\"";
        String body = """
                {"account":"%s","password":"%s","email":"%s","mobile":%s}
                """.formatted(account, password, email, mobileJson);
        return mockMvc.perform(post("/api/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 测试辅助方法：执行登录、断言成功并提取 Token。 */
    private String login(String account, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/accounts/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"%s","password":"%s"}
                                """.formatted(account, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();
        return readToken(result);
    }

    /** 从统一响应的 data.accessToken 字段读取 JWT。 */
    private String readToken(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return json.path("data").path("accessToken").asText();
    }
}
