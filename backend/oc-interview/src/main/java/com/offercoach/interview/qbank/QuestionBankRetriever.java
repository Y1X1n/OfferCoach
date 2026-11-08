package com.offercoach.interview.qbank;

import com.fasterxml.jackson.core.type.TypeReference;
import com.offercoach.interview.support.JsonCodec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 题库检索实现：同岗位族优先，按关键词 / tag / 字符 bigram 相似度在 Java 侧打分。
 */
@Component
public class QuestionBankRetriever implements QuestionRetriever {

    private static final int CANDIDATE_LIMIT = 200;
    private static final int SUPPLEMENT_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 50;
    private static final Set<String> TECHNICAL_FAMILIES = Set.of("后端", "前端", "AI-Agent", "数据", "算法", "系统设计");
    private static final List<String> DIMENSION_ORDER = List.of("DEPTH", "PROJECT", "BEHAVIOR", "FIT");
    private static final Comparator<Scored> BY_SCORE = Comparator.comparingDouble(Scored::score)
            .reversed()
            .thenComparingLong(
                    item -> item.row().getId() == null ? 0 : item.row().getId());
    private static final char TAG_SEPARATOR = (char) 31;
    private static final TypeReference<List<Object>> OBJECT_LIST = new TypeReference<>() {};

    private final QuestionBankMapper questionBankMapper;
    private final JsonCodec jsonCodec;

    public QuestionBankRetriever(QuestionBankMapper questionBankMapper, JsonCodec jsonCodec) {
        this.questionBankMapper = questionBankMapper;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public List<RetrievedQuestion> retrieve(RetrieveQuery query) {
        if (query == null) {
            return List.of();
        }
        int limit = query.limit() <= 0 ? DEFAULT_LIMIT : Math.min(query.limit(), MAX_LIMIT);
        String family = JobFamilyClassifier.normalize(query.jobFamily());
        List<String> keywords = normalizeKeywords(query.keywords());

        List<QuestionBankDO> candidates = collectCandidates(family);
        if (candidates.isEmpty()) {
            return List.of();
        }

        String queryText = String.join(" ", keywords) + " " + family;
        List<Scored> scored = new ArrayList<>();
        for (QuestionBankDO row : candidates) {
            if (isJunk(row)) {
                continue;
            }
            scored.add(new Scored(row, score(row, family, keywords, queryText)));
        }
        scored.sort(BY_SCORE);
        return balancedSelection(scored, limit).stream()
                .map(item -> toRetrieved(item.row(), item.score()))
                .toList();
    }

    /**
     * 每个维度保留基础名额，避免 top N 被 DEPTH 题目占满，导致 PROJECT/BEHAVIOR/FIT 只能走模板。
     */
    private static List<Scored> balancedSelection(List<Scored> scored, int limit) {
        int perDimension = Math.max(3, limit / 6);
        Set<Long> used = new HashSet<>();
        List<Scored> selected = new ArrayList<>();
        for (String dimension : DIMENSION_ORDER) {
            int taken = 0;
            for (Scored item : scored) {
                if (taken >= perDimension) {
                    break;
                }
                Long id = item.row().getId();
                if (dimension.equals(item.row().getDimension()) && id != null && used.add(id)) {
                    selected.add(item);
                    taken++;
                }
            }
        }
        for (Scored item : scored) {
            if (selected.size() >= limit) {
                break;
            }
            Long id = item.row().getId();
            if (id != null && used.add(id)) {
                selected.add(item);
            }
        }
        selected.sort(BY_SCORE);
        return selected.stream().limit(limit).toList();
    }

    private List<QuestionBankDO> collectCandidates(String family) {
        Map<Long, QuestionBankDO> merged = new LinkedHashMap<>();
        merge(merged, questionBankMapper.selectPublishedByFamily(family, CANDIDATE_LIMIT));
        if (!JobFamilyClassifier.DEFAULT_FAMILY.equals(family)) {
            merge(
                    merged,
                    questionBankMapper.selectPublishedByFamily(JobFamilyClassifier.DEFAULT_FAMILY, SUPPLEMENT_LIMIT));
        }
        if (TECHNICAL_FAMILIES.contains(family) && !"系统设计".equals(family)) {
            merge(merged, questionBankMapper.selectPublishedByFamily("系统设计", SUPPLEMENT_LIMIT));
        }
        if (merged.isEmpty()) {
            merge(merged, questionBankMapper.selectPublished(CANDIDATE_LIMIT));
        }
        return new ArrayList<>(merged.values());
    }

    private static void merge(Map<Long, QuestionBankDO> target, List<QuestionBankDO> rows) {
        for (QuestionBankDO row : rows) {
            if (row.getId() != null) {
                target.putIfAbsent(row.getId(), row);
            }
        }
    }

    private static boolean isJunk(QuestionBankDO row) {
        String stem = row.getStem() == null ? "" : row.getStem().strip();
        return stem.isEmpty()
                || stem.regionMatches(true, 0, "http://", 0, 7)
                || stem.regionMatches(true, 0, "https://", 0, 8);
    }

    private static double score(QuestionBankDO row, String family, List<String> keywords, String queryText) {
        List<String> tags = tagsOf(row.getTagsJoined());
        String title = row.getTitle() == null ? "" : row.getTitle().toLowerCase(Locale.ROOT);
        String stem = row.getStem() == null ? "" : row.getStem().toLowerCase(Locale.ROOT);
        List<String> lowerTags =
                tags.stream().map(tag -> tag.toLowerCase(Locale.ROOT)).toList();

        double score = 0;
        for (String keyword : keywords) {
            if (title.contains(keyword)) {
                score += 2.0;
            }
            if (stem.contains(keyword)) {
                score += 1.5;
            }
            for (String tag : lowerTags) {
                if (tag.equals(keyword)) {
                    score += 1.5;
                } else if (tag.contains(keyword)) {
                    score += 0.8;
                }
            }
        }
        String document = title + " " + stem + " " + String.join(" ", lowerTags);
        score += 2.5 * similarity(queryText, document);
        String rowFamily = JobFamilyClassifier.normalize(row.getJobFamily());
        if (family.equals(rowFamily)) {
            score += 1.5;
        } else if (JobFamilyClassifier.DEFAULT_FAMILY.equals(rowFamily)) {
            score += 0.2;
        } else if ("系统设计".equals(rowFamily) && TECHNICAL_FAMILIES.contains(family)) {
            score += 0.3;
        } else {
            score -= 1.0;
        }
        if (row.getReferenceAnswer() != null && !row.getReferenceAnswer().isBlank()) {
            score += 0.8;
        }
        if (row.getScoringPoints() != null && !row.getScoringPoints().isBlank()) {
            score += 0.2;
        }
        if (row.getQualityScore() != null) {
            score += row.getQualityScore().doubleValue() * 0.5;
        }
        return Math.round(score * 1000.0) / 1000.0;
    }

    private RetrievedQuestion toRetrieved(QuestionBankDO row, double score) {
        return new RetrievedQuestion(
                row.getId() == null ? 0 : row.getId(),
                row.getTitle(),
                row.getStem(),
                row.getDimension(),
                row.getDifficulty(),
                row.getJobFamily(),
                tagsOf(row.getTagsJoined()),
                referencePoints(row.getScoringPoints()),
                score,
                row.getSourceRepo(),
                row.getCompany());
    }

    private List<String> referencePoints(String scoringPointsJson) {
        if (scoringPointsJson == null || scoringPointsJson.isBlank()) {
            return List.of();
        }
        try {
            List<Object> points = jsonCodec.read(scoringPointsJson, OBJECT_LIST);
            if (points == null) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (Object point : points) {
                if (point instanceof String text && !text.isBlank()) {
                    result.add(text.strip());
                } else if (point instanceof Map<?, ?> map && map.get("point") instanceof String text) {
                    result.add(text.strip());
                }
                if (result.size() >= 8) {
                    break;
                }
            }
            return result;
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }

    private static List<String> tagsOf(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(String.valueOf(TAG_SEPARATOR)))
                .map(String::strip)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
    }

    private static List<String> normalizeKeywords(Set<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank()) {
                result.add(keyword.strip().toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 字符 bigram Jaccard 相似度，近似 pg_trgm 排序效果，避免额外依赖。
     */
    private static double similarity(String first, String second) {
        Set<String> left = bigrams(first);
        Set<String> right = bigrams(second);
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> bigrams(String text) {
        if (text == null) {
            return Set.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}，。；：、“”（）【】]+", "");
        Set<String> grams = new LinkedHashSet<>();
        for (int index = 0; index + 1 < normalized.length(); index++) {
            grams.add(normalized.substring(index, index + 2));
        }
        if (grams.isEmpty() && !normalized.isEmpty()) {
            grams.add(normalized);
        }
        return grams;
    }

    private record Scored(QuestionBankDO row, double score) {}
}
