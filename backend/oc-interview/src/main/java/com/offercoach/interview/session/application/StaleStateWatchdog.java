package com.offercoach.interview.session.application;

import com.offercoach.interview.resume.application.port.ResumeRepository;
import com.offercoach.interview.resume.domain.ResumeStatus;
import com.offercoach.interview.session.application.port.InterviewAnswerRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewSession;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 状态看门狗：异步任务丢失时，超时的 PREPARING 会话置失败并退款、卡住的简历解析置失败、
 * 超时的 SCORING 回答标记失败，避免用户永久等待。
 */
@Service
public class StaleStateWatchdog {

    private static final Logger log = LoggerFactory.getLogger(StaleStateWatchdog.class);

    private static final int STALE_MINUTES = 10;
    private static final int BATCH_SIZE = 20;
    private static final String SESSION_TIMEOUT_REASON = "WATCHDOG_TIMEOUT";
    private static final String RESUME_TIMEOUT_REASON = "解析超时，请重新解析";

    private final InterviewSessionRepository sessionRepository;
    private final InterviewSessionService sessionService;
    private final ResumeRepository resumeRepository;
    private final InterviewAnswerRepository answerRepository;

    public StaleStateWatchdog(
            InterviewSessionRepository sessionRepository,
            InterviewSessionService sessionService,
            ResumeRepository resumeRepository,
            InterviewAnswerRepository answerRepository) {
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.resumeRepository = resumeRepository;
        this.answerRepository = answerRepository;
    }

    @Scheduled(fixedDelay = 60_000L)
    public void run() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(STALE_MINUTES);
        recoverSessions(cutoff);
        recoverResumes(cutoff);
        recoverAnswers(cutoff);
    }

    private void recoverSessions(OffsetDateTime cutoff) {
        List<InterviewSession> stale = sessionRepository.findStalePreparing(cutoff, BATCH_SIZE);
        for (InterviewSession session : stale) {
            sessionService.markGenerationFailed(session.userId(), session.id(), SESSION_TIMEOUT_REASON);
            log.warn("超时会话置失败：sessionId={}", session.id());
        }
    }

    private void recoverResumes(OffsetDateTime cutoff) {
        for (Long resumeId : resumeRepository.findStaleParsingIds(cutoff, BATCH_SIZE)) {
            resumeRepository.updateParseState(resumeId, ResumeStatus.FAILED, RESUME_TIMEOUT_REASON);
            log.warn("超时简历解析置失败：resumeId={}", resumeId);
        }
    }

    private void recoverAnswers(OffsetDateTime cutoff) {
        int failed = answerRepository.failStaleScoring(cutoff);
        if (failed > 0) {
            log.warn("超时评分标记失败：count={}", failed);
        }
    }
}
