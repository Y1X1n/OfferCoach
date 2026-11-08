package com.offercoach.admin.application.port;

import com.offercoach.admin.application.model.AdminModels.ImportBatch;
import com.offercoach.admin.application.model.AdminModels.ImportedQuestion;
import com.offercoach.admin.application.model.AdminModels.Question;
import com.offercoach.admin.application.model.AdminModels.QuestionWrite;
import java.util.List;
import java.util.Optional;

/**
 * 题库管理仓储端口；筛选条件均为 null 时忽略。
 */
public interface AdminQuestionRepository {

    List<Question> search(
            String status,
            String jobFamily,
            String dimension,
            String difficulty,
            String keyword,
            int offset,
            int limit);

    long count(String status, String jobFamily, String dimension, String difficulty, String keyword);

    Optional<Question> findById(long id);

    /**
     * 新建 DRAFT 题目，返回主键。
     */
    long insert(QuestionWrite write, long createdBy);

    /**
     * 按非 null 字段更新；成功返回 1，题目不存在返回 0。
     */
    int update(long id, QuestionWrite patch);

    /**
     * 无条件更新状态；调用方负责状态机校验。
     */
    void updateStatus(long id, String status);

    /**
     * 创建 PENDING 导入批次，返回批次 ID。
     */
    long insertImportBatch(String sourceRepo, int totalCount, long createdBy);

    /**
     * 结束批次：写入计数、状态与错误报告。
     */
    void finishImportBatch(long id, String status, int success, int failed, int skipped, String errorReportJson);

    /**
     * 导入题目；按 (source_repo, source_path, md5(stem)) 幂等，冲突返回 0。
     */
    int insertImported(ImportedQuestion question, String sourceRepo, String sourcePath, long createdBy);

    List<ImportBatch> listBatches(int offset, int limit);

    long countBatches();

    Optional<ImportBatch> findBatch(long id);
}
