package org.example.rebuiltzijingforum.f09_home.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.example.rebuiltzijingforum.f09_home.vo.HotTagVO;

import java.time.LocalDateTime;
import java.util.List;

/** F09 聚合 SQL：每个 COUNT 的 WHERE 就是统计口径。 */
@Repository
public class HomeMapper {
    private final JdbcTemplate jdbc;

    /** Spring 注入 SQL 执行工具。 */
    public HomeMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 用户总数只统计非禁用账号；若产品需要全部注册数，可调整此 WHERE。 */
    public long users() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE status<>'BANNED'", Long.class);
    }

    /** 总帖子数只包括 PUBLISHED 且没有 deleted_at 的帖子。 */
    public long posts() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM post WHERE status='PUBLISHED' AND deleted_at IS NULL", Long.class);
    }

    /** 半开区间 [start,end) 避免午夜 00:00 同时被昨天和今天统计。 */
    public long postsBetween(LocalDateTime start, LocalDateTime end) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM post WHERE status='PUBLISHED' AND deleted_at IS NULL AND created_at>=? AND created_at<?",
                Long.class, start, end);
    }

    /** 从有效帖子的 post_tag 关系 COUNT 热度；删除帖子不会继续贡献热门标签。 */
    public List<HotTagVO> hotTags(int limit) {
        return jdbc.query("SELECT t.name,COUNT(*) AS used FROM tag t JOIN post_tag pt ON pt.tag_id=t.id JOIN post p ON p.id=pt.post_id WHERE p.status='PUBLISHED' AND p.deleted_at IS NULL GROUP BY t.id,t.name ORDER BY used DESC,t.name ASC LIMIT ?",
                (rs, n) -> new HotTagVO(rs.getString(1), rs.getLong(2)), limit);
    }

}
