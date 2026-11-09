package com.offercoach.interview.resume.infrastructure.persistence;

import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.Resume;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 简历仓储实现：在边界完成 DO 与领域 record 的映射。
 */
@Repository
@RequiredArgsConstructor
public class ResumeRepositoryImpl implements ResumeRepository {

    private final ResumeMapper resumeMapper;

    @Override
    public Optional<Resume> findOwned(long userId, long id) {
        return Optional.ofNullable(toResume(resumeMapper.findOwned(userId, id)));
    }

    @Override
    public Optional<Resume> findAny(long id) {
        return Optional.ofNullable(toResume(resumeMapper.findAny(id)));
    }

    @Override
    public Optional<Resume> findBySha256(long userId, String sha256) {
        return Optional.ofNullable(toResume(resumeMapper.findBySha256(userId, sha256)));
    }

    @Override
    public long insert(Resume resume) {
        ResumeDO row = new ResumeDO();
        row.setUserId(resume.userId());
        row.setTitle(resume.title());
        row.setSourceType(resume.sourceType());
        row.setFileKey(resume.fileKey());
        row.setFileName(resume.fileName());
        row.setMimeType(resume.mimeType());
        row.setFileSize(resume.fileSize());
        row.setSha256(resume.sha256());
        row.setPageCount(resume.pageCount());
        row.setParseStatus(resume.parseStatus());
        row.setParseError(resume.parseError());
        row.setRawText(resume.rawText());
        row.setStructuredJson(resume.structuredJson());
        row.setSummary(resume.summary());
        row.setSummaryHash(resume.summaryHash());
        row.setDefaultFlag(resume.isDefault());
        resumeMapper.insert(row);
        return row.getId() == null ? 0 : row.getId();
    }

    @Override
    public List<Resume> list(long userId, int offset, int limit) {
        return resumeMapper.list(userId, offset, limit).stream()
                .map(ResumeRepositoryImpl::toResume)
                .toList();
    }

    @Override
    public long count(long userId) {
        return resumeMapper.count(userId);
    }

    @Override
    public long countReady(long userId) {
        return resumeMapper.countReady(userId);
    }

    @Override
    public void clearDefault(long userId) {
        resumeMapper.clearDefault(userId);
    }

    @Override
    public void setDefault(long userId, long id) {
        resumeMapper.setDefault(userId, id);
    }

    @Override
    public void updateParseState(long id, String parseStatus, String parseError) {
        resumeMapper.updateParseState(id, parseStatus, parseError);
    }

    @Override
    public List<Long> findStaleParsingIds(OffsetDateTime cutoff, int limit) {
        return resumeMapper.findStaleParsingIds(cutoff, limit);
    }

    @Override
    public boolean failIfParsing(long id, String parseError) {
        return resumeMapper.failIfParsing(id, parseError) > 0;
    }

    @Override
    public void markReady(
            long id, String rawText, String structuredJson, String summary, String summaryHash, int pageCount) {
        resumeMapper.markReady(id, rawText, structuredJson, summary, summaryHash, pageCount);
    }

    @Override
    public void markFailed(long id, String parseError) {
        resumeMapper.markFailed(id, parseError);
    }

    @Override
    public void updateTitle(long userId, long id, String title) {
        resumeMapper.updateTitle(userId, id, title);
    }

    @Override
    public void updateStructured(long id, String structuredJson, String summary, String summaryHash) {
        resumeMapper.updateStructured(id, structuredJson, summary, summaryHash);
    }

    @Override
    public void updateDefault(long userId, long id, boolean isDefault) {
        resumeMapper.updateDefault(userId, id, isDefault);
    }

    @Override
    public void markDeleted(long userId, long id) {
        resumeMapper.markDeleted(userId, id, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public long countActiveSessions(long userId, long resumeId) {
        return resumeMapper.countActiveSessions(userId, resumeId);
    }

    private static Resume toResume(ResumeDO row) {
        if (row == null) {
            return null;
        }
        return new Resume(
                row.getId() == null ? 0 : row.getId(),
                row.getUserId() == null ? 0 : row.getUserId(),
                row.getTitle(),
                row.getSourceType(),
                row.getFileKey(),
                row.getFileName(),
                row.getMimeType(),
                row.getFileSize(),
                row.getSha256(),
                row.getPageCount(),
                row.getParseStatus(),
                row.getParseError(),
                row.getRawText(),
                row.getStructuredJson(),
                row.getSummary(),
                row.getSummaryHash(),
                Boolean.TRUE.equals(row.getDefaultFlag()),
                row.getCreatedAt());
    }
}
