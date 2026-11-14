package com.offercoach.interview.document;

import java.util.regex.Pattern;

/**
 * 网页正文清理：Markdown / 富文本 → 纯文本。
 *
 * <p>招聘页正文多为 Markdown（标题、加粗、列表、链接、图片），结构化与出题只需要文字本身。
 * 与 [OcrTextCleaner] 的区别：后者面向 OCR 的坐标标记，本类面向网页的 Markdown 语法。</p>
 */
public final class WebContentCleaner {

    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]{1,200}>");
    private static final Pattern CODE_FENCE = Pattern.compile("(?m)^\\s*```.*$");
    private static final Pattern BLOCK_QUOTE = Pattern.compile("(?m)^\\s*>\\s?");
    private static final Pattern HEADING_MARK = Pattern.compile("(?m)^#{1,6}\\s*");
    private static final Pattern SETEXT_UNDERLINE = Pattern.compile("(?m)^\\s*[=-]{3,}\\s*$");
    private static final Pattern BOLD_ITALIC = Pattern.compile("(\\*\\*|__|\\*|_)([^*_\\n]+)\\1");
    private static final Pattern LIST_MARKER = Pattern.compile("(?m)^\\s*(?:[-*+]|\\d{1,3}[.、)])\\s+");
    private static final Pattern TABLE_DIVIDER = Pattern.compile("(?m)^\\s*\\|?[\\s:|-]{5,}\\|?\\s*$");
    private static final Pattern TABLE_PIPE = Pattern.compile("(?m)^\\s*\\|");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t\\u00A0\\u3000]{2,}");
    private static final Pattern HR = Pattern.compile("(?m)^\\s*(?:[-*_]\\s*){3,}$");

    private WebContentCleaner() {}

    /**
     * 清理为纯文本；保留段落换行结构，去掉 Markdown 与 HTML 语法噪声。
     */
    public static String clean(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = CODE_FENCE.matcher(raw).replaceAll("");
        text = IMAGE.matcher(text).replaceAll(" ");
        text = LINK.matcher(text).replaceAll("$1");
        text = HTML_TAG.matcher(text).replaceAll(" ");
        text = BLOCK_QUOTE.matcher(text).replaceAll("");
        text = HEADING_MARK.matcher(text).replaceAll("");
        text = SETEXT_UNDERLINE.matcher(text).replaceAll("");
        text = HR.matcher(text).replaceAll("");
        text = TABLE_DIVIDER.matcher(text).replaceAll("");
        text = TABLE_PIPE.matcher(text).replaceAll("");
        text = LIST_MARKER.matcher(text).replaceAll("");
        text = BOLD_ITALIC.matcher(text).replaceAll("$2");
        text = INLINE_CODE.matcher(text).replaceAll("$1");
        text = MULTI_SPACE.matcher(text).replaceAll(" ");
        return DocumentTextExtractor.clean(text);
    }
}
