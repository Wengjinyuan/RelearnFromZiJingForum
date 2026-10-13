package org.example.rebuiltzijingforum.f11_admin.service.impl;

import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f11_admin.dto.PostAuditDTO;
import org.example.rebuiltzijingforum.f11_admin.entity.AuditLogEntity;
import org.example.rebuiltzijingforum.f11_admin.mapper.AdminMapper;
import org.example.rebuiltzijingforum.f11_admin.mapper.AuditLogMapper;
import org.example.rebuiltzijingforum.f11_admin.service.AdminPostService;
import org.example.rebuiltzijingforum.f11_admin.vo.AdminPostVO;
import org.example.rebuiltzijingforum.f11_admin.vo.AuditLogVO;
import org.example.rebuiltzijingforum.f08_search.service.SearchSyncService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** F11 帖子治理实现：审核状态机与审计同一数据库事务。 */
@Service
public class AdminPostServiceImpl implements AdminPostService {
    private final AdminMapper posts;
    private final AuditLogMapper audits;
    private final Clock clock;
    private final ObjectProvider<SearchSyncService> searchSync;

    /** 注入帖子 SQL、审计 SQL 和可测试时钟。 */
    public AdminPostServiceImpl(AdminMapper posts, AuditLogMapper audits, Clock clock, ObjectProvider<SearchSyncService> searchSync) {
        this.posts = posts; this.audits = audits; this.clock = clock; this.searchSync = searchSync;
    }

    /** 后台列表：验管理员/状态/页码后查包含待审和已删除的帖子。 */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminPostVO> posts(CurrentUserDTO actor, String status, int page, int size) {
        AdminChecks.admin(actor); AdminChecks.page(page, size);
        String filter = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("", "PENDING", "PUBLISHED", "REJECTED", "DELETED").contains(filter))
            throw AdminChecks.bad("STATUS_INVALID", "非法状态");
        return new PageResponse<>(posts.posts(filter, size, (page-1)*size).stream().map(this::toVo).toList(),
                posts.postCount(filter), page, size);
    }

    /** 审批/驳回/删除：查旧状态 → 验允许转换 → UPDATE post → INSERT audit → VO。 */
    @Override
    @Transactional
    public AdminPostVO changeStatus(CurrentUserDTO actor, long id, String action, PostAuditDTO input) {
        AdminChecks.admin(actor); String why = AdminChecks.reason(input.reason());
        String next = switch (action) {
            case "APPROVE" -> "PUBLISHED";
            case "REJECT" -> "REJECTED";
            case "DELETE" -> "DELETED";
            default -> throw AdminChecks.bad("ACTION_INVALID", "非法操作");
        };
        AdminMapper.PostRow post = posts.post(id).orElseThrow(() -> AdminChecks.missing("POST_NOT_FOUND", "帖子不存在"));
        if ("DELETED".equals(post.status())) throw AdminChecks.bad("POST_DELETED", "已删除帖子不能再次操作");
        if (("APPROVE".equals(action) || "REJECT".equals(action)) && !"PENDING".equals(post.status()))
            throw AdminChecks.bad("POST_NOT_PENDING", "只有待审核帖子可以审批");
        LocalDateTime now = LocalDateTime.now(clock);
        // WHERE id=? AND status=旧状态 是最后一道并发防线；0 行说明别的管理员抢先改了。
        if (posts.setPostStatus(id, post.status(), next, now) != 1)
            throw new BusinessException("POST_CHANGED", "帖子状态已变化，请刷新重试", HttpStatus.CONFLICT);
        if ("DELETE".equals(action)) posts.decrementTagsForPost(id);
        audits.insert(actor.userId(), action, "POST", id, post.status(), next, why, now);
        SearchSyncService sync = searchSync.getIfAvailable();
        if (sync != null) sync.postChanged(id);
        return toVo(posts.post(id).orElseThrow());
    }

    /** 置顶/精华：只允许 PUBLISHED，列名由代码白名单给出而非前端拼 SQL。 */
    @Override
    @Transactional
    public AdminPostVO setFlag(CurrentUserDTO actor, long id, String flag, PostAuditDTO input) {
        AdminChecks.admin(actor); String why = AdminChecks.reason(input.reason());
        if (input.enabled() == null) throw AdminChecks.bad("ENABLED_REQUIRED", "缺少 enabled");
        AdminMapper.PostRow post = posts.post(id).orElseThrow(() -> AdminChecks.missing("POST_NOT_FOUND", "帖子不存在"));
        if (!"PUBLISHED".equals(post.status())) throw AdminChecks.bad("POST_NOT_PUBLISHED", "只能操作已发布帖子");
        String column = switch (flag) {
            case "TOP" -> "is_top";
            case "ESSENCE" -> "is_essence";
            default -> throw AdminChecks.bad("FLAG_INVALID", "非法标记");
        };
        boolean before = "TOP".equals(flag) ? post.top() : post.essence();
        LocalDateTime now = LocalDateTime.now(clock);
        if (posts.setPostFlag(id, column, input.enabled(), now) != 1)
            throw AdminChecks.bad("POST_CHANGED", "帖子状态已变化");
        audits.insert(actor.userId(), flag, "POST", id, String.valueOf(before), String.valueOf(input.enabled()), why, now);
        return toVo(posts.post(id).orElseThrow());
    }

    /** 审计页：只有管理员能看；读出 Entity 再转对外 VO。 */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogVO> audits(CurrentUserDTO actor, int page, int size) {
        AdminChecks.admin(actor); AdminChecks.page(page, size);
        return new PageResponse<>(audits.list(size, (page-1)*size).stream().map(this::toAuditVo).toList(),
                audits.count(), page, size);
    }

    /** 帖子数据库投影 → 对外 VO。 */
    private AdminPostVO toVo(AdminMapper.PostRow row) {
        return new AdminPostVO(row.id(), row.authorId(), row.title(), row.status(), row.top(), row.essence());
    }

    /** 审计 Entity → 对外 VO。 */
    private AuditLogVO toAuditVo(AuditLogEntity row) {
        return new AuditLogVO(row.id(), row.operatorId(), row.action(), row.targetType(), row.targetId(),
                row.beforeState(), row.afterState(), row.reason(), row.createdAt());
    }
}
