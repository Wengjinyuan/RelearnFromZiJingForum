package org.example.rebuiltzijingforum.f09_home.vo;

import java.time.LocalDateTime;

/** 首页统计卡片；calculatedAt 告诉前端这个值是何时计算的。 */
public record HomeStatisticsVO(long users, long posts, long todayPosts, long yesterdayPosts, LocalDateTime calculatedAt) {}
