package org.example.rebuiltzijingforum.f08_search.vo;

import java.time.LocalDateTime;

/** 帖子搜索结果；不把富文本正文直接返回搜索页。 */
public record PostSearchVO(long id, String title, String summary, long authorId, LocalDateTime createdAt,
                           String titleHighlight, String summaryHighlight) {}
