package org.example.rebuiltzijingforum.common.security;

/** 统一角色判断，避免每个功能只认识 ADMIN 而漏掉 SUPER_ADMIN。 */
public final class Roles {
    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    private Roles() {}

    public static boolean isAdmin(String role) {
        return ADMIN.equals(role) || SUPER_ADMIN.equals(role);
    }

    public static boolean isSuperAdmin(String role) {
        return SUPER_ADMIN.equals(role);
    }

    public static boolean isPrivileged(String role) {
        return isAdmin(role);
    }
}
