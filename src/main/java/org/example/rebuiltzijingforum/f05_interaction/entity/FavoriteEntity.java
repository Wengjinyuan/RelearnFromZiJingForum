package org.example.rebuiltzijingforum.f05_interaction.entity;

/** favorite 表的一行；用户和帖子组合唯一。 */
public record FavoriteEntity(long userId, long postId) {}
