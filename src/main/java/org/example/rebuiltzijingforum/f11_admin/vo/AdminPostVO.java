package org.example.rebuiltzijingforum.f11_admin.vo;

/** 后台帖子行，展示审核状态和置顶/精华标记。 */
public record AdminPostVO(long id, long authorId, String title, String status, boolean top, boolean essence) {}
