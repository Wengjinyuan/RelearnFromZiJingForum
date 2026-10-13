package org.example.rebuiltzijingforum.f05_interaction.entity;

/** like_record 表的一行；三列共同构成“某用户对某目标的点赞”。 */
public record LikeEntity(long userId, String targetType, long targetId) {}
