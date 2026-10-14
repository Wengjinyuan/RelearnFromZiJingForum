package org.example.rebuiltzijingforum.f08_search.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.example.rebuiltzijingforum.f08_search.vo.PostSearchVO;
import org.example.rebuiltzijingforum.f08_search.vo.UserSearchVO;
import org.example.rebuiltzijingforum.f08_search.entity.EsPostEntity;
import org.example.rebuiltzijingforum.f08_search.entity.EsUserEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

/** F08 第一版用 MySQL/H2 的 LIKE 查事实表，数据库操作均集中在 Mapper。 */
@Repository
public class SearchMapper {
    private final JdbcTemplate jdbc;

    /** Spring 注入 JdbcTemplate。 */
    public SearchMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** COUNT 与列表使用同一过滤条件；已删除/待审核帖子永远不进入结果。 */
    public long countPosts(String pattern, String category) {
        String sql = "SELECT COUNT(*) FROM post p WHERE p.status='PUBLISHED' AND p.deleted_at IS NULL "
                + "AND (LOWER(p.title) LIKE ? ESCAPE '!' OR LOWER(p.summary) LIKE ? ESCAPE '!' "
                + "OR EXISTS (SELECT 1 FROM post_tag pt JOIN tag t ON t.id=pt.tag_id WHERE pt.post_id=p.id AND LOWER(t.name) LIKE ? ESCAPE '!'))"
                + (category == null ? "" : " AND p.category=?");
        List<Object> args = new ArrayList<>(List.of(pattern, pattern, pattern));
        if (category != null) args.add(category);
        return jdbc.queryForObject(sql, Long.class, args.toArray());
    }

    /** 标题/摘要/标签匹配；CASE 给标题更高权重，最后 id 排序保证分页稳定。 */
    public List<PostSearchVO> posts(String pattern, String category, String sort, int size, int offset) {
        String base = """
                SELECT p.id,p.title,p.summary,p.author_id,COALESCE(up.display_name,u.account) AS author_name,
                       p.category,p.view_count,p.like_count,p.comment_count,p.favorite_count,p.is_essence,p.created_at
                FROM post p
                JOIN user_account u ON u.id=p.author_id
                LEFT JOIN user_profile up ON up.user_id=p.author_id
                WHERE p.status='PUBLISHED' AND p.deleted_at IS NULL
                  AND (LOWER(p.title) LIKE ? ESCAPE '!' OR LOWER(p.summary) LIKE ? ESCAPE '!'
                       OR EXISTS (SELECT 1 FROM post_tag pt JOIN tag t ON t.id=pt.tag_id
                                  WHERE pt.post_id=p.id AND LOWER(t.name) LIKE ? ESCAPE '!'))
                """ + (category == null ? "" : " AND p.category=? ");
        String order = switch (sort) {
            case "latest" -> "ORDER BY p.created_at DESC,p.id DESC";
            case "most_viewed" -> "ORDER BY p.view_count DESC,p.id DESC";
            case "most_liked" -> "ORDER BY p.like_count DESC,p.id DESC";
            default -> "ORDER BY CASE WHEN LOWER(p.title) LIKE ? ESCAPE '!' THEN 2 ELSE 1 END DESC,p.created_at DESC,p.id DESC";
        };
        List<Object> args = new ArrayList<>(List.of(pattern, pattern, pattern));
        if (category != null) args.add(category);
        if ("relevance".equals(sort)) args.add(pattern);
        args.add(size);
        args.add(offset);
        return jdbc.query(base + order + " LIMIT ? OFFSET ?", (rs, n) -> new PostSearchVO(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5),
                rs.getString(6), rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getInt(10),
                rs.getBoolean(11), rs.getTimestamp(12).toLocalDateTime(), null, null), args.toArray());
    }

    /** 用户名或签名匹配；LEFT JOIN 允许尚未填写个人资料的用户也能按账号搜到。 */
    public List<UserSearchVO> users(String pattern, int size, int offset) {
        return jdbc.query("SELECT u.id,u.account,p.display_name,p.signature FROM user_account u LEFT JOIN user_profile p ON p.user_id=u.id WHERE u.status<>'BANNED' AND (LOWER(u.account) LIKE ? ESCAPE '!' OR LOWER(COALESCE(p.display_name,'')) LIKE ? ESCAPE '!' OR LOWER(COALESCE(p.signature,'')) LIKE ? ESCAPE '!') ORDER BY u.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new UserSearchVO(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), null, null), pattern, pattern, pattern, size, offset);
    }

    /** 用户搜索的 COUNT 与 users 的 WHERE 保持一致。 */
    public long countUsers(String pattern) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_account u LEFT JOIN user_profile p ON p.user_id=u.id WHERE u.status<>'BANNED' AND (LOWER(u.account) LIKE ? ESCAPE '!' OR LOWER(COALESCE(p.display_name,'')) LIKE ? ESCAPE '!' OR LOWER(COALESCE(p.signature,'')) LIKE ? ESCAPE '!')",
                Long.class, pattern, pattern, pattern);
    }

    /** ES 同步必须回 MySQL 看当前状态；旧事件只携带 id，不携带可能过时的正文。 */
    public Optional<EsPostEntity> indexPost(long id) {
        var rows = jdbc.query("""
                SELECT p.id,p.title,p.summary,p.author_id,COALESCE(up.display_name,u.account),p.category,
                       p.view_count,p.like_count,p.comment_count,p.favorite_count,p.is_essence,p.created_at,p.updated_at
                FROM post p JOIN user_account u ON u.id=p.author_id
                LEFT JOIN user_profile up ON up.user_id=p.author_id
                WHERE p.id=? AND p.status='PUBLISHED' AND p.deleted_at IS NULL
                """, (rs, n) -> new EsPostEntity(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getString(5), rs.getString(6), rs.getInt(7), rs.getInt(8), rs.getInt(9),
                        rs.getInt(10), rs.getBoolean(11), List.of(), rs.getTimestamp(12).toLocalDateTime(),
                        rs.getTimestamp(13).toLocalDateTime()), id);
        if (rows.isEmpty()) return Optional.empty();
        EsPostEntity row = rows.get(0);
        List<String> tags = jdbc.query("SELECT t.name FROM post_tag pt JOIN tag t ON t.id=pt.tag_id WHERE pt.post_id=? ORDER BY t.id",
                (rs, n) -> rs.getString(1), id);
        return Optional.of(new EsPostEntity(row.id(), row.title(), row.summary(), row.authorId(), row.authorName(),
                row.category(), row.viewCount(), row.likeCount(), row.commentCount(), row.favoriteCount(),
                row.essence(), tags, row.createdAt(), row.sourceUpdatedAt()));
    }

    /** BANNED 用户不应留在公开搜索索引中。 */
    public Optional<EsUserEntity> indexUser(long id) {
        return jdbc.query("SELECT u.id,u.account,p.display_name,p.signature,u.updated_at FROM user_account u LEFT JOIN user_profile p ON p.user_id=u.id WHERE u.id=? AND u.status<>'BANNED'",
                (rs, n) -> new EsUserEntity(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getTimestamp(5).toLocalDateTime()), id).stream().findFirst();
    }

    /** 全量重建用主键递增游标，不用越翻越慢的 OFFSET。 */
    public List<Long> postIdsAfter(long id, int size) {
        return jdbc.query("SELECT id FROM post WHERE id>? AND status='PUBLISHED' AND deleted_at IS NULL ORDER BY id LIMIT ?",
                (rs, n) -> rs.getLong(1), id, size);
    }

    public List<Long> userIdsAfter(long id, int size) {
        return jdbc.query("SELECT id FROM user_account WHERE id>? AND status<>'BANNED' ORDER BY id LIMIT ?",
                (rs, n) -> rs.getLong(1), id, size);
    }

    /** 全量重建后用 MySQL 公开帖子数与新 ES 索引 _count 比较，数量不符不切别名。 */
    public long publishedPostCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM post WHERE status='PUBLISHED' AND deleted_at IS NULL", Long.class);
    }

    /** 用户索引只应包含未封禁用户，因此核对也使用相同 WHERE。 */
    public long searchableUserCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE status<>'BANNED'", Long.class);
    }

}
