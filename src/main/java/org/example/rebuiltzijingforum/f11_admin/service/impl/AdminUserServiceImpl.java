package org.example.rebuiltzijingforum.f11_admin.service.impl;

import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f11_admin.dto.PunishmentDTO;
import org.example.rebuiltzijingforum.f11_admin.entity.PunishmentEntity;
import org.example.rebuiltzijingforum.f11_admin.mapper.AdminMapper;
import org.example.rebuiltzijingforum.f11_admin.mapper.PunishmentMapper;
import org.example.rebuiltzijingforum.f11_admin.mapper.AuditLogMapper;
import org.example.rebuiltzijingforum.f11_admin.service.AdminUserService;
import org.example.rebuiltzijingforum.f11_admin.vo.AdminUserVO;
import org.example.rebuiltzijingforum.f08_search.service.SearchSyncService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** F11 用户治理实现：角色、目标、状态转换、处罚和审计都在此。 */
@Service
public class AdminUserServiceImpl implements AdminUserService {
    private final AdminMapper users;
    private final PunishmentMapper punishments;
    private final AuditLogMapper audits;
    private final Clock clock;
    private final ObjectProvider<SearchSyncService> searchSync;

    /** 注入当前用户 SQL、处罚历史 SQL、审计 SQL 和测试可控时钟。 */
    public AdminUserServiceImpl(AdminMapper users, PunishmentMapper punishments, AuditLogMapper audits, Clock clock,
                                ObjectProvider<SearchSyncService> searchSync) {
        this.users = users; this.punishments = punishments; this.audits = audits; this.clock = clock; this.searchSync = searchSync;
    }

    /** 管理员用户列表：验角色/页码 → Mapper COUNT/SELECT → Entity 投影转 VO。 */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserVO> users(CurrentUserDTO actor, String keyword, int page, int size) {
        AdminChecks.admin(actor); AdminChecks.page(page, size);
        String filter = "%" + (keyword == null ? "" : keyword.trim().toLowerCase()
                .replace("!", "!!").replace("%", "!%").replace("_", "!_")) + "%";
        return new PageResponse<>(users.users(filter, size, (page-1)*size).stream().map(this::toVo).toList(),
                users.userCount(filter), page, size);
    }

    @Override
    @Transactional
    public AdminUserVO changeRole(CurrentUserDTO actor, long target, String role) {
        AdminChecks.admin(actor);
        if (!"ADMIN".equals(role) && !"USER".equals(role)) throw AdminChecks.bad("ROLE_INVALID", "权限只能是管理员或普通用户");
        AdminMapper.UserRow user = users.user(target).orElseThrow(() -> AdminChecks.missing("USER_NOT_FOUND", "用户不存在"));
        if (actor.userId() == target && !"ADMIN".equals(role)) throw AdminChecks.bad("ADMIN_SELF", "不能取消自己的管理员权限");
        if ("ADMIN".equals(user.role()) && "USER".equals(role) && users.adminCount() <= 1)
            throw AdminChecks.bad("LAST_ADMIN", "必须保留至少一名管理员");
        if (role.equals(user.role())) return toVo(user);
        LocalDateTime now = LocalDateTime.now(clock);
        if (users.setUserRole(target, user.role(), role, now) != 1)
            throw new BusinessException("USER_CHANGED", "用户角色已变化，请刷新重试", HttpStatus.CONFLICT);
        audits.insert(actor.userId(), "CHANGE_ROLE", "USER", target, user.role(), role, "管理员调整用户权限", now);
        return toVo(users.user(target).orElseThrow());
    }

    /** 禁言/封禁/解除：Service 验状态机 → UPDATE user_account → INSERT 处罚和审计，同事务。 */
    @Override
    @Transactional
    public AdminUserVO changeUser(CurrentUserDTO actor, long target, String action, PunishmentDTO input) {
        AdminChecks.admin(actor); String why = AdminChecks.reason(input.reason());
        if (actor.userId() == target) throw AdminChecks.bad("ADMIN_SELF", "不能处罚自己");
        AdminMapper.UserRow user = users.user(target).orElseThrow(() -> AdminChecks.missing("USER_NOT_FOUND", "用户不存在"));
        if ("ADMIN".equals(user.role())) throw AdminChecks.bad("ADMIN_TARGET", "不能处罚管理员");
        String status = switch (action) {
            case "MUTE" -> "MUTED";
            case "BAN" -> "BANNED";
            case "UNBAN", "UNMUTE" -> "NORMAL";
            default -> throw AdminChecks.bad("ACTION_INVALID", "非法操作");
        };
        LocalDateTime now = LocalDateTime.now(clock);
        if ("MUTE".equals(action) && (input.expiresAt() == null || !input.expiresAt().isAfter(now)))
            throw AdminChecks.bad("EXPIRES_INVALID", "禁言截止时间须晚于现在");
        if ("BANNED".equals(user.status()) && "MUTE".equals(action))
            throw AdminChecks.bad("USER_BANNED", "封禁账号无需禁言");
        if ("UNBAN".equals(action) && !"BANNED".equals(user.status()))
            throw AdminChecks.bad("USER_NOT_BANNED", "账号当前未封禁");
        if ("UNMUTE".equals(action) && !"MUTED".equals(user.status()))
            throw AdminChecks.bad("USER_NOT_MUTED", "账号当前未禁言");
        if (users.setUserStatus(target, user.status(), status, "BAN".equals(action), now) != 1)
            throw new BusinessException("USER_CHANGED", "用户状态已变化，请刷新重试", HttpStatus.CONFLICT);
        punishments.insert(target, actor.userId(), action, why, "MUTE".equals(action) ? input.expiresAt() : null, now);
        audits.insert(actor.userId(), action, "USER", target, user.status(), status, why, now);
        SearchSyncService sync = searchSync.getIfAvailable();
        if (sync != null) sync.userChanged(target);
        return toVo(users.user(target).orElseThrow());
    }

    /** 先验管理员与目标，再从 PunishmentMapper 按 user_id 读取不可变历史。 */
    @Override
    @Transactional(readOnly = true)
    public List<PunishmentEntity> punishments(CurrentUserDTO actor, long target) {
        AdminChecks.admin(actor);
        users.user(target).orElseThrow(() -> AdminChecks.missing("USER_NOT_FOUND", "用户不存在"));
        return punishments.list(target);
    }

    /** 数据库用户行 → 对外管理 VO，避免返回敏感字段。 */
    private AdminUserVO toVo(AdminMapper.UserRow row) { return new AdminUserVO(row.id(), row.account(), row.role(), row.status()); }
}
