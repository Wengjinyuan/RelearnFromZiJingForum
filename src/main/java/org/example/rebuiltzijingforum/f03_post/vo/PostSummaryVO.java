package org.example.rebuiltzijingforum.f03_post.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 列表输出，不包含富文本正文。 */
public record PostSummaryVO(
        long id,
        long authorId,
        String authorAccount,
        String category,
        String title,
        String summary,
        String coverUrl,
        int viewCount,
        int likeCount,
        int commentCount,
        int favoriteCount,
        boolean top,
        boolean essence,
        List<String> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public PostSummaryVO {
        tags = List.copyOf(tags);
    }
}
