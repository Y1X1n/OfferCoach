package com.offercoach.interview.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * OCR 标记清理：坐标与引用标记必须被去掉，正文保留。
 */
class OcrTextCleanerTest {

    @Test
    void stripsRefAndBoxMarkers() {
        String raw = "<｜ref｜>张三<｜/ref｜><｜box｜>[[52, 40, 948, 96]]<｜/box｜>\n"
                + "<｜ref｜>高级后端工程师<｜/ref｜><｜box｜>[[60, 120, 700, 160]]<｜/box｜>";

        String cleaned = OcrTextCleaner.clean(raw);

        assertThat(cleaned).contains("张三").contains("高级后端工程师");
        assertThat(cleaned).doesNotContain("<｜ref｜>").doesNotContain("<｜box｜>").doesNotContain("[[");
    }

    @Test
    void keepsPlainTextUntouched() {
        String raw = "教育经历\n某大学 计算机科学与技术";

        assertThat(OcrTextCleaner.clean(raw)).isEqualTo("教育经历\n某大学 计算机科学与技术");
    }

    @Test
    void stripsMarkdownHeadingMarkers() {
        String raw = "## 个人简介\n3 年后端开发经验\n### 技能\n- Java\n- Redis";

        String cleaned = OcrTextCleaner.clean(raw);

        assertThat(cleaned).contains("个人简介").contains("3 年后端开发经验").contains("技能");
        assertThat(cleaned).doesNotContain("##").doesNotContain("###");
        assertThat(cleaned.lines().filter(line -> line.startsWith("- "))).isEmpty();
    }

    @Test
    void stripsAsciiPipeMarkers() {
        String raw = "<|ref|>张三<|/ref|><|box|>[[52, 40, 948, 96]]<|/box|>";

        String cleaned = OcrTextCleaner.clean(raw);

        assertThat(cleaned).isEqualTo("张三");
    }

    @Test
    void handlesEmptyInput() {
        assertThat(OcrTextCleaner.clean(null)).isEmpty();
        assertThat(OcrTextCleaner.clean("")).isEmpty();
    }
}
