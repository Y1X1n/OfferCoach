package com.offercoach.admin.application.support;

/**
 * 管理端分页参数：页码从 1 开始，size 收敛到 1-100。
 */
public record PageQuery(int page, int size, long offset) {

    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 100;

    public static PageQuery of(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, MIN_SIZE), MAX_SIZE);
        return new PageQuery(safePage, safeSize, (long) (safePage - 1) * safeSize);
    }
}
