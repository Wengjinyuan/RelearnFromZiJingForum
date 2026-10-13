package org.example.rebuiltzijingforum.f08_search.entity;

import java.time.LocalDateTime;
import java.util.List;

/** ES 帖子副本；原始事实仍在 post/post_tag，版本时间来自 post.updated_at。 */
public record EsPostEntity(long id, String title, String summary, long authorId, List<String> tags,
                           LocalDateTime createdAt, LocalDateTime sourceUpdatedAt) {}
