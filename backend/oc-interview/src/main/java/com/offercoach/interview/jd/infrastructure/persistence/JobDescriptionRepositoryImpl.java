package com.offercoach.interview.jd.infrastructure.persistence;

import com.offercoach.interview.jd.application.port.JobDescriptionRepository;
import com.offercoach.interview.jd.domain.JobDescription;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * JD 仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class JobDescriptionRepositoryImpl implements JobDescriptionRepository {

    private final JdMapper jdMapper;

    @Override
    public Optional<JobDescription> findOwned(long userId, long id) {
        return Optional.ofNullable(toDomain(jdMapper.findOwned(userId, id)));
    }

    @Override
    public long insert(JobDescription description) {
        JobDescriptionDO row = new JobDescriptionDO();
        row.setUserId(description.userId());
        row.setTitle(description.title());
        row.setCompany(description.company());
        row.setRawText(description.rawText());
        row.setStructuredJson(description.structuredJson());
        row.setContentHash(description.contentHash());
        jdMapper.insert(row);
        return row.getId() == null ? 0 : row.getId();
    }

    @Override
    public List<JobDescription> list(long userId, int offset, int limit) {
        return jdMapper.list(userId, offset, limit).stream()
                .map(JobDescriptionRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public long count(long userId) {
        return jdMapper.count(userId);
    }

    @Override
    public void updateMatch(long id, BigDecimal matchScore, String matchReport) {
        jdMapper.updateMatch(id, matchScore, matchReport);
    }

    @Override
    public void markDeleted(long userId, long id) {
        jdMapper.markDeleted(userId, id, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static JobDescription toDomain(JobDescriptionDO row) {
        if (row == null) {
            return null;
        }
        return new JobDescription(
                row.getId() == null ? 0 : row.getId(),
                row.getUserId() == null ? 0 : row.getUserId(),
                row.getTitle(),
                row.getCompany(),
                row.getRawText(),
                row.getStructuredJson(),
                row.getMatchScore(),
                row.getMatchReport(),
                row.getContentHash(),
                row.getCreatedAt());
    }
}
