package org.example.rebuiltzijingforum.f04_comment.entity;

import java.time.LocalDateTime;

/** 数据库 comment 的一行；Mapper 负责把 ResultSet 转成本对象，Service 不直接处理 SQL。 */
public record CommentEntity(long id, long postId, long authorId, Long parentId, Long rootId,
                            Long replyUserId, String content, int likeCount, boolean deleted,
                            LocalDateTime createdAt, String imageUrls, LocalDateTime deletedAt) {}
