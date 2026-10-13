package org.example.rebuiltzijingforum.f03_post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** post_tag 多对多关系实体，数据库使用 (post_id, tag_id) 组合唯一约束。 */
@Data
@TableName("post_tag")
public class PostTagEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long tagId;
}
