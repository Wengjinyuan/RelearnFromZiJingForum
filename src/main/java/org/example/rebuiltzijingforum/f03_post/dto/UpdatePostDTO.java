package org.example.rebuiltzijingforum.f03_post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 作者编辑帖子时可修改的白名单字段。 */
public record UpdatePostDTO(
        @NotBlank(message = "分类不能为空")
        @Size(max = 32, message = "分类不能超过32个字符")
        String category,
        @NotBlank(message = "标题不能为空")
        @Size(max = 120, message = "标题不能超过120个字符")
        String title,
        @NotBlank(message = "正文不能为空")
        @Size(max = 30000, message = "正文不能超过30000个字符")
        String content,
        @Size(max = 10, message = "最多选择10个标签")
        List<@NotBlank(message = "标签不能为空") @Size(max = 20, message = "单个标签不能超过20个字符") String> tags
) {
    public UpdatePostDTO {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
