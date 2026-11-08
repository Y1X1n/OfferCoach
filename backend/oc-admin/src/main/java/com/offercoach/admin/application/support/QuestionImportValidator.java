package com.offercoach.admin.application.support;

import com.offercoach.admin.application.model.AdminModels.ImportItem;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 题库导入与新建校验：dimension/difficulty 白名单、stem 非空、字段截断与默认值。
 */
public final class QuestionImportValidator {

    public static final int MAX_IMPORT_ITEMS = 500;
    public static final Set<String> DIMENSIONS = Set.of("DEPTH", "PROJECT", "BEHAVIOR", "FIT");
    public static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final String DEFAULT_JOB_FAMILY = "GENERAL";
    private static final String DEFAULT_LANGUAGE = "zh-CN";
    private static final int TITLE_MAX = 200;
    private static final int JOB_FAMILY_MAX = 64;
    private static final int COMPANY_MAX = 120;
    private static final int LICENSE_MAX = 64;
    private static final int LANGUAGE_MAX = 16;
    private static final int DERIVED_TITLE_MAX = 60;

    private QuestionImportValidator() {}

    /**
     * 逐条校验并标准化导入条目；校验失败抛 InvalidQuestionFieldException。
     */
    public static ValidatedItem validateImportItem(ImportItem item) {
        if (item == null) {
            throw new InvalidQuestionFieldException("item", "条目不能为空");
        }
        String stem = requireText("stem", item.stem(), null);
        String dimension = normalizeDimension(item.dimension());
        String difficulty = normalizeDifficulty(item.difficulty());
        String title = optionalText("title", item.title(), TITLE_MAX);
        if (title == null) {
            title = truncate(stem, DERIVED_TITLE_MAX);
        }
        String jobFamily = optionalText("jobFamily", item.jobFamily(), JOB_FAMILY_MAX);
        jobFamily = jobFamily == null ? DEFAULT_JOB_FAMILY : jobFamily.toUpperCase(Locale.ROOT);
        String language = optionalText("language", item.language(), LANGUAGE_MAX);
        return new ValidatedItem(
                title,
                jobFamily,
                dimension,
                difficulty,
                sanitizeTags(item.tags()),
                stem,
                optionalText("referenceAnswer", item.referenceAnswer(), null),
                item.scoringPoints() == null
                        ? List.of()
                        : item.scoringPoints().stream()
                                .filter(value -> value != null)
                                .toList(),
                optionalText("company", item.company(), COMPANY_MAX),
                optionalText("sourceUrl", item.sourceUrl(), null),
                optionalText("sourceLicense", item.sourceLicense(), LICENSE_MAX),
                language == null ? DEFAULT_LANGUAGE : language);
    }

    public static String normalizeDimension(String value) {
        return requireEnum("dimension", value, DIMENSIONS);
    }

    public static String normalizeDifficulty(String value) {
        return requireEnum("difficulty", value, DIFFICULTIES);
    }

    public static String requireText(String field, String value, Integer maxLength) {
        String stripped = value == null ? null : value.strip();
        if (stripped == null || stripped.isEmpty()) {
            throw new InvalidQuestionFieldException(field, "不能为空");
        }
        return truncateRequired(field, stripped, maxLength);
    }

    public static String optionalText(String field, String value, Integer maxLength) {
        String stripped = value == null ? null : value.strip();
        if (stripped == null || stripped.isEmpty()) {
            return null;
        }
        return truncateRequired(field, stripped, maxLength);
    }

    public static List<String> sanitizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String stripped = tag.strip();
            if (!stripped.isEmpty()) {
                unique.add(stripped);
            }
        }
        return new ArrayList<>(unique);
    }

    private static String truncateRequired(String field, String value, Integer maxLength) {
        if (maxLength != null && value.length() > maxLength) {
            throw new InvalidQuestionFieldException(field, "长度不能超过 " + maxLength);
        }
        return value;
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String requireEnum(String field, String value, Set<String> allowed) {
        String normalized = value == null ? null : value.strip().toUpperCase(Locale.ROOT);
        if (normalized == null || !allowed.contains(normalized)) {
            throw new InvalidQuestionFieldException(
                    field, "取值必须为 " + String.join("/", allowed.stream().sorted().toList()));
        }
        return normalized;
    }

    /**
     * 校验失败字段；服务层据此生成逐条错误报告或 BizException。
     */
    public static final class InvalidQuestionFieldException extends RuntimeException {

        private final String field;

        public InvalidQuestionFieldException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    /**
     * 标准化后的导入条目；scoringPoints 由服务序列化为 JSONB。
     */
    public record ValidatedItem(
            String title,
            String jobFamily,
            String dimension,
            String difficulty,
            List<String> tags,
            String stem,
            String referenceAnswer,
            List<Object> scoringPoints,
            String company,
            String sourceUrl,
            String sourceLicense,
            String language) {}
}
