package org.example.rebuiltzijingforum.f08_search.entity;

import java.time.LocalDateTime;

/** ES 用户副本只含公开字段，不索引密码、邮箱或手机号。 */
public record EsUserEntity(long id, String account, String displayName, String signature,
                           LocalDateTime sourceUpdatedAt) {}
