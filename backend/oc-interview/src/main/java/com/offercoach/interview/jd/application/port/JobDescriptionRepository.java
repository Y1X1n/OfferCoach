package com.offercoach.interview.jd.application.port;

import com.offercoach.interview.jd.domain.JobDescription;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * JD 仓储端口；按用户读取必须带 user_id 归属条件。
 */
public interface JobDescriptionRepository {

    Optional<JobDescription> findOwned(long userId, long id);

    /**
     * 按 id 批量取 JD（归属过滤），用于报告列表批量解析标题与公司。
     */
    List<JobDescription> findOwnedByIds(long userId, java.util.Collection<Long> ids);

    /**
     * 按内容哈希查同用户未删除的 JD；无则为空。
     */
    Optional<JobDescription> findByContentHash(long userId, String contentHash);

    long insert(JobDescription description);

    /**
     * 幂等插入：命中唯一键时返回 0（不抛异常），由调用方回查既有行。
     *
     * <p>事务内去重写入必须走这条路径——PostgreSQL 唯一约束冲突会把整个事务置为 aborted，
     * 捕获异常后再回查会连带失败。</p>
     */
    long insertIfAbsent(JobDescription description);

    List<JobDescription> list(long userId, int offset, int limit);

    long count(long userId);

    void updateMatch(long id, BigDecimal matchScore, String matchReport);

    void markDeleted(long userId, long id);
}
