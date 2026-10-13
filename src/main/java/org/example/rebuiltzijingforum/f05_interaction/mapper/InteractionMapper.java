package org.example.rebuiltzijingforum.f05_interaction.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** F05 的 SQL 集中在这里：关系表决定“有没有操作”，主表计数便于页面快速展示。 */
@Repository
public class InteractionMapper {
    private final JdbcTemplate jdbc;

    /** Spring 注入 JdbcTemplate，由它绑定 ? 参数并执行 SQL。 */
    public InteractionMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Service 先查目标是否公开可见，避免对不存在或已删除帖子点赞。 */
    public boolean postExists(long postId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM post WHERE id=? AND status='PUBLISHED' AND deleted_at IS NULL", Long.class, postId) > 0;
    }

    /** 评论必须未删除，且所在帖子仍公开；两个 EXISTS 条件共同限制目标。 */
    public boolean commentExists(long commentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM comment c JOIN post p ON p.id=c.post_id WHERE c.id=? AND c.deleted=FALSE AND p.status='PUBLISHED' AND p.deleted_at IS NULL", Long.class, commentId) > 0;
    }

    /** 点赞/收藏成功后定位内容作者，供 F07 发送通知。 */
    public long author(String type, long id) {
        String sql = "COMMENT".equals(type) ? "SELECT author_id FROM comment WHERE id=?" : "SELECT author_id FROM post WHERE id=?";
        return jdbc.queryForObject(sql, Long.class, id);
    }

    /** 关系变化后才更新计数；加减在数据库内完成，不能在 Java 中读旧值再写。 */
    public int changeLikeCount(String type, long targetId, int delta) {
        String table = "COMMENT".equals(type) ? "comment" : "post";
        return jdbc.update("UPDATE " + table + " SET like_count=like_count+? WHERE id=? AND like_count+?>=0", delta, targetId, delta);
    }

    /** 冗余收藏数跟随关系变化；条件保证不会减成负数。 */
    public void changeFavoriteCount(long postId, int delta) {
        jdbc.update("UPDATE post SET favorite_count=favorite_count+? WHERE id=? AND favorite_count+?>=0", delta, postId, delta);
    }

    /** 当前第一版的浏览量是精确请求次数 PV：每次调用 +1，不是独立访客 UV。 */
    public int recordView(long postId) {
        return jdbc.update("UPDATE post SET view_count=view_count+1 WHERE id=? AND status='PUBLISHED' AND deleted_at IS NULL", postId);
    }

    /** 返回页面需要的计数；Service 把它和当前用户关系状态一起装成 View。 */
    public Counts counts(long postId) {
        return jdbc.queryForObject("SELECT view_count,like_count,favorite_count FROM post WHERE id=?",
                (rs, n) -> new Counts(rs.getInt(1), rs.getInt(2), rs.getInt(3)), postId);
    }

    /** 三项帖子计数。 */
    public record Counts(int views, int likes, int favorites) {}
}
