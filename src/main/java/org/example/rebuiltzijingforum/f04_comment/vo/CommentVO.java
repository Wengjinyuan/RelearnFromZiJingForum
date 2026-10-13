package org.example.rebuiltzijingforum.f04_comment.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 返回给网页的评论，不含数据库内部字段；删除后 content 只显示占位文字。 */
public record CommentVO(long id, long postId, long authorId, String authorName, Long parentId, Long rootId,
                        Long replyUserId, String replyUserName, String content, int likeCount, boolean deleted,
                        LocalDateTime createdAt, List<String> imageUrls, List<CommentVO> replies) {}
