package com.offercoach.interview.api.dto;

import java.util.List;

/**
 * 统一分页响应：items/total/page/size。
 */
public record PageResponse<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        return new PageResponse<>(items, total, page, size);
    }
}
