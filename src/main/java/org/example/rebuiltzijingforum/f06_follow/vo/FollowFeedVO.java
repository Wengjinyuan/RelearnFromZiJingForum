package org.example.rebuiltzijingforum.f06_follow.vo;

import java.time.LocalDateTime;

/** 关注流中的帖子摘要；正文仍去 F03 详情接口读取。 */
public record FollowFeedVO(long id, long authorId, String title, String summary, LocalDateTime createdAt) {}
