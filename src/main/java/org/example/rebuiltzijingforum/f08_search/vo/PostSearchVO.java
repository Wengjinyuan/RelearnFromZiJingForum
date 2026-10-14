package org.example.rebuiltzijingforum.f08_search.vo;

import java.time.LocalDateTime;

/** 帖子搜索结果；包含列表展示字段，不把富文本正文直接返回搜索页。 */
public record PostSearchVO(long id, String title, String summary, long authorId, String authorName,
                           String category, int viewCount, int likeCount, int commentCount,
                           int favoriteCount, boolean essence, LocalDateTime createdAt,
                           String titleHighlight, String summaryHighlight) {}
