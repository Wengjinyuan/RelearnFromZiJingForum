package org.example.rebuiltzijingforum.f11_admin.service.impl;

import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.common.security.Roles;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.springframework.http.HttpStatus;

/** 两种管理 Service 共用的基础校验；具体状态转换仍由各自 Impl 决定。 */
final class AdminChecks {
    private AdminChecks() {}

    /** 身份合法不等于有权限：普通用户即使带 JWT 也不能进入管理业务。 */
    static void admin(CurrentUserDTO actor) {
        if (!Roles.isAdmin(actor.role())) throw new BusinessException("ADMIN_FORBIDDEN", "需要管理员权限", HttpStatus.FORBIDDEN);
    }

    /** 只有超级管理员能授予或撤销 ADMIN；普通管理员只能做内容和用户治理。 */
    static void superAdmin(CurrentUserDTO actor) {
        if (!Roles.isSuperAdmin(actor.role()))
            throw new BusinessException("SUPER_ADMIN_FORBIDDEN", "只有超级管理员可以设置或撤销管理员", HttpStatus.FORBIDDEN);
    }

    /** 限制分页大小，避免后台列表一次拉取过多。 */
    static void page(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw bad("PAGE_INVALID", "分页参数错误");
    }

    /** 每次治理动作都要原因，供处罚历史和审计追溯。 */
    static String reason(String input) {
        if (input == null || input.isBlank() || input.length() > 500) throw bad("REASON_INVALID", "原因须为1到500字");
        return input.trim();
    }

    /** 业务参数/状态错误返回 400。 */
    static BusinessException bad(String code, String message) { return new BusinessException(code, message, HttpStatus.BAD_REQUEST); }
    /** 资源不存在返回 404。 */
    static BusinessException missing(String code, String message) { return new BusinessException(code, message, HttpStatus.NOT_FOUND); }
}
