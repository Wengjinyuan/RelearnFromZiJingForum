package org.example.rebuiltzijingforum.common.security;

import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import org.example.rebuiltzijingforum.f11_admin.service.PunishmentService;

/** F11 禁言规则的共用校验点：F03 发帖和 F04 评论都从这里判断能否发布。 */
@Component
public class PostingPermission {
    private final JdbcTemplate db;
    private final Clock clock;
    private final PunishmentService punishments;

    /** Spring 注入数据库对象和时间源。 */
    public PostingPermission(JdbcTemplate db, Clock clock, PunishmentService punishments) {
        this.db = db; this.clock = clock; this.punishments = punishments;
    }

    /** 查询账号状态；过期禁言恢复 NORMAL，未过期禁言直接返回 403。 */
    public void requireCanPost(long userId) {
        String status = db.query("SELECT status FROM user_account WHERE id=?", rs -> rs.next() ? rs.getString(1) : null, userId);
        if (status == null) throw new BusinessException("USER_NOT_FOUND", "用户不存在", HttpStatus.UNAUTHORIZED);
        if (!"MUTED".equals(status)) return;
        LocalDateTime expires = db.query("SELECT expires_at FROM user_punishment WHERE user_id=? AND action='MUTE' ORDER BY id DESC LIMIT 1",
                rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toLocalDateTime() : null, userId);
        if (expires == null || expires.isAfter(LocalDateTime.now(clock)))
            throw new BusinessException("USER_MUTED", "账号处于禁言期", HttpStatus.FORBIDDEN);
        // 不能在这里裸 UPDATE：F11 的处罚历史和审计必须与状态变化一起提交。
        if (!punishments.expireUserIfDue(userId)) {
            String latest = db.queryForObject("SELECT status FROM user_account WHERE id=?", String.class, userId);
            if (!"NORMAL".equals(latest)) throw new BusinessException("USER_MUTED", "账号处于禁言期", HttpStatus.FORBIDDEN);
        }
    }
}
