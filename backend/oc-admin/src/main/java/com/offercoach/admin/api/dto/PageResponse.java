package com.offercoach.admin.api.dto;

import java.util.List;

/**
 * 管理端统一分页响应。
 */
public record PageResponse<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        return new PageResponse<>(items, total, page, size);
    }
}
