package com.offercoach.admin.application.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offercoach.admin.application.model.AdminModels.ImportItem;
import com.offercoach.admin.application.support.QuestionImportValidator.InvalidQuestionFieldException;
import com.offercoach.admin.application.support.QuestionImportValidator.ValidatedItem;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 题库导入逐条校验：dimension/difficulty 白名单、stem 非空与字段标准化。
 */
class QuestionImportValidatorTest {

    @Test
    void normalizesValidItem() {
        ImportItem item = new ImportItem(
                null,
                " backend ",
                "depth",
                "medium",
                List.of(" Redis ", "Redis", ""),
                "  如何保证缓存一致性？ ",
                " 参考回答 ",
                List.of("延迟双删"),
                " 某公司 ",
                null,
                null,
                null);

        ValidatedItem validated = QuestionImportValidator.validateImportItem(item);

        assertEquals("如何保证缓存一致性？", validated.title());
        assertEquals("BACKEND", validated.jobFamily());
        assertEquals("DEPTH", validated.dimension());
        assertEquals("MEDIUM", validated.difficulty());
        assertEquals(List.of("Redis"), validated.tags());
        assertEquals("如何保证缓存一致性？", validated.stem());
        assertEquals("参考回答", validated.referenceAnswer());
        assertEquals("某公司", validated.company());
        assertEquals("zh-CN", validated.language());
    }

    @Test
    void derivesTitleFromStemWhenBlank() {
        ImportItem item = new ImportItem(
                "   ",
                "BACKEND",
                "PROJECT",
                "EASY",
                List.of(),
                "项目里最有挑战的事情是什么？",
                null,
                null,
                null,
                null,
                null,
                "en-US");

        ValidatedItem validated = QuestionImportValidator.validateImportItem(item);

        assertTrue(validated.title().startsWith("项目里最有挑战"));
        assertEquals("en-US", validated.language());
    }

    @Test
    void rejectsInvalidDimension() {
        InvalidQuestionFieldException exception = assertThrows(
                InvalidQuestionFieldException.class,
                () -> QuestionImportValidator.validateImportItem(item("INVALID", "EASY", "题干")));
        assertEquals("dimension", exception.field());
    }

    @Test
    void rejectsInvalidDifficulty() {
        InvalidQuestionFieldException exception = assertThrows(
                InvalidQuestionFieldException.class,
                () -> QuestionImportValidator.validateImportItem(item("DEPTH", "VERY_HARD", "题干")));
        assertEquals("difficulty", exception.field());
    }

    @Test
    void rejectsBlankStem() {
        InvalidQuestionFieldException exception = assertThrows(
                InvalidQuestionFieldException.class,
                () -> QuestionImportValidator.validateImportItem(item("DEPTH", "EASY", "   ")));
        assertEquals("stem", exception.field());
    }

    @Test
    void rejectsOverlongTitle() {
        InvalidQuestionFieldException exception = assertThrows(
                InvalidQuestionFieldException.class,
                () -> QuestionImportValidator.validateImportItem(new ImportItem(
                        "x".repeat(201),
                        "BACKEND",
                        "DEPTH",
                        "EASY",
                        List.of(),
                        "题干",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));
        assertEquals("title", exception.field());
    }

    @Test
    void capIsFiveHundred() {
        assertEquals(500, QuestionImportValidator.MAX_IMPORT_ITEMS);
    }

    private static ImportItem item(String dimension, String difficulty, String stem) {
        return new ImportItem(
                "标题", "BACKEND", dimension, difficulty, List.of(), stem, null, null, null, null, null, null);
    }
}
