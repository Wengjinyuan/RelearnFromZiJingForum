package org.example.rebuiltzijingforum.f06_follow.dto;

/** 关注列表/关注流分页输入；ServiceImpl 负责统一校验 page 和 size。 */
public record FollowPageDTO(int page, int size) {}
