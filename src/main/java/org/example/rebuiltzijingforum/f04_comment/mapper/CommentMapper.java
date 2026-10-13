package org.example.rebuiltzijingforum.f04_comment.mapper;

import org.example.rebuiltzijingforum.common.db.DbIds;
import org.example.rebuiltzijingforum.f04_comment.entity.CommentEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Collection;
import java.util.Map;

/** F04 数据库访问层：这里的 SQL 是 Service 调用后真正执行的数据库操作。 */
@Repository
public class CommentMapper {
    private final JdbcTemplate jdbc;

    /** Spring 注入 JDBC 连接工具；本类不决定谁有权限评论。 */
    public CommentMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** SQL 的 WHERE 只接受已发布且未删除的帖子；Service 用它拦截无效 postId。 */
    public boolean visiblePost(long postId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM post WHERE id=? AND status='PUBLISHED' AND deleted_at IS NULL", Long.class, postId) > 0;
    }

    /** 创建评论后找到帖子作者，以便 F07 给作者发通知。 */
    public long postAuthor(long postId) {
        return jdbc.queryForObject("SELECT author_id FROM post WHERE id=?", Long.class, postId);
    }

    /** 根据评论 id 查一行；Service 随后验证帖子归属、删除状态或作者权限。 */
    public Optional<CommentEntity> find(long id) {
        List<CommentEntity> rows = jdbc.query("SELECT * FROM comment WHERE id=?", this::map, id);
        return rows.stream().findFirst();
    }

    /** INSERT 写入评论；parent/root/replyUser 由 Service 根据父评论计算，不能由客户端伪造。 */
    public long insert(long postId, long authorId, Long parentId, Long rootId, Long replyUserId, String content, String imageUrls, LocalDateTime now) {
        return DbIds.insert(jdbc, "INSERT INTO comment(post_id,author_id,parent_id,root_id,reply_user_id,content,image_urls,created_at) VALUES(?,?,?,?,?,?,?,?)",
                postId, authorId, parentId, rootId, replyUserId, content, imageUrls, now);
    }

    /** 原子加一：数据库用当前 comment_count+1，避免两人同时评论时相互覆盖。 */
    public int incrementPostCount(long postId) {
        return jdbc.update("UPDATE post SET comment_count=comment_count+1 WHERE id=? AND status='PUBLISHED' AND deleted_at IS NULL", postId);
    }

    /** 原子减一且不小于零；删除评论的事务会同时修改 comment 和 post。 */
    public void decrementPostCount(long postId) {
        jdbc.update("UPDATE post SET comment_count=comment_count-1 WHERE id=? AND comment_count>0", postId);
    }

    /** 逻辑删除保留父评论占位，让子回复的上下文仍可读。 */
    public int markDeleted(long id, LocalDateTime now) {
        return jdbc.update("UPDATE comment SET deleted=TRUE, deleted_at=? WHERE id=? AND deleted=FALSE", now, id);
    }

    /** 只计算一级评论：parent_id IS NULL 是“没有直接父评论”的条件。 */
    public long countRoots(long postId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM comment WHERE post_id=? AND parent_id IS NULL", Long.class, postId);
    }

    /** 按稳定顺序取一页一级评论；LIMIT 控制页大小，OFFSET 跳过之前的页。 */
    public List<CommentEntity> roots(long postId, String sort, int size, int offset) {
        String order = switch (sort) {
            case "asc" -> "created_at ASC, id ASC";
            case "desc" -> "created_at DESC, id DESC";
            default -> "like_count DESC, id DESC";
        };
        return jdbc.query("SELECT * FROM comment WHERE post_id=? AND parent_id IS NULL ORDER BY " + order + " LIMIT ? OFFSET ?",
                this::map, postId, size, offset);
    }

    /** 查询某个根评论下的回复；root_id 保证 B 回复 A、C 回复 B 仍在同一棵树。 */
    public List<CommentEntity> replies(long rootId, int size, long beforeId) {
        return jdbc.query("SELECT * FROM comment WHERE root_id=? AND id<? ORDER BY id DESC LIMIT ?",
                this::map, rootId, beforeId, size);
    }

    /** 一次 SQL 加载当前页所有根评论的每棵树前 3 条回复，避免循环调用 replies 形成 N+1 查询。 */
    public List<CommentEntity> previewReplies(List<Long> rootIds) {
        if (rootIds.isEmpty()) return List.of();
        String marks = rootIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT * FROM (SELECT c.*, ROW_NUMBER() OVER (PARTITION BY root_id ORDER BY id DESC) AS rn "
                + "FROM comment c WHERE root_id IN (" + marks + ")) ranked WHERE rn<=3 ORDER BY root_id,id DESC";
        return jdbc.query(sql, this::map, rootIds.toArray());
    }

    /** 一次读取本页所有作者/被回复用户的账号名；? 个数只由 ID 数量决定，值仍通过 JDBC 绑定。 */
    public Map<Long, String> accountNames(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        String marks = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
        return jdbc.query("SELECT id,account FROM user_account WHERE id IN (" + marks + ")",
                rs -> {
                    Map<Long, String> names = new java.util.HashMap<>();
                    while (rs.next()) names.put(rs.getLong(1), rs.getString(2));
                    return names;
                }, userIds.toArray());
    }

    /** 一行 ResultSet 转为 Row；这一步仅做类型映射，不做业务判断。 */
    private CommentEntity map(ResultSet rs, int rowNum) throws SQLException {
        return new CommentEntity(rs.getLong("id"), rs.getLong("post_id"), rs.getLong("author_id"),
                rs.getObject("parent_id", Long.class), rs.getObject("root_id", Long.class),
                rs.getObject("reply_user_id", Long.class), rs.getString("content"),
                rs.getInt("like_count"), rs.getBoolean("deleted"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getString("image_urls"), rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toLocalDateTime());
    }

}
