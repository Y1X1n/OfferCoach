package com.offercoach.interview.document;

import java.util.regex.Pattern;

/**
 * OCR 结果清理：去掉坐标、引用与 Markdown 标题标记，只保留纯文本。
 *
 * <p>DeepSeek OCR 逐页输出形如
 * {@code <｜ref｜>标题<｜/ref｜><｜box｜>[[52,40,948,96]]<｜/box｜>}，
 * 部分场景直接输出 Markdown（{@code ## 个人简介}）。坐标不入库，标题符号会干扰
 * 启发式解析器的分段识别，因此一并去掉。</p>
 */
public final class OcrTextCleaner {

    private static final Pattern REF_TAG = Pattern.compile("<[｜|]/?ref[｜|]>");
    private static final Pattern BOX_WITH_COORDS =
            Pattern.compile("<[｜|]box[｜|]>\\s*\\[\\[[^\\]]*]]\\s*<[｜|]/box[｜|]>");
    private static final Pattern BOX_BARE = Pattern.compile("<[｜|]/?box[｜|]>");
    private static final Pattern LEADING_HASHES = Pattern.compile("(?m)^#{1,6}\\s*");
    private static final Pattern BOLD_MARKERS = Pattern.compile("\\*\\*([^*\\n]+)\\*\\*");
    private static final Pattern LIST_MARKERS = Pattern.compile("(?m)^\\s*[-*+]\\s+");

    private OcrTextCleaner() {}

    /**
     * 逐页文本清理：去标记、去标题符号，保留正文与换行结构。
     */
    public static String clean(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = BOX_WITH_COORDS.matcher(raw).replaceAll(" ");
        text = BOX_BARE.matcher(text).replaceAll(" ");
        text = REF_TAG.matcher(text).replaceAll("");
        text = LEADING_HASHES.matcher(text).replaceAll("");
        text = BOLD_MARKERS.matcher(text).replaceAll("$1");
        text = LIST_MARKERS.matcher(text).replaceAll("");
        return DocumentTextExtractor.clean(text);
    }
}
