package org.example.rebuiltzijingforum.f06_follow.entity;

import java.time.LocalDateTime;

/** follow_relation 表的一行；followerId → followedId，方向不能颠倒。 */
public record FollowEntity(long followerId, long followedId, LocalDateTime createdAt) {}
