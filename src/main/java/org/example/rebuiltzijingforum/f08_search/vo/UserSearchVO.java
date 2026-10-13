package org.example.rebuiltzijingforum.f08_search.vo;

/** 用户搜索结果只含公开资料，不含邮箱/手机号。 */
public record UserSearchVO(long id, String account, String displayName, String signature,
                           String accountHighlight, String displayNameHighlight) {}
