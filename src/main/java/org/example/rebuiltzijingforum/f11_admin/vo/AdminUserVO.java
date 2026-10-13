package org.example.rebuiltzijingforum.f11_admin.vo;

/** 后台用户列表/操作结果，不暴露密码哈希、邮箱或手机号。 */
public record AdminUserVO(long id, String account, String role, String status) {}
