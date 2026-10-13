package org.example.rebuiltzijingforum.f03_post.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 帖子详情输出，正文已经过服务端白名单清洗。 */
public record PostDetailVO(
        long id,
        long authorId,
        String authorAccount,
        String category,
        String title,
        String content,
        String summary,
        String coverUrl,
        String status,
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
    public PostDetailVO {
        tags = List.copyOf(tags);
    }
}
