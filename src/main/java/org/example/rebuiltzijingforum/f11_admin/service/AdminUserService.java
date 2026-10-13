package org.example.rebuiltzijingforum.f11_admin.service;

import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f11_admin.dto.PunishmentDTO;
import org.example.rebuiltzijingforum.f11_admin.entity.PunishmentEntity;
import org.example.rebuiltzijingforum.f11_admin.vo.AdminUserVO;

import java.util.List;

/** F11 用户治理契约；HTTP 层只知道这些方法，不直接碰数据库。 */
public interface AdminUserService {
    /** 管理员查用户列表。 */
    PageResponse<AdminUserVO> users(CurrentUserDTO actor, String keyword, int page, int size);
    /** 禁言/解禁/封禁/解封；具体状态机与审计在 Impl。 */
    AdminUserVO changeUser(CurrentUserDTO actor, long target, String action, PunishmentDTO input);
    AdminUserVO changeRole(CurrentUserDTO actor, long target, String role);
    /** 查看某用户的不可变处罚历史。 */
    List<PunishmentEntity> punishments(CurrentUserDTO actor, long target);
}
