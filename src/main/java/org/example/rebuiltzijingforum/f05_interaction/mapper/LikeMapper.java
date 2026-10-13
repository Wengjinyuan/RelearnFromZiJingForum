package org.example.rebuiltzijingforum.f05_interaction.mapper;

import org.example.rebuiltzijingforum.f05_interaction.entity.LikeEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 专门操作 like_record；计数仍由 InteractionMapper 原子更新。 */
@Repository
public class LikeMapper {
    private final JdbcTemplate db;
    /** Spring 注入 JDBC。 */
    public LikeMapper(JdbcTemplate db) { this.db = db; }
    /** SELECT 三列唯一键，存在即表示当前用户已点赞。 */
    public Optional<LikeEntity> find(long userId, String type, long targetId) {
        return db.query("SELECT user_id,target_type,target_id FROM like_record WHERE user_id=? AND target_type=? AND target_id=?",
                (rs, i) -> new LikeEntity(rs.getLong(1), rs.getString(2), rs.getLong(3)), userId, type, targetId).stream().findFirst();
    }
    /** 第一次点赞 INSERT；数据库唯一约束是并发时最后一道防线。 */
    public int insert(LikeEntity row) {
        return db.update("INSERT INTO like_record(user_id,target_type,target_id) VALUES(?,?,?)", row.userId(), row.targetType(), row.targetId());
    }
    /** 只删除指定用户/类型/目标的关系，受影响行数决定计数是否减一。 */
    public int delete(long userId, String type, long targetId) {
        return db.update("DELETE FROM like_record WHERE user_id=? AND target_type=? AND target_id=?", userId, type, targetId);
    }
}
