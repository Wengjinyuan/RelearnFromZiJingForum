package org.example.rebuiltzijingforum.f06_follow.vo;

/** 粉丝或关注列表中可公开展示的用户卡片。 */
public record FollowUserVO(long id, String account, String displayName, String avatarUrl,
                           String signature, String major, long followerCount) {}
