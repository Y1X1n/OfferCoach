package com.offercoach.interview.resume.application.port;

import com.offercoach.interview.resume.domain.Resume;
import java.util.List;
import java.util.Optional;

/**
 * 简历仓储端口；所有按用户读取的方法都必须带 user_id 归属条件。
 */
public interface ResumeRepository {

    Optional<Resume> findOwned(long userId, long id);

    /**
     * 按 id 批量取简历（归属过滤），用于报告列表批量解析标题。
     */
    List<Resume> findOwnedByIds(long userId, java.util.Collection<Long> ids);

    Optional<Resume> findAny(long id);

    Optional<Resume> findBySha256(long userId, String sha256);

    long insert(Resume resume);

    List<Resume> list(long userId, int offset, int limit);

    long count(long userId);

    /** 已完成解析的简历数，用于工作台就绪判断。 */
    long countReady(long userId);

    void clearDefault(long userId);

    void setDefault(long userId, long id);

    void updateParseState(long id, String parseStatus, String parseError);

    /**
     * 看门狗：超时仍停留在 PENDING/PARSING 的简历 ID。
     */
    List<Long> findStaleParsingIds(java.time.OffsetDateTime cutoff, int limit);

    /**
     * 看门狗：仅当仍处于 PENDING/PARSING 时置失败，避免覆盖并发完成的 READY。
     */
    boolean failIfParsing(long id, String parseError);

    void markReady(long id, String rawText, String structuredJson, String summary, String summaryHash, int pageCount);

    void markFailed(long id, String parseError);

    void updateTitle(long userId, long id, String title);

    void updateStructured(long id, String structuredJson, String summary, String summaryHash);

    void updateDefault(long userId, long id, boolean isDefault);

    void markDeleted(long userId, long id);

    long countActiveSessions(long userId, long resumeId);
}
