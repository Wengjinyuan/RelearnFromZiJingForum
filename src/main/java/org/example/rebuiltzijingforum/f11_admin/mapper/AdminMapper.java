package org.example.rebuiltzijingforum.f11_admin.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** F11 SQL 层：所有 WHERE id=? 与状态条件都是防误改的重要组成部分。 */
@Repository
public class AdminMapper {
    private final JdbcTemplate db;

    /** Spring 注入 JDBC；这里的每个方法执行一条数据库操作。 */
    public AdminMapper(JdbcTemplate db) { this.db = db; }

    /** 管理员操作用户前查询其角色、状态；Service 据此禁止处罚管理员或自己。 */
    public Optional<UserRow> user(long id) {
        return db.query("SELECT id,account,role,status FROM user_account WHERE id=?",
                (rs, i) -> new UserRow(rs.getLong("id"), rs.getString("account"), rs.getString("role"), rs.getString("status")), id).stream().findFirst();
    }

    /** 用户列表：WHERE 可选模糊词，LIMIT/OFFSET 分页，不返回密码哈希。 */
    public List<UserRow> users(String keyword, int size, int offset) {
        return db.query("SELECT id,account,role,status FROM user_account WHERE LOWER(account) LIKE ? ESCAPE '!' ORDER BY id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new UserRow(rs.getLong("id"), rs.getString("account"), rs.getString("role"), rs.getString("status")), keyword, size, offset);
    }

    /** 与 users 相同的过滤条件，供前端计算总页数。 */
    public long userCount(String keyword) { return db.queryForObject("SELECT COUNT(*) FROM user_account WHERE LOWER(account) LIKE ? ESCAPE '!'", Long.class, keyword); }

    public int setUserRole(long id, String oldRole, String role, LocalDateTime now) {
        return db.update("UPDATE user_account SET role=?,token_version=token_version+1,updated_at=? WHERE id=? AND role=?",
                role, now, id, oldRole);
    }

    /** 修改单个用户状态；封禁时 token_version+1 让旧 JWT 立即作废。 */
    public int setUserStatus(long id, String oldStatus, String status, boolean revokeToken, LocalDateTime now) {
        return db.update("UPDATE user_account SET status=?, token_version=token_version+?, updated_at=? WHERE id=? AND status=? AND role NOT IN ('ADMIN','SUPER_ADMIN')",
                status, revokeToken ? 1 : 0, now, id, oldStatus);
    }

    /** 管理员改帖前取原状态，便于验证及生成审计日志。 */
    public Optional<PostRow> post(long id) {
        return db.query("SELECT id,author_id,title,status,is_top,is_essence FROM post WHERE id=?",
                (rs, i) -> new PostRow(rs.getLong("id"), rs.getLong("author_id"), rs.getString("title"), rs.getString("status"),
                        rs.getBoolean("is_top"), rs.getBoolean("is_essence")), id).stream().findFirst();
    }

    /** 帖子管理列表：可按状态过滤，包含待审核/已删除记录。 */
    public List<PostRow> posts(String status, String keyword, int size, int offset) {
        return db.query("""
                SELECT id,author_id,title,status,is_top,is_essence FROM post
                WHERE (?='' OR status=?)
                  AND (LOWER(title) LIKE ? ESCAPE '!' OR LOWER(content) LIKE ? ESCAPE '!')
                ORDER BY id DESC LIMIT ? OFFSET ?
                """,
                (rs, i) -> new PostRow(rs.getLong("id"), rs.getLong("author_id"), rs.getString("title"), rs.getString("status"),
                        rs.getBoolean("is_top"), rs.getBoolean("is_essence")), status, status, keyword, keyword, size, offset);
    }

    /** 帖子列表总数；与列表保持相同的 WHERE。 */
    public long postCount(String status, String keyword) {
        return db.queryForObject("""
                SELECT COUNT(*) FROM post
                WHERE (?='' OR status=?)
                  AND (LOWER(title) LIKE ? ESCAPE '!' OR LOWER(content) LIKE ? ESCAPE '!')
                """, Long.class, status, status, keyword, keyword);
    }

    /** 审核/拒绝/删除：WHERE 只修改目标帖；删除同时写 deleted_at。 */
    public int setPostStatus(long id, String oldStatus, String status, LocalDateTime now) {
        return db.update("UPDATE post SET status=?, updated_at=?, deleted_at=CASE WHEN ?='DELETED' THEN ? ELSE deleted_at END WHERE id=? AND status=?",
                status, now, status, now, id, oldStatus);
    }

    /** 后台删除也须维护 tag.use_count；先取当前帖关联 tag_id，再逐个原子减一。 */
    public void decrementTagsForPost(long postId) {
        List<Long> ids = db.query("SELECT tag_id FROM post_tag WHERE post_id=?", (rs, i) -> rs.getLong(1), postId);
        for (Long tagId : ids) db.update("UPDATE tag SET use_count=use_count-1 WHERE id=? AND use_count>0", tagId);
    }

    /** 置顶或精华只改一个布尔列；列名只能由代码常量选择，不能来自用户输入。 */
    public int setPostFlag(long id, String column, boolean enabled, LocalDateTime now) {
        if (!"is_top".equals(column) && !"is_essence".equals(column)) throw new IllegalArgumentException("非法列名");
        return db.update("UPDATE post SET " + column + "=?, updated_at=? WHERE id=? AND status='PUBLISHED'", enabled, now, id);
    }

    /** 不含密码的用户管理行。 */
    public record UserRow(long id, String account, String role, String status) {}
    /** 帖子审核行。 */
    public record PostRow(long id, long authorId, String title, String status, boolean top, boolean essence) {}
}
