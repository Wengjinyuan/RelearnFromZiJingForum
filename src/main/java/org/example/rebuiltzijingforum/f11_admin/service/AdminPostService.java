package org.example.rebuiltzijingforum.f11_admin.service;

import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f11_admin.dto.PostAuditDTO;
import org.example.rebuiltzijingforum.f11_admin.vo.AdminPostVO;
import org.example.rebuiltzijingforum.f11_admin.vo.AuditLogVO;

/** F11 帖子治理契约；审批、开关、审计由实现类组合 Mapper 完成。 */
public interface AdminPostService {
    /** 查询含待审和已删除的后台帖子列表。 */
    PageResponse<AdminPostVO> posts(CurrentUserDTO actor, String status, int page, int size);
    /** 审批/驳回/删除帖子，并在事务内留审计。 */
    AdminPostVO changeStatus(CurrentUserDTO actor, long postId, String action, PostAuditDTO input);
    /** 置顶/精华开关，并在事务内留审计。 */
    AdminPostVO setFlag(CurrentUserDTO actor, long postId, String flag, PostAuditDTO input);
    /** 审计日志分页查询。 */
    PageResponse<AuditLogVO> audits(CurrentUserDTO actor, int page, int size);
}
