package com.offercoach.interview.session.application;

import com.offercoach.interview.config.InterviewProperties;
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
 * 定时清理 24 小时无活动的进行中会话；未答任何题时幂等退款。
 */
@Service
public class SessionIdleAbandoner {

    private static final Logger log = LoggerFactory.getLogger(SessionIdleAbandoner.class);
    private static final int BATCH_SIZE = 50;

    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewSessionService sessionService;
    private final InterviewProperties properties;

    public SessionIdleAbandoner(
            InterviewSessionRepository sessionRepository,
            InterviewAnswerRepository answerRepository,
            InterviewSessionService sessionService,
            InterviewProperties properties) {
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.sessionService = sessionService;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 600_000L)
    public void run() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(properties.getSessionIdleAbandon());
        List<InterviewSession> idle = sessionRepository.findIdle(cutoff, BATCH_SIZE);
        for (InterviewSession session : idle) {
            if (!sessionRepository.markIdleAbandoned(session.id(), cutoff)) {
                continue;
            }
            if (answerRepository.countBySession(session.id()) == 0) {
                sessionService.refundQuietly(session.userId(), session.id());
            }
            log.info("超时会话已放弃：sessionId={}", session.id());
        }
    }
}
