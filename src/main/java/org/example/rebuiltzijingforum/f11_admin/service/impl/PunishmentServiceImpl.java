package org.example.rebuiltzijingforum.f11_admin.service.impl;

import org.example.rebuiltzijingforum.f11_admin.mapper.PunishmentMapper;
import org.example.rebuiltzijingforum.f11_admin.mapper.AuditLogMapper;
import org.example.rebuiltzijingforum.f11_admin.service.PunishmentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** F11 到期处理：每轮最多 100 人，失败下轮重试，数据库状态条件避免重复解除。 */
@Service
public class PunishmentServiceImpl implements PunishmentService {
    private final PunishmentMapper punishments;
    private final AuditLogMapper audits;
    private final Clock clock;

    /** 注入处罚 SQL、审计 SQL 和时间源。 */
    public PunishmentServiceImpl(PunishmentMapper punishments, AuditLogMapper audits, Clock clock) {
        this.punishments = punishments; this.audits = audits; this.clock = clock;
    }

    /** 定时扫描 → 找最新到期 MUTE → 条件 UPDATE → INSERT 解除历史和系统审计。 */
    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${forum.admin.mute-scan-delay-ms:60000}",
            initialDelayString = "${forum.admin.mute-scan-initial-delay-ms:60000}")
    public int expireDueMutes() {
        LocalDateTime now = LocalDateTime.now(clock);
        int changed = 0;
        for (long userId : punishments.dueMuteIds(now, 100)) {
            if (expireUserIfDue(userId)) changed++;
        }
        return changed;
    }

    /** 发帖校验/定时任务共用：条件 UPDATE 成功才追加一条解除记录和审计。 */
    @Override
    @Transactional
    public boolean expireUserIfDue(long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (punishments.clearExpiredMute(userId, now) != 1) return false;
        punishments.insert(userId, 0, "EXPIRE_MUTE", "禁言期限已到", null, now);
        audits.insert(0, "EXPIRE_MUTE", "USER", userId, "MUTED", "NORMAL", "禁言期限已到", now);
        return true;
    }
}
