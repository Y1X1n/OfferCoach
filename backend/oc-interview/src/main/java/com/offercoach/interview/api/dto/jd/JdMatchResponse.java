package com.offercoach.interview.api.dto.jd;

import com.offercoach.interview.jd.domain.MatchResult;
import java.util.List;

/**
 * 匹配分析响应，结构与 api.md §5.2 一致。
 */
public record JdMatchResponse(
        double matchScore,
        List<MatchedSkill> matchedSkills,
        List<MissingSkill> missingSkills,
        List<String> risks,
        List<MatchEvidence> evidence) {

    public record MatchedSkill(String skill, String evidence) {}

    public record MissingSkill(String skill, String suggestion) {}

    public record MatchEvidence(String jdQuote, String resumeQuote) {}

    public static JdMatchResponse from(MatchResult result) {
        return new JdMatchResponse(
                result.matchScore(),
                result.matchedSkills().stream()
                        .map(item -> new MatchedSkill(item.skill(), item.evidence()))
                        .toList(),
                result.missingSkills().stream()
                        .map(item -> new MissingSkill(item.skill(), item.suggestion()))
                        .toList(),
                result.risks(),
                result.evidence().stream()
                        .map(item -> new MatchEvidence(item.jdQuote(), item.resumeQuote()))
                        .toList());
    }
}
