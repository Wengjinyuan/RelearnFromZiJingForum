package org.example.rebuiltzijingforum.f06_follow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.example.rebuiltzijingforum.f06_follow.entity.FollowEntity;
import org.example.rebuiltzijingforum.f06_follow.vo.FollowUserVO;
import org.example.rebuiltzijingforum.f06_follow.vo.FollowFeedVO;

import java.time.LocalDateTime;
import java.util.List;
@Mapper
/** F06 SQL：follow_relation 一行表示 follower_id 关注 followed_id。 */
@Repository
public class FollowMapper {
    private final JdbcTemplate jdbc;

    /** Spring 注入 SQL 执行工具。 */
    public FollowMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 关注前查用户是否存在且未禁用。 */
    public boolean activeUser(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE id=? AND status<>'BANNED'", Long.class, id) > 0;
    }

    /** 查询指定方向的一条关系，给按钮状态和幂等判断使用。 */
    public boolean follows(long follower, long target) {
        return !jdbc.query("SELECT follower_id,followed_id,created_at FROM follow_relation WHERE follower_id=? AND followed_id=?",
                (rs, i) -> new FollowEntity(rs.getLong(1), rs.getLong(2), rs.getTimestamp(3).toLocalDateTime()), follower, target).isEmpty();
    }

    /** INSERT 关系；组合唯一键最终防止重复关注。 */
    public int add(FollowEntity row) {
        return jdbc.update("INSERT INTO follow_relation(follower_id,followed_id,created_at) VALUES(?,?,?)", row.followerId(), row.followedId(), row.createdAt());
    }

    /** DELETE 只撤销当前用户关注目标的这一条关系。 */
    public int remove(long follower, long target) {
        return jdbc.update("DELETE FROM follow_relation WHERE follower_id=? AND followed_id=?", follower, target);
    }

    /** COUNT 根据 follower_id 得到“我关注的人数”。 */
    public long followingCount(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM follow_relation WHERE follower_id=?", Long.class, userId);
    }

    /** COUNT 根据 followed_id 得到“我的粉丝数”；关系表是计数真相。 */
    public long followerCount(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM follow_relation WHERE followed_id=?", Long.class, userId);
    }

    /** JOIN 右边的用户表，按当前用户关注的人返回账号列表。 */
    public List<FollowUserVO> following(long userId, int size, int offset) {
        return jdbc.query("SELECT u.id,u.account FROM follow_relation f JOIN user_account u ON u.id=f.followed_id WHERE f.follower_id=? ORDER BY f.created_at DESC,u.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new FollowUserVO(rs.getLong(1), rs.getString(2)), userId, size, offset);
    }

    /** JOIN 左边的用户表，按被关注人返回粉丝列表。 */
    public List<FollowUserVO> followers(long userId, int size, int offset) {
        return jdbc.query("SELECT u.id,u.account FROM follow_relation f JOIN user_account u ON u.id=f.follower_id WHERE f.followed_id=? ORDER BY f.created_at DESC,u.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new FollowUserVO(rs.getLong(1), rs.getString(2)), userId, size, offset);
    }

    /** 拉模式 Feed：先用 f.follower_id 找关注关系，再用 p.author_id 找这些作者发布的帖子。 */
    public List<FollowFeedVO> feed(long viewerId, int size, int offset) {
        return jdbc.query("SELECT p.id,p.author_id,p.title,p.summary,p.created_at FROM follow_relation f JOIN post p ON p.author_id=f.followed_id WHERE f.follower_id=? AND p.status='PUBLISHED' AND p.deleted_at IS NULL ORDER BY p.created_at DESC,p.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new FollowFeedVO(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toLocalDateTime()), viewerId, size, offset);
    }

    /** Feed 总数使用和列表完全相同的 JOIN/WHERE，不包含已删除或待审核帖子。 */
    public long feedCount(long viewerId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM follow_relation f JOIN post p ON p.author_id=f.followed_id WHERE f.follower_id=? AND p.status='PUBLISHED' AND p.deleted_at IS NULL", Long.class, viewerId);
    }

}
