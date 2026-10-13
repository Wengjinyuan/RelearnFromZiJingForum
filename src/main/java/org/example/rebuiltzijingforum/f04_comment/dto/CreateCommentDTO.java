package org.example.rebuiltzijingforum.f04_comment.dto;

/** 浏览器 POST 的 JSON。postId 从 URL 取、authorId 从 JWT 取，所以这里都不能由前端填写。 */
import java.util.List;

public record CreateCommentDTO(String content, Long parentId, List<String> imageUrls) {}
