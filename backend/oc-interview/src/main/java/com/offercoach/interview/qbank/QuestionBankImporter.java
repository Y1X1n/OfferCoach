package com.offercoach.interview.qbank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.interview.support.JsonCodec;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时从 oc.qbank.import-path 指向的 JSONL 导入题库，按 (source_repo, source_path) 幂等。
 */
@Component
public class QuestionBankImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QuestionBankImporter.class);

    private static final Set<String> DIMENSIONS = Set.of("DEPTH", "PROJECT", "BEHAVIOR", "FIT");
    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final String DEFAULT_REPO = "local";
    private static final String DEFAULT_JOB_FAMILY = "GENERAL";
    private static final String DEFAULT_LANGUAGE = "zh-CN";
    private static final char TAG_SEPARATOR = (char) 31;
    private static final int MAX_ERRORS_PER_REPO = 50;
    /**
     * 非宽松许可数据源：UNLICENSED / CC-BY-NC-SA / CC-BY-NC-ND / GPL-3.0，
     * 公开部署前由 data/question-bank/LICENSES.md 复核后排除。
     */
    private static final Set<String> EXCLUDED_REPOS = Set.of(
            "DerekLin924/bat_code",
            "0voice/Campus_recruitment_interview_questions",
            "ByteByteGoHq/system-design-101",
            "ayush-that/codejeet");

    private final QuestionBankMapper questionBankMapper;
    private final QuestionImportBatchMapper batchMapper;
    private final ObjectMapper objectMapper;
    private final JsonCodec jsonCodec;
    private final String importPath;

    public QuestionBankImporter(
            QuestionBankMapper questionBankMapper,
            QuestionImportBatchMapper batchMapper,
            ObjectMapper objectMapper,
            JsonCodec jsonCodec,
            @Value("${oc.qbank.import-path:}") String importPath) {
        this.questionBankMapper = questionBankMapper;
        this.batchMapper = batchMapper;
        this.objectMapper = objectMapper;
        this.jsonCodec = jsonCodec;
        this.importPath = importPath;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (importPath == null || importPath.isBlank()) {
            log.debug("未配置 oc.qbank.import-path，跳过题库导入");
            return;
        }
        Path path = Paths.get(importPath.trim());
        if (!Files.isRegularFile(path)) {
            log.warn("题库导入文件不存在，跳过：{}", importPath);
            return;
        }

        Map<String, RepoStats> statsByRepo = new LinkedHashMap<>();
        long lineNumber = 0;
        long excluded = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                if (isExcludedLine(line)) {
                    excluded++;
                    continue;
                }
                processLine(line, lineNumber, statsByRepo);
            }
        } catch (IOException exception) {
            log.error("题库导入文件读取失败：{}", exception.getClass().getSimpleName());
            return;
        }

        for (Map.Entry<String, RepoStats> entry : statsByRepo.entrySet()) {
            writeBatch(entry.getKey(), entry.getValue());
        }
        int success =
                statsByRepo.values().stream().mapToInt(stats -> stats.success).sum();
        int skipped =
                statsByRepo.values().stream().mapToInt(stats -> stats.skipped).sum();
        int failed =
                statsByRepo.values().stream().mapToInt(stats -> stats.failed).sum();
        log.info("题库导入完成：repos={} success={} skipped={} failed={}", statsByRepo.size(), success, skipped, failed);
        if (excluded > 0) {
            log.info("题库导入跳过非宽松许可来源 {} 条（见 LICENSES.md）", excluded);
        }
    }

    private boolean isExcludedLine(String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            String repo = text(node, "sourceRepo");
            return repo != null && EXCLUDED_REPOS.contains(repo);
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private void processLine(String line, long lineNumber, Map<String, RepoStats> statsByRepo) {
        JsonNode node = null;
        try {
            node = objectMapper.readTree(line);
        } catch (JsonProcessingException exception) {
            RepoStats stats = statsByRepo.computeIfAbsent(DEFAULT_REPO, key -> new RepoStats());
            stats.total++;
            stats.fail(lineNumber, "invalid json");
            return;
        }

        String repo = defaultIfBlank(text(node, "sourceRepo"), DEFAULT_REPO);
        RepoStats stats = statsByRepo.computeIfAbsent(repo, key -> new RepoStats());
        stats.total++;

        String dimension = upper(text(node, "dimension"));
        String difficulty = upper(text(node, "difficulty"));
        String stem = text(node, "stem");
        if (!DIMENSIONS.contains(dimension)) {
            stats.fail(lineNumber, "invalid dimension");
            return;
        }
        if (!DIFFICULTIES.contains(difficulty)) {
            stats.fail(lineNumber, "invalid difficulty");
            return;
        }
        if (stem == null || stem.isBlank()) {
            stats.fail(lineNumber, "blank stem");
            return;
        }
        String trimmedStem = stem.strip();
        if (trimmedStem.regionMatches(true, 0, "http://", 0, 7)
                || trimmedStem.regionMatches(true, 0, "https://", 0, 8)
                || trimmedStem.length() < 8) {
            stats.fail(lineNumber, "junk stem");
            return;
        }

        try {
            String sourcePath = defaultIfBlank(text(node, "sourcePath"), repo + ":line:" + lineNumber);
            String title = defaultIfBlank(text(node, "title"), truncate(stem.strip(), 60));
            List<String> points = stringList(node.get("referencePoints"));
            List<String> tags = stringList(node.get("tags"));
            String scoringPoints = jsonCodec.write(
                    points.stream().map(point -> Map.of("point", point)).toList());
            int inserted = questionBankMapper.insertImported(
                    truncate(title, 200),
                    truncate(defaultIfBlank(upper(text(node, "jobFamily")), DEFAULT_JOB_FAMILY), 64),
                    dimension,
                    difficulty,
                    String.join(String.valueOf(TAG_SEPARATOR), tags),
                    stem.strip(),
                    text(node, "referenceAnswer"),
                    scoringPoints,
                    truncate(repo, 160),
                    sourcePath,
                    text(node, "sourceUrl"),
                    truncate(text(node, "sourceLicense"), 64),
                    truncate(text(node, "company"), 120),
                    truncate(defaultIfBlank(text(node, "language"), DEFAULT_LANGUAGE), 16));
            if (inserted > 0) {
                stats.success++;
            } else {
                stats.skipped++;
            }
        } catch (RuntimeException exception) {
            stats.fail(lineNumber, exception.getClass().getSimpleName());
        }
    }

    private void writeBatch(String repo, RepoStats stats) {
        try {
            batchMapper.insertBatch(
                    repo, stats.total, stats.success, stats.failed, stats.skipped, jsonCodec.write(stats.errors));
        } catch (RuntimeException exception) {
            log.warn("题库导入批次写入失败：repo={} reason={}", repo, exception.getClass().getSimpleName());
        }
    }

    private static List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String value = item.isTextual() ? item.asText().strip() : "";
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return new ArrayList<>(values);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static final class RepoStats {
        private int total;
        private int success;
        private int skipped;
        private int failed;
        private final List<Map<String, Object>> errors = new ArrayList<>();

        private void fail(long lineNumber, String reason) {
            failed++;
            if (errors.size() < MAX_ERRORS_PER_REPO) {
                errors.add(Map.of("line", lineNumber, "reason", reason));
            }
        }
    }
}
