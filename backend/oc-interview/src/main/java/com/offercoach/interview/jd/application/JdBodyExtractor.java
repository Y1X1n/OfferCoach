package com.offercoach.interview.jd.application;

import java.util.List;

/**
 * 网页正文的 JD 抽取与"是否抓到 JD"判定。
 *
 * <p>招聘页常见噪声：顶部导航、面包屑、页脚、推荐位。本类按 JD 段落标题切出正文起点，
 * 并给出 {@code looksLikeJd} 判定，供编排层决定是否走搜索兜底（见 ai-jd-web-prd.md §4.3）。</p>
 */
public final class JdBodyExtractor {

    /** 与 {@code JdService.MAX_RAW_TEXT} 一致：JD 原文上限。 */
    public static final int MAX_LENGTH = 8000;

    /** 低于此长度视为"没抓到正文"。 */
    public static final int MIN_USEFUL_LENGTH = 300;

    private static final List<String> SECTION_KEYWORDS = List.of(
            "岗位职责", "工作职责", "职位描述", "主要职责", "职责描述", "工作内容", "任职要求", "任职资格", "任职条件", "岗位要求", "职位要求", "资格要求", "能力要求",
            "技能要求", "我们希望你", "岗位描述", "你将负责");

    private static final List<String> JD_SIGNALS =
            List.of("招聘", "职位", "岗位", "任职", "职责", "薪资", "薪酬", "工作地点", "学历", "经验", "福利");

    private JdBodyExtractor() {}

    /**
     * 是否像一份 JD：长度达标，且（含 JD 段落标题 或 招聘信号词 ≥ 2）。
     */
    public static boolean looksLikeJd(String cleaned) {
        if (cleaned == null) {
            return false;
        }
        String text = cleaned.strip();
        if (text.length() < MIN_USEFUL_LENGTH) {
            return false;
        }
        if (hasSectionKeyword(text)) {
            return true;
        }
        long hits = JD_SIGNALS.stream().filter(text::contains).count();
        return hits >= 2;
    }

    /**
     * 抽取 JD 正文：从最早的 JD 段落标题处起（丢弃前置导航），并截断到上限。
     */
    public static String extract(String cleaned) {
        if (cleaned == null || cleaned.isBlank()) {
            return "";
        }
        String text = cleaned.strip();
        int cut = earliestSectionIndex(text);
        if (cut > 0) {
            text = text.substring(cut).strip();
        }
        if (text.length() > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH).strip();
        }
        return text;
    }

    /**
     * 页面标题兜底：Search / Fetch 的 title 可能为空，此时用正文首行。
     */
    public static String firstNonBlankLine(String cleaned, int maxLength) {
        if (cleaned == null) {
            return "";
        }
        for (String line : cleaned.split("\n", -1)) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
            }
        }
        return "";
    }

    private static boolean hasSectionKeyword(String text) {
        return SECTION_KEYWORDS.stream().anyMatch(text::contains);
    }

    private static int earliestSectionIndex(String text) {
        int earliest = -1;
        for (String keyword : SECTION_KEYWORDS) {
            int index = text.indexOf(keyword);
            if (index > 0 && (earliest < 0 || index < earliest)) {
                earliest = index;
            }
        }
        return earliest;
    }
}
