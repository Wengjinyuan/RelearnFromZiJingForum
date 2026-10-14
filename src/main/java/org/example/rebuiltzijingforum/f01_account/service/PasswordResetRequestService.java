package org.example.rebuiltzijingforum.f01_account.service;

import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.common.security.Roles;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** 申请由用户发起；只有管理员能批准，批准时使原有登录令牌失效。 */
@Service
public class PasswordResetRequestService {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;

    public PasswordResetRequestService(JdbcTemplate db, PasswordEncoder passwords) {
        this.db = db; this.passwords = passwords;
    }

    public record RequestRow(long id, long userId, String account, LocalDateTime createdAt) {}

    @Transactional
    public void request(String input) {
        String account = input.trim().toLowerCase(Locale.ROOT);
        List<Long> ids = db.query("SELECT id FROM user_account WHERE account=? OR LOWER(email)=? OR mobile=?",
                (rs, i) -> rs.getLong(1), account, account, account);
        if (ids.isEmpty()) return; // 公开入口不暴露账号是否存在。
        long userId = ids.get(0);
        Integer count = db.queryForObject("SELECT COUNT(*) FROM password_reset_request WHERE user_id=? AND status='PENDING'",
                Integer.class, userId);
        if (count != null && count > 0) return;
        db.update("INSERT INTO password_reset_request(user_id,status,created_at) VALUES(?,'PENDING',?)",
                userId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<RequestRow> pending(CurrentUserDTO actor) {
        requireAdmin(actor);
        return db.query("SELECT r.id,r.user_id,u.account,r.created_at FROM password_reset_request r JOIN user_account u ON u.id=r.user_id WHERE r.status='PENDING' ORDER BY r.id DESC",
                (rs, i) -> new RequestRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getTimestamp(4).toLocalDateTime()));
    }

    @Transactional
    public void approve(CurrentUserDTO actor, long id) {
        requireAdmin(actor);
        List<Long> users = db.query("SELECT user_id FROM password_reset_request WHERE id=? AND status='PENDING'",
                (rs, i) -> rs.getLong(1), id);
        if (users.isEmpty()) throw new BusinessException("REQUEST_NOT_FOUND", "申请不存在或已处理", HttpStatus.NOT_FOUND);
        long userId = users.get(0);
        LocalDateTime now = LocalDateTime.now();
        int changed = db.update("UPDATE password_reset_request SET status='APPROVED',handled_at=?,handled_by=? WHERE id=? AND status='PENDING'",
                now, actor.userId(), id);
        if (changed != 1) throw new BusinessException("REQUEST_CHANGED", "申请已变化，请刷新", HttpStatus.CONFLICT);
        db.update("UPDATE user_account SET password_hash=?,token_version=token_version+1,updated_at=? WHERE id=?",
                passwords.encode("123456"), now, userId);
        db.update("INSERT INTO admin_audit_log(operator_id,action,target_type,target_id,before_state,after_state,reason,created_at) VALUES(?,?,?,?,?,?,?,?)",
                actor.userId(), "RESET_PASSWORD", "USER", userId, "PENDING", "APPROVED", "用户申请重置密码", now);
    }

    private void requireAdmin(CurrentUserDTO actor) {
        if (!Roles.isAdmin(actor.role())) throw new BusinessException("ADMIN_FORBIDDEN", "需要管理员权限", HttpStatus.FORBIDDEN);
    }
}
