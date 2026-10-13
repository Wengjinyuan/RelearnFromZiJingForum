package org.example.rebuiltzijingforum.f09_home.vo;

/** 一个热门标签；used 是当前公开帖里使用它的帖子数。 */
public record HotTagVO(String name, long used) {}
