package org.example.rebuiltzijingforum.f03_post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** tag 表实体。 */
@Data
@TableName("tag")
public class TagEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer useCount;
    private LocalDateTime createdAt;
}
