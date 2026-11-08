package com.offercoach.interview.qbank;

import java.util.List;
import java.util.Set;

/**
 * 题库检索端口；M2 出题只依赖本接口，具体实现使用 PostgreSQL pg_trgm。
 */
public interface QuestionRetriever {

    /**
     * 按岗位族、关键词与维度召回候选题目，按相关性降序。
     */
    List<RetrievedQuestion> retrieve(RetrieveQuery query);

    record RetrieveQuery(String jobFamily, Set<String> keywords, int limit) {}

    record RetrievedQuestion(
            long id,
            String title,
            String stem,
            String dimension,
            String difficulty,
            String jobFamily,
            List<String> tags,
            List<String> referencePoints,
            double score,
            String sourceRepo,
            String company) {}
}
