package org.example.rebuiltzijingforum.f11_admin.service;

/** 定时处罚处理契约；独立于管理员 HTTP 请求。 */
public interface PunishmentService {
    /** 发帖/评论时发现禁言已到期，可立即解除并写处罚与审计；并发重复调用只成功一次。 */
    boolean expireUserIfDue(long userId);
    /** 扫描到期禁言，条件更新成功后恢复 NORMAL，返回实际解除人数。 */
    int expireDueMutes();
}
