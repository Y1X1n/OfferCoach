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

    long insert(JobDescription description);

    List<JobDescription> list(long userId, int offset, int limit);

    long count(long userId);

    void updateMatch(long id, BigDecimal matchScore, String matchReport);

    void markDeleted(long userId, long id);
}
