package org.example.rebuiltzijingforum.f11_admin.mapper;

import org.example.rebuiltzijingforum.f11_admin.entity.PunishmentEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** 专门操作处罚历史；user_account.status 是快速判定的当前状态。 */
@Repository
public class PunishmentMapper {
    private final JdbcTemplate db;
    /** Spring 注入 JDBC。 */
    public PunishmentMapper(JdbcTemplate db) { this.db = db; }

    /** 处罚/解除都 INSERT 新历史，审计和状态更新在 Service 事务中一起提交。 */
    public void insert(long userId, long operatorId, String action, String reason, LocalDateTime expires, LocalDateTime now) {
        db.update("INSERT INTO user_punishment(user_id,operator_id,action,reason,expires_at,created_at) VALUES(?,?,?,?,?,?)",
                userId, operatorId, action, reason, expires, now);
    }

    /** 只看指定用户的历史，按最新动作在前。 */
    public List<PunishmentEntity> list(long userId) {
        return db.query("SELECT id,user_id,operator_id,action,reason,expires_at,created_at FROM user_punishment WHERE user_id=? ORDER BY id DESC",
                (rs, i) -> new PunishmentEntity(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                        rs.getString(5), rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toLocalDateTime(),
                        rs.getTimestamp(7).toLocalDateTime()), userId);
    }

    /** 定时任务只选仍处于 MUTED、且最新禁言记录已到期的用户。 */
    public List<Long> dueMuteIds(LocalDateTime now, int limit) {
        return db.query("""
                SELECT u.id FROM user_account u JOIN user_punishment p ON p.user_id=u.id
                WHERE u.status='MUTED' AND p.action='MUTE' AND p.expires_at<=?
                  AND p.id=(SELECT MAX(p2.id) FROM user_punishment p2 WHERE p2.user_id=u.id AND p2.action='MUTE')
                ORDER BY u.id LIMIT ?
                """, (rs, i) -> rs.getLong(1), now, limit);
    }

    /** 多个实例同时执行时只有一个 UPDATE 能把 MUTED 改为 NORMAL。 */
    public int clearExpiredMute(long userId, LocalDateTime now) {
        return db.update("""
                UPDATE user_account SET status='NORMAL',updated_at=? WHERE id=? AND status='MUTED'
                  AND EXISTS (SELECT 1 FROM user_punishment p WHERE p.user_id=user_account.id
                    AND p.action='MUTE' AND p.expires_at<=?
                    AND p.id=(SELECT MAX(p2.id) FROM user_punishment p2
                              WHERE p2.user_id=user_account.id AND p2.action='MUTE'))
                """, now, userId, now);
    }
}
