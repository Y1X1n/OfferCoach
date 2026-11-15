package com.offercoach.interview.jd.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 联网获取的纯函数：兜底检索词推导与岗位标题择优。
 */
class JdWebHelpersTest {

    @Test
    void fallbackQueryPrefersUserKeywords() {
        assertThat(JdWebImportService.fallbackQuery("https://www.zhipin.com/job/1", "高级后端工程师 上海"))
                .isEqualTo("高级后端工程师 上海");
    }

    @Test
    void fallbackQueryUsesSiteAndSlugWhenNoKeywords() {
        String query =
                JdWebImportService.fallbackQuery("https://www.zhipin.com/zhaopin/abc-%E9%AB%98%E7%BA%A7-123", null);

        assertThat(query).startsWith("site:www.zhipin.com").contains("招聘");
    }

    @Test
    void fallbackQueryFallsBackToSiteOnlyForNumericPath() {
        assertThat(JdWebImportService.fallbackQuery("https://jobs.example.com/123456", "  "))
                .isEqualTo("site:jobs.example.com 招聘");
    }

    @Test
    void preferJobTitleUsesStructuredTitleWhenPresent() {
        assertThat(JdPreviewStoreService.preferJobTitle("高级后端工程师", "某某招聘系统--招聘明细"))
                .isEqualTo("高级后端工程师");
    }

    @Test
    void preferJobTitleFallsBackToPageTitleWhenBlankOrTooLong() {
        assertThat(JdPreviewStoreService.preferJobTitle("  ", "页面标题")).isEqualTo("页面标题");
        assertThat(JdPreviewStoreService.preferJobTitle("x".repeat(200), "页面标题"))
                .isEqualTo("页面标题");
    }
}
