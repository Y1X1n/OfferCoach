package com.offercoach.interview.report.application;

import com.offercoach.interview.jd.application.port.JobDescriptionRepository;
import com.offercoach.interview.jd.domain.JobDescription;
import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.Resume;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewSession;
import org.springframework.stereotype.Component;

/**
 * 报告展示元信息：由会话关联的简历与 JD 推导标题、公司与岗位。
 */
@Component
public class ReportMetaResolver {

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

    public Meta resolve(long userId, long sessionId) {
        InterviewSession session = sessionRepository.findAny(sessionId).orElse(null);
        JobDescription jd = session == null
                ? null
                : jdRepository.findOwned(userId, session.jdId()).orElse(null);
        Resume resume = session == null
                ? null
                : resumeRepository.findOwned(userId, session.resumeId()).orElse(null);
        String jdTitle = jd == null ? null : jd.title();
        String company = jd == null ? null : jd.company();
        String role = jdTitle == null || jdTitle.isBlank() ? "模拟面试" : jdTitle;
        String title =
                resume != null && resume.title() != null && !resume.title().isBlank()
                        ? resume.title()
                        : role + " · 面试报告";
        return new Meta(
                title,
                company == null || company.isBlank() ? "目标公司" : company,
                role,
                session == null ? 0 : session.questionCount());
    }

    public record Meta(String title, String company, String role, int questionCount) {}
}
