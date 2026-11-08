package com.offercoach.interview.session.application;

import com.offercoach.interview.qbank.QuestionRetriever.RetrievedQuestion;
import com.offercoach.interview.session.domain.QuestionPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 题库选题：每个槽位优先同维度、同难度的召回题目，按相关度降序，且不重复使用。
 */
public final class QuestionSelection {

    private QuestionSelection() {}

    /**
     * 返回与槽位一一对应的选题结果；题库不足时对应位置为 null，由调用方用 AI 或模板补齐。
     */
    public static List<Selected> select(List<QuestionPlan.Slot> slots, List<RetrievedQuestion> candidates) {
        List<RetrievedQuestion> pool = candidates == null ? List.of() : candidates;
        Map<String, List<RetrievedQuestion>> byDimension = new HashMap<>();
        for (RetrievedQuestion candidate : pool) {
            if (candidate.dimension() == null) {
                continue;
            }
            byDimension
                    .computeIfAbsent(candidate.dimension(), key -> new ArrayList<>())
                    .add(candidate);
        }
        Set<Long> used = new HashSet<>();
        List<Selected> result = new ArrayList<>(slots.size());
        for (QuestionPlan.Slot slot : slots) {
            RetrievedQuestion picked = pick(byDimension.getOrDefault(slot.dimension(), List.of()), slot, used);
            if (picked != null) {
                used.add(picked.id());
            }
            result.add(new Selected(slot, picked));
        }
        return result;
    }

    private static RetrievedQuestion pick(List<RetrievedQuestion> candidates, QuestionPlan.Slot slot, Set<Long> used) {
        RetrievedQuestion fallback = null;
        for (RetrievedQuestion candidate : candidates) {
            if (used.contains(candidate.id())) {
                continue;
            }
            if (slot.difficulty().equalsIgnoreCase(candidate.difficulty())) {
                return candidate;
            }
            if (fallback == null) {
                fallback = candidate;
            }
        }
        return fallback;
    }

    public record Selected(QuestionPlan.Slot slot, RetrievedQuestion question) {

        public boolean fromBank() {
            return question != null;
        }
    }
}
