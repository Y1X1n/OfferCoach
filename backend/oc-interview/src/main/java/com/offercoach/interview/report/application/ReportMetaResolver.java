package com.offercoach.interview.report.application;

import com.offercoach.interview.jd.application.port.JobDescriptionRepository;
import com.offercoach.interview.jd.domain.JobDescription;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.Resume;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewSession;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 报告展示元信息：由会话关联的简历与 JD 推导标题、公司与岗位。
 *
 * <p>列表场景必须走 {@link #resolveAll}：逐份调用 {@link #resolve} 时每份报告要查
 * 会话 / JD / 简历三次，一页 20 份就是 61 条查询。</p>
 */
@Component
public class ReportMetaResolver {

    private static final String FALLBACK_ROLE = "模拟面试";
    private static final String FALLBACK_COMPANY = "目标公司";

    private final InterviewSessionRepository sessionRepository;
    private final JobDescriptionRepository jdRepository;
    private final ResumeRepository resumeRepository;

    public ReportMetaResolver(
            InterviewSessionRepository sessionRepository,
            JobDescriptionRepository jdRepository,
            ResumeRepository resumeRepository) {
        this.sessionRepository = sessionRepository;
        this.jdRepository = jdRepository;
        this.resumeRepository = resumeRepository;
    }

    /**
     * 单份报告的元信息；未找到会话/简历/JD 时返回占位值。
     */
    public Meta resolve(long userId, long sessionId) {
        return resolveAll(userId, List.of(sessionId)).getOrDefault(sessionId, unknown());
    }

    /**
     * 批量解析：会话 / JD / 简历各查一次，查询次数与 page size 无关。
     *
     * @return sessionId → Meta；入参为空时返回空 Map
     */
    public Map<Long, Meta> resolveAll(long userId, Collection<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> wanted = new LinkedHashSet<>();
        sessionIds.stream().filter(Objects::nonNull).forEach(wanted::add);
        if (wanted.isEmpty()) {
            return Map.of();
        }

        Map<Long, InterviewSession> sessions =
                index(sessionRepository.findByIdsOwned(userId, wanted), InterviewSession::id);
        Map<Long, JobDescription> jds =
                index(jdRepository.findOwnedByIds(userId, idsOf(sessions, InterviewSession::jdId)), JobDescription::id);
        Map<Long, Resume> resumes =
                index(resumeRepository.findOwnedByIds(userId, idsOf(sessions, InterviewSession::resumeId)), Resume::id);

        Map<Long, Meta> metas = new LinkedHashMap<>();
        for (Long sessionId : wanted) {
            InterviewSession session = sessions.get(sessionId);
            metas.put(
                    sessionId,
                    meta(
                            session == null ? null : jds.get(session.jdId()),
                            session == null ? null : resumes.get(session.resumeId()),
                            session));
        }
        return metas;
    }

    /**
     * 找不到关联数据时的占位元信息。
     */
    public static Meta unknown() {
        return new Meta(FALLBACK_ROLE + " · 面试报告", FALLBACK_COMPANY, FALLBACK_ROLE, 0);
    }

    private static Meta meta(JobDescription jd, Resume resume, InterviewSession session) {
        String jdTitle = jd == null ? null : jd.title();
        String company = jd == null ? null : jd.company();
        String role = jdTitle == null || jdTitle.isBlank() ? FALLBACK_ROLE : jdTitle;
        String title =
                resume != null && resume.title() != null && !resume.title().isBlank()
                        ? resume.title()
                        : role + " · 面试报告";
        return new Meta(
                title,
                company == null || company.isBlank() ? FALLBACK_COMPANY : company,
                role,
                session == null ? 0 : session.questionCount());
    }

    private static Set<Long> idsOf(Map<Long, InterviewSession> sessions, Function<InterviewSession, Long> extractor) {
        Set<Long> ids = new LinkedHashSet<>();
        sessions.values().forEach(session -> {
            Long id = extractor.apply(session);
            if (id != null && id > 0) {
                ids.add(id);
            }
        });
        return ids;
    }

    private static <T> Map<Long, T> index(List<T> rows, Function<T, Long> keyFn) {
        return rows.stream().collect(Collectors.toMap(keyFn, Function.identity(), (left, right) -> left));
    }

    public record Meta(String title, String company, String role, int questionCount) {}
}
