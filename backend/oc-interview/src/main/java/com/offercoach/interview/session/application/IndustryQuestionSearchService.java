package com.offercoach.interview.session.application;

import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.gateway.AiToolGateway;
import com.offercoach.interview.session.application.QuestionContextBuilder.QuestionContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 行业题库检索：出题前用岗位标题与技能联网搜索公开面经/题库线索。
 *
 * <p>搜索结果只作为「出题方向」提供给模型，不直接当作题目；工具服务不可用、未配置 Key
 * 或平台关闭该能力时返回空，由调用方退回模型直出 + 题库兜底，不阻断出题。</p>
 */
@Service
public class IndustryQuestionSearchService {

    private static final Logger log = LoggerFactory.getLogger(IndustryQuestionSearchService.class);
    private static final int MAX_SKILLS_IN_QUERY = 3;
    private static final int MAX_ITEMS = 6;
    private static final int SNIPPET_LIMIT = 140;
    private static final int BRIEF_LIMIT = 1200;

    private final AiToolGateway gateway;
    private final boolean enabled;
    private final int requestedCount;

    public IndustryQuestionSearchService(
            AiToolGateway gateway,
            @Value("${oc.interview.question-search-enabled:true}") boolean enabled,
            @Value("${oc.interview.question-search-count:6}") int requestedCount) {
        this.gateway = gateway;
        this.enabled = enabled;
        this.requestedCount = Math.min(Math.max(requestedCount, 1), MAX_ITEMS);
    }

    /**
     * 检索行业题库线索；任何异常都降级为空，不抛给调用方。
     */
    public Optional<SearchContext> lookup(long userId, QuestionContext context) {
        if (!enabled || context == null) {
            return Optional.empty();
        }
        String query = buildQuery(context);
        try {
            List<ToolDtos.SearchResult> results = gateway.search(userId, query, requestedCount, null, false);
            return toContext(query, results);
        } catch (RuntimeException exception) {
            log.warn("行业题库检索失败，改为模型直出：reason={}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 查询串：岗位标题 + 前三个技能 + 「面试题 面经」。岗位标题为空时退回技能。
     */
    String buildQuery(QuestionContext context) {
        Set<String> parts = new LinkedHashSet<>();
        if (context.jdTitle() != null && !context.jdTitle().isBlank()) {
            parts.add(context.jdTitle().strip());
        }
        int added = 0;
        for (String skill : context.skills()) {
            if (skill == null || skill.isBlank()) {
                continue;
            }
            parts.add(skill.strip());
            added++;
            if (added >= MAX_SKILLS_IN_QUERY) {
                break;
            }
        }
        if (parts.isEmpty()) {
            for (String keyword : context.keywords()) {
                if (keyword != null && !keyword.isBlank()) {
                    parts.add(keyword.strip());
                    break;
                }
            }
        }
        parts.add("面试题 面经");
        return String.join(" ", parts);
    }

    private Optional<SearchContext> toContext(String query, List<ToolDtos.SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }
        List<Evidence> items = new ArrayList<>();
        StringBuilder brief = new StringBuilder();
        for (ToolDtos.SearchResult result : results) {
            String title = safe(result.title());
            String snippet = truncate(safe(result.snippet()), SNIPPET_LIMIT);
            if (title.isBlank() && snippet.isBlank()) {
                continue;
            }
            items.add(new Evidence(title, safe(result.siteName()), safe(result.url()), snippet));
            brief.append(items.size())
                    .append(". ")
                    .append(title)
                    .append(siteSuffix(result.siteName()))
                    .append("：")
                    .append(snippet)
                    .append('\n');
            if (brief.length() > BRIEF_LIMIT) {
                break;
            }
        }
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SearchContext(
                query, List.copyOf(items), truncate(brief.toString().strip(), BRIEF_LIMIT)));
    }

    private static String siteSuffix(String site) {
        return site == null || site.isBlank() ? "" : "（" + site.strip() + "）";
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }

    /** 单条检索证据；url 仅用于溯源展示，不写入模型 Prompt 以外的位置。 */
    public record Evidence(String title, String site, String url, String snippet) {}

    /** 一次检索的查询串、证据列表与给模型看的压缩摘要。 */
    public record SearchContext(String query, List<Evidence> items, String brief) {}
}
