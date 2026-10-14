package org.example.rebuiltzijingforum.common.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;

/** Repeatable sample content for the local H2 practice database. */
@Component
@ConditionalOnProperty(name = "forum.demo-data.enabled", havingValue = "true")
public class H2DemoData implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;

    public H2DemoData(JdbcTemplate jdbc, DataSource dataSource, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!"H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return;
        }
        long superAdmin = account("demo_super_admin", "demo_super_admin@example.test", "SUPER_ADMIN", "演示超级管理员");
        long admin = account("demo_admin", "demo_admin@example.test", "ADMIN", "演示管理员");
        long alice = account("demo_alice", "demo_alice@example.test", "USER", "小紫");
        long bob = account("demo_bob", "demo_bob@example.test", "USER", "小荆");
        post(alice, "学习交流", "欢迎来到紫荆论坛", "<p>这里可以交流课程、社团和校园生活。登录演示账号后，试试发帖、评论、点赞和关注。</p>", "新手指南", true);
        post(bob, "校园资讯", "本周校园活动推荐", "<p>欢迎在评论区分享你感兴趣的讲座、比赛和社团活动。</p>", "校园活动", false);
        post(alice, "二手交易", "闲置教材交换帖", "<p>有闲置教材的同学可以在评论区留言，约好地点后当面交换。</p>", "二手教材", false);
        post(superAdmin, "公告", "演示环境使用说明", "<p>这是本地 H2 演示数据。数据库保存在 data 目录，重新启动不会重复插入。</p>", "公告", false);
        long welcome = id("SELECT id FROM post WHERE title=? AND author_id=?", "欢迎来到紫荆论坛", alice);
        if (count("SELECT COUNT(*) FROM comment WHERE post_id=? AND author_id=?", welcome, bob) == 0) {
            jdbc.update("INSERT INTO comment(post_id,author_id,content,like_count,deleted,created_at) VALUES(?,?,?,0,FALSE,?)",
                    welcome, bob, "欢迎！我来试试评论功能。", LocalDateTime.now().minusMinutes(30));
            jdbc.update("UPDATE post SET comment_count=comment_count+1 WHERE id=?", welcome);
        }
        if (count("SELECT COUNT(*) FROM follow_relation WHERE follower_id=? AND followed_id=?", bob, alice) == 0) {
            jdbc.update("INSERT INTO follow_relation(follower_id,followed_id,created_at) VALUES(?,?,?)", bob, alice, LocalDateTime.now());
        }
        if (count("SELECT COUNT(*) FROM notification WHERE event_key=?", "DEMO:WELCOME:COMMENT") == 0) {
            jdbc.update("INSERT INTO notification(user_id,actor_id,kind,target_id,event_key,content,is_read,created_at) VALUES(?,?,?,?,?, ?,FALSE,?)",
                    alice, bob, "COMMENT", welcome, "DEMO:WELCOME:COMMENT", "小荆评论了你的欢迎帖", LocalDateTime.now());
        }
    }

    private long account(String name, String email, String role, String displayName) {
        if (count("SELECT COUNT(*) FROM user_account WHERE account=?", name) == 0) {
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("INSERT INTO user_account(account,password_hash,email,role,status,token_version,created_at,updated_at) VALUES(?,?,?,?,?,0,?,?)",
                    name, passwordEncoder.encode("Demo12345"), email, role, "NORMAL", now, now);
        }
        long userId = id("SELECT id FROM user_account WHERE account=?", name);
        if (count("SELECT COUNT(*) FROM user_profile WHERE user_id=?", userId) == 0) {
            jdbc.update("INSERT INTO user_profile(user_id,display_name,signature,updated_at) VALUES(?,?,?,?)",
                    userId, displayName, "H2 演示账号", LocalDateTime.now());
        }
        return userId;
    }

    private void post(long authorId, String category, String title, String content, String tagName, boolean top) {
        if (count("SELECT COUNT(*) FROM post WHERE author_id=? AND title=?", authorId, title) > 0) return;
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO post(author_id,category,title,content,summary,status,is_top,is_essence,created_at,updated_at) VALUES(?,?,?,?,?,'PUBLISHED',?,?,?,?)",
                authorId, category, title, content, content.replaceAll("<[^>]+>", ""), top, top, now, now);
        long postId = id("SELECT id FROM post WHERE author_id=? AND title=?", authorId, title);
        if (count("SELECT COUNT(*) FROM tag WHERE name=?", tagName) == 0) {
            jdbc.update("INSERT INTO tag(name,use_count,created_at) VALUES(?,0,?)", tagName, now);
        }
        long tagId = id("SELECT id FROM tag WHERE name=?", tagName);
        jdbc.update("INSERT INTO post_tag(post_id,tag_id) VALUES(?,?)", postId, tagId);
        jdbc.update("UPDATE tag SET use_count=use_count+1 WHERE id=?", tagId);
    }

    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    private long id(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
}
