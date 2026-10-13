package org.example.rebuiltzijingforum.f05_interaction.vo;

/** 帖子详情页初始化点赞/收藏按钮和计数时使用。 */
public record PostInteractionVO(boolean liked, boolean favorited, int views, int likes, int favorites) {}
