package org.example.rebuiltzijingforum.f03_post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** post 表实体。 */
@Data
@TableName("post")
public class PostEntity {
    @TableId(type = IdType.AUTO)
    private Long id;//帖子ID
    private Long authorId;
    private String category;
    private String title;
    private String content;
    private String summary;
    private String coverUrl;
    private String status;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;
    private Boolean isTop;
    private Boolean isEssence;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
