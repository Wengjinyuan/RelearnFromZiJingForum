package org.example.rebuiltzijingforum.f06_follow.vo;

/** 当前用户是否关注目标，以及目标现在的粉丝数。 */
public record FollowStatusVO(boolean following, long followerCount) {}
