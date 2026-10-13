package org.example.rebuiltzijingforum.common.api;

import java.util.List;

/**
 *
 * @param items 当前页数据列表
 * @param total 数据总条数
 * @param page  当前页码
 * @param size  每页显示的数据条数
 * @param <T>
 *       只用于创建评论分页 就这一个作用
 */
public record PageResponse<T>(List<T> items, long total, int page, int size) {

    public PageResponse {
        items = List.copyOf(items);
        if (total < 0 || page < 1 || size < 1) {
            throw new IllegalArgumentException("total、page、size 不能为非法值");
        }
    }
}

