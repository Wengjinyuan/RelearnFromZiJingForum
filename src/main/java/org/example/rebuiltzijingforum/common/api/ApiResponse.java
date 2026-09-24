package org.example.rebuiltzijingforum.common.api;

/**
 * 全站统一响应外壳。业务包只返回自己的 DTO，不直接暴露数据库实体。
 */
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", "success", data);
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}

