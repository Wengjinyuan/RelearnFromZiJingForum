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
import org.example.rebuiltzijingforum.f07_notification.mapper.NotificationMapper;
import org.example.rebuiltzijingforum.f07_notification.mapper.NotificationOutboxMapper;
import org.example.rebuiltzijingforum.f07_notification.service.impl.NotificationServiceImpl;
import org.example.rebuiltzijingforum.f07_notification.service.impl.NotificationUnreadCache;
import org.example.rebuiltzijingforum.f07_notification.dto.NotificationEventDTO;
import java.time.Clock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 走真实 HTTP→拦截器→Controller→Service→Mapper→H2 的跨功能回归。 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql(statements = {
        "DELETE FROM admin_audit_log", "DELETE FROM user_punishment", "DELETE FROM private_message",
        "DELETE FROM conversation_member", "DELETE FROM conversation", "DELETE FROM notification",
        "DELETE FROM notification_outbox",
        "DELETE FROM follow_relation", "DELETE FROM favorite", "DELETE FROM like_record", "DELETE FROM comment",
        "DELETE FROM post_tag", "DELETE FROM tag", "DELETE FROM post", "DELETE FROM user_profile", "DELETE FROM user_account"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FeatureFlowApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired NotificationMapper notificationMapper;
    @Autowired NotificationOutboxMapper outboxMapper;
    @Autowired Clock clock;
    @Autowired org.springframework.beans.factory.ObjectProvider<NotificationUnreadCache> unreadCache;

    /** 验证 F04-F09：匿名可读、写需登录、关系和通知真实落库。 */
    @Test
    void commentsInteractionsFollowSearchAndHome() throws Exception {
        long alice = register("flow_alice", "flow-alice@example.com");
        long bob = register("flow_bob", "flow-bob@example.com");
        String a = login("flow_alice"), b = login("flow_bob");
        long post = id(mvc.perform(post("/api/posts").header("Authorization", bearer(a)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"学习交流\",\"title\":\"Java 入门\",\"content\":\"<p>这里学 Java</p>\",\"tags\":[\"Java\"]}"))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/posts/{id}/comments", post).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"no token\"}"))
                .andExpect(status().isUnauthorized());
        long comment = id(mvc.perform(post("/api/posts/{id}/comments", post).header("Authorization", bearer(b))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"好帖\"}"))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(get("/api/posts/{id}/comments", post)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(put("/api/interactions/posts/{id}/like", post).header("Authorization", bearer(b)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/interactions/posts/{id}/like", post).header("Authorization", bearer(b)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/interactions/posts/{id}/state", post).header("Authorization", bearer(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.likes").value(1));
        mvc.perform(put("/api/interactions/posts/{id}/favorite", post).header("Authorization", bearer(b)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/interactions/posts/{id}/view", post)).andExpect(status().isOk());
        mvc.perform(put("/api/follows/{id}", alice).header("Authorization", bearer(b))).andExpect(status().isOk());
        mvc.perform(get("/api/follows/users/{id}/followers", alice)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].account").value("flow_bob"));
        mvc.perform(get("/api/follows/feed").header("Authorization", bearer(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/search/posts?keyword=Java")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].titleHighlight").value("<em>Java</em> 入门"));
        mvc.perform(get("/api/search/posts?keyword=Java&category=学习交流&sort=most_liked"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].category").value("学习交流"));
        mvc.perform(get("/api/search/posts?keyword=Java&category=社团活动"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/search/users?keyword=flow_alice")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/home")).andExpect(status().isOk()).andExpect(jsonPath("$.data.posts").value(1));
        mvc.perform(get("/api/home/hot-tags")).andExpect(status().isOk());
        mvc.perform(get("/api/posts?sort=active")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(post));
        mvc.perform(get("/api/posts?sort=hot")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(post));
        mvc.perform(get("/api/search/ai/status").header("Authorization", bearer(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(false));
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(4));
        mvc.perform(get("/api/notifications").header("Authorization", bearer(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(4));
        mvc.perform(put("/api/notifications/read-all").header("Authorization", bearer(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(4));
        mvc.perform(delete("/api/comments/{id}", comment).header("Authorization", bearer(a)))
                .andExpect(status().isForbidden());
    }

    /** F04 的 parentId 不是任意评论 ID：必须属于 URL 中的同一篇帖子。 */
    @Test
    void replyCannotCrossPosts() throws Exception {
        register("reply_owner", "reply-owner@example.com");
        String token = login("reply_owner");
        String postBody = "{\"category\":\"学习交流\",\"title\":\"帖子标题\",\"content\":\"帖子正文\",\"tags\":[]}";
        long first = id(mvc.perform(post("/api/posts").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(postBody)).andExpect(status().isCreated()).andReturn());
        long second = id(mvc.perform(post("/api/posts").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(postBody)).andExpect(status().isCreated()).andReturn());
        long comment = id(mvc.perform(post("/api/posts/{id}/comments", first).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"第一帖评论\"}"))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/posts/{id}/comments", second).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"跨帖回复\",\"parentId\":" + comment + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARENT_INVALID"));
        mvc.perform(get("/api/posts/{id}/comments", second)).andExpect(jsonPath("$.data.total").value(0));
    }

    /** F04 完整链路：图片校验/入库、根评论一次装配回复、软删除后整棵树不再返回。 */
    @Test
    void commentPreviewImagesAndSoftDelete() throws Exception {
        register("comment_owner", "comment-owner@example.com");
        register("comment_reader", "comment-reader@example.com");
        String owner = login("comment_owner"), reader = login("comment_reader");
        long postId = id(mvc.perform(post("/api/posts").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"学习交流\",\"title\":\"评论测试\",\"content\":\"正文\",\"tags\":[]}"))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/posts/{id}/comments", postId).header("Authorization", bearer(reader))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"错误图片\",\"imageUrls\":[\"javascript:alert(1)\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_INVALID"));
        long root = id(mvc.perform(post("/api/posts/{id}/comments", postId).header("Authorization", bearer(reader))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"原评论\",\"imageUrls\":[\"https://example.com/p.png\"]}"))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/posts/{id}/comments", postId).header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"回复\",\"parentId\":" + root + "}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/posts/{id}/comments", postId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].authorName").value("comment_reader"))
                .andExpect(jsonPath("$.data.items[0].replies[0].content").value("回复"))
                .andExpect(jsonPath("$.data.items[0].replies[0].replyUserName").value("comment_reader"))
                .andExpect(jsonPath("$.data.items[0].imageUrls[0]").value("https://example.com/p.png"));
        mvc.perform(delete("/api/comments/{id}", root).header("Authorization", bearer(reader)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/{id}/comments", postId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        org.junit.jupiter.api.Assertions.assertEquals(0L, db.queryForObject("SELECT comment_count FROM post WHERE id=?", Long.class, postId));
        org.junit.jupiter.api.Assertions.assertNotNull(db.queryForObject("SELECT deleted_at FROM comment WHERE id=?", java.time.LocalDateTime.class, root));
        org.junit.jupiter.api.Assertions.assertEquals(2L, db.queryForObject("SELECT COUNT(*) FROM comment WHERE (id=? OR root_id=?) AND deleted=TRUE", Long.class, root, root));
    }

    /** F11 到期禁言在发帖前即时解除，而且状态、处罚历史、审计三处一致。 */
    @Test
    void expiredMuteWritesAuditBeforePosting() throws Exception {
        long user = register("expired_writer", "expired-writer@example.com");
        String token = login("expired_writer");
        db.update("UPDATE user_account SET status='MUTED' WHERE id=?", user);
        db.update("INSERT INTO user_punishment(user_id,operator_id,action,reason,expires_at,created_at) VALUES(?,0,'MUTE','测试到期',?,?)",
                user, java.time.LocalDateTime.now().minusMinutes(2), java.time.LocalDateTime.now().minusDays(1));
        mvc.perform(post("/api/posts").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"学习交流\",\"title\":\"解除后发布\",\"content\":\"测试正文\",\"tags\":[]}"))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals("NORMAL", db.queryForObject("SELECT status FROM user_account WHERE id=?", String.class, user));
        org.junit.jupiter.api.Assertions.assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM user_punishment WHERE user_id=? AND action='EXPIRE_MUTE'", Long.class, user));
        org.junit.jupiter.api.Assertions.assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE target_id=? AND action='EXPIRE_MUTE'", Long.class, user));
    }

    /** 不依赖真实 MQ，验证可选异步版先写 Outbox，重复消费仍只插一条通知。 */
    @Test
    void notificationOutboxAndConsumerAreIdempotent() throws Exception {
        long recipient = register("outbox_receiver", "outbox-receiver@example.com");
        long actor = register("outbox_actor", "outbox-actor@example.com");
        NotificationServiceImpl async = new NotificationServiceImpl(notificationMapper, clock, outboxMapper, json, true, unreadCache);
        async.emit(recipient, actor, "FOLLOW", actor, "TEST:FOLLOW:1", "有人关注了你");
        async.emit(recipient, actor, "FOLLOW", actor, "TEST:FOLLOW:1", "有人关注了你");
        org.junit.jupiter.api.Assertions.assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM notification_outbox", Long.class));
        org.junit.jupiter.api.Assertions.assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM notification", Long.class));
        NotificationEventDTO event = new NotificationEventDTO(recipient, actor, "FOLLOW", actor, "TEST:FOLLOW:1", "有人关注了你");
        async.persistEvent(event);
        async.persistEvent(event);
        org.junit.jupiter.api.Assertions.assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM notification", Long.class));
    }

    /** 两个并发重试带同一个 clientMessageId，唯一约束确保只落一条，未读只加一次。 */
    @Test
    void concurrentMessageRetryDoesNotDuplicate() throws Exception {
        register("race_sender", "race-sender@example.com");
        long recipient = register("race_receiver", "race-receiver@example.com");
        String token = login("race_sender");
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<MvcResult> request = () -> {
            start.await();
            return mvc.perform(post("/api/messages/users/{id}", recipient).header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clientMessageId\":\"same-send\",\"type\":\"TEXT\",\"content\":\"并发测试\"}"))
                    .andReturn();
        };
        try {
            var first = workers.submit(request);
            var second = workers.submit(request);
            start.countDown();
            MvcResult a = first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            MvcResult b = second.get(10, java.util.concurrent.TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertEquals(200, a.getResponse().getStatus());
            org.junit.jupiter.api.Assertions.assertEquals(200, b.getResponse().getStatus());
            org.junit.jupiter.api.Assertions.assertEquals(id(a), id(b));
            org.junit.jupiter.api.Assertions.assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM private_message", Long.class));
            org.junit.jupiter.api.Assertions.assertEquals(1L, db.queryForObject("SELECT unread_count FROM conversation_member WHERE user_id=?", Long.class, recipient));
        } finally {
            workers.shutdownNow();
        }
    }

    /** 资料写接口不接受 URL 中的他人 id，只能使用登录令牌里的当前用户。 */
    @Test
    void profileUpdateCannotModifyAnotherUser() throws Exception {
        long alice = register("profile_alice", "profile-alice@example.com");
        long bob = register("profile_bob", "profile-bob@example.com");
        db.update("INSERT INTO user_profile(user_id, display_name, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                alice, "Alice原名");
        String bobToken = login("profile_bob");
        String aliceBefore = db.queryForObject("SELECT display_name FROM user_profile WHERE user_id=?", String.class, alice);
        String body = "{\"displayName\":\"Bob的新名字\",\"gender\":\"UNKNOWN\",\"signature\":\"只改本人\",\"major\":\"计算机\",\"birthday\":null}";
        mvc.perform(put("/api/profiles/{id}", alice).header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/profiles/me").header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(bob));
        org.junit.jupiter.api.Assertions.assertEquals(aliceBefore,
                db.queryForObject("SELECT display_name FROM user_profile WHERE user_id=?", String.class, alice));
        org.junit.jupiter.api.Assertions.assertEquals("Bob的新名字",
                db.queryForObject("SELECT display_name FROM user_profile WHERE user_id=?", String.class, bob));
    }

    /** 验证 F10/F11：私信隔离、重复发送去重、管理员角色和禁言。 */
    @Test
    void messagesAndAdminChecks() throws Exception {
        long alice = register("admin_alice", "admin-alice@example.com");
        long bob = register("admin_bob", "admin-bob@example.com");
        register("admin_root", "admin-root@example.com");
        db.update("UPDATE user_account SET role='ADMIN' WHERE account='admin_root'");
        String a = login("admin_alice"), b = login("admin_bob"), root = login("admin_root");
        String body = "{\"clientMessageId\":\"m1\",\"type\":\"TEXT\",\"content\":\"你好\"}";
        MvcResult sent = mvc.perform(post("/api/messages/users/{id}", bob).header("Authorization", bearer(a))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
        long conversation = json.readTree(sent.getResponse().getContentAsByteArray()).path("data").path("conversationId").asLong();
        mvc.perform(post("/api/messages/users/{id}", bob).header("Authorization", bearer(a))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/messages/conversations").header("Authorization", bearer(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].unreadCount").value(1));
        mvc.perform(get("/api/messages/unread-count").header("Authorization", bearer(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(1));
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(1));
        mvc.perform(get("/api/messages/conversations/{id}", conversation).header("Authorization", bearer(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/messages/conversations/{id}", conversation).header("Authorization", bearer(root)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/messages/conversations/{id}/read", conversation).header("Authorization", bearer(b)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/messages/conversations").header("Authorization", bearer(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].unreadCount").value(0));
        mvc.perform(get("/api/messages/unread-count").header("Authorization", bearer(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(0));
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(0));
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(a))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(root))).andExpect(status().isOk());
        mvc.perform(put("/api/admin/users/{id}/role", bob).header("Authorization", bearer(root))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SUPER_ADMIN_FORBIDDEN"));
        mvc.perform(put("/api/admin/users/{id}/mute", alice).header("Authorization", bearer(root))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"刷屏\",\"expiresAt\":\"2099-01-01T00:00:00\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/posts").header("Authorization", bearer(a)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"学习交流\",\"title\":\"被禁言\",\"content\":\"内容\",\"tags\":[]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_MUTED"));
        mvc.perform(get("/api/admin/audit").header("Authorization", bearer(root)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(put("/api/admin/users/{id}/unmute", alice).header("Authorization", bearer(root))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"申诉通过\"}"))
                .andExpect(status().isOk());
        long pending = id(mvc.perform(post("/api/posts").header("Authorization", bearer(a)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"学习交流\",\"title\":\"待审核\",\"content\":\"内容足够阅读\",\"tags\":[]}"))
                .andExpect(status().isCreated()).andReturn());
        db.update("UPDATE post SET status='PENDING' WHERE id=?", pending);
        mvc.perform(get("/api/admin/posts?keyword=审核").header("Authorization", bearer(root)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(pending));
        mvc.perform(get("/api/admin/posts?keyword=足够阅读").header("Authorization", bearer(root)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/admin/posts?keyword=完全不存在").header("Authorization", bearer(root)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(put("/api/admin/posts/{id}/approve", pending).header("Authorization", bearer(root))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"内容正常\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mvc.perform(put("/api/admin/posts/{id}/top", pending).header("Authorization", bearer(root))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\"优秀内容\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.top").value(true));
        mvc.perform(put("/api/admin/posts/{id}/delete", pending).header("Authorization", bearer(root))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"后续违规\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/posts/{id}", pending)).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/users/{id}/ban", bob).header("Authorization", bearer(root))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"严重违规\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/messages/conversations").header("Authorization", bearer(b)))
                .andExpect(status().isUnauthorized());
    }

    /** 前端注册请求的测试帮助函数：读取统一响应 data.id。 */
    private long register(String account, String email) throws Exception {
        return id(mvc.perform(post("/api/accounts/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + account + "\",\"password\":\"Password123\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated()).andReturn());
    }

    /** 登录后从 JSON 中取 JWT，模拟浏览器保存并发送 Authorization。 */
    private String login(String account) throws Exception {
        MvcResult result = mvc.perform(post("/api/accounts/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + account + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("accessToken").asText();
    }

    /** 测试中从 Controller 响应取生成的主键，后续接口沿用此 id。 */
    private long id(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("id").asLong();
    }

    /** 模拟浏览器请求头的 Bearer 前缀。 */
    private String bearer(String token) { return "Bearer " + token; }
}
