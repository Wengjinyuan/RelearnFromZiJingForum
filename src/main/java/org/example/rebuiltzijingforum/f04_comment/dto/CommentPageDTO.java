package org.example.rebuiltzijingforum.f04_comment.dto;

/** 浏览器 GET 查询串中的分页和排序；业务层会验证范围，不能直接相信前端。 */
public record CommentPageDTO(int page, int size, String sort) {}
