package org.example.rebuiltzijingforum.common.api;

import java.util.List;

public record PageResponse<T>(List<T> items, long total, int page, int size) {

    public PageResponse {
        items = List.copyOf(items);
        if (total < 0 || page < 1 || size < 1) {
            throw new IllegalArgumentException("total、page、size 不能为非法值");
        }
    }
}

