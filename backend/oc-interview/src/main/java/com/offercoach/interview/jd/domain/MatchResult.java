package com.offercoach.interview.jd.domain;

import java.util.List;

/**
 * JD 匹配结果；字段与 api.md §5.2 契约一致，degraded 标记是否走确定性降级。
 */
public record MatchResult(
        double matchScore,
        List<MatchedSkill> matchedSkills,
        List<MissingSkill> missingSkills,
        List<String> risks,
        List<MatchEvidence> evidence,
        boolean degraded) {

    public record MatchedSkill(String skill, String evidence) {}

    public record MissingSkill(String skill, String suggestion) {}

    public record MatchEvidence(String jdQuote, String resumeQuote) {}
}
