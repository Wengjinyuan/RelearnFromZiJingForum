package org.example.rebuiltzijingforum.f11_admin.mapper;

import org.example.rebuiltzijingforum.common.db.DbIds;
import org.example.rebuiltzijingforum.f11_admin.entity.AuditLogEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** 专门操作审计日志；操作者和 before/after 状态由 Service 填写，不信前端。 */
@Repository
public class AuditLogMapper {
    private final JdbcTemplate db;
    /** Spring 注入 JDBC。 */
    public AuditLogMapper(JdbcTemplate db) { this.db = db; }

    /** INSERT 审计；同一事务中业务状态失败则此行也回滚。 */
    public long insert(long operator, String action, String targetType, long targetId,
                       String before, String after, String reason, LocalDateTime now) {
        return DbIds.insert(db, "INSERT INTO admin_audit_log(operator_id,action,target_type,target_id,before_state,after_state,reason,created_at) VALUES(?,?,?,?,?,?,?,?)",
                operator, action, targetType, targetId, before, after, reason, now);
    }

    /** 后台审计页按最新 id 倒序读取一页。 */
    public List<AuditLogEntity> list(int size, int offset) {
        return db.query("SELECT id,operator_id,action,target_type,target_id,before_state,after_state,reason,created_at FROM admin_audit_log ORDER BY id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new AuditLogEntity(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getTimestamp(9).toLocalDateTime()), size, offset);
    }

    /** COUNT 所有审计记录，供后台分页。 */
    public long count() { return db.queryForObject("SELECT COUNT(*) FROM admin_audit_log", Long.class); }
}
