package com.offercoach.interview.session.application;

import com.offercoach.interview.config.InterviewProperties;
import com.offercoach.interview.session.application.port.InterviewAnswerRepository;
import com.offercoach.interview.session.application.port.InterviewQuestionRepository;
import com.offercoach.interview.session.application.port.InterviewSessionRepository;
import com.offercoach.interview.session.domain.InterviewSession;
import java.time.Duration;
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
    private final InterviewQuestionRepository questionRepository;
    private final InterviewSessionService sessionService;
    private final InterviewProperties properties;

    public SessionIdleAbandoner(
            InterviewSessionRepository sessionRepository,
            InterviewAnswerRepository answerRepository,
            InterviewQuestionRepository questionRepository,
            InterviewSessionService sessionService,
            InterviewProperties properties) {
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.sessionService = sessionService;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 600_000L)
    public void run() {
        Duration idle = properties.getSessionIdleAbandon();
        if (idle == null || idle.isNegative() || idle.isZero()) {
            // 配置为 0/负值会把 cutoff 推到未来，下一轮就会把所有进行中会话全部放弃。
            log.warn("空闲放弃阈值未配置或非法，跳过本轮：value={}", idle);
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(idle);
        List<InterviewSession> idleSessions = sessionRepository.findIdle(cutoff, BATCH_SIZE);
        for (InterviewSession session : idleSessions) {
            if (!sessionRepository.markIdleAbandoned(session.id(), cutoff)) {
                continue;
            }
            // 与手动放弃一致：题目已生成即视为已消耗出题成本，不再退款。
            if (answerRepository.countBySession(session.id()) == 0
                    && questionRepository.countBySession(session.id()) == 0) {
                sessionService.refundQuietly(session.userId(), session.id());
            }
            log.info("超时会话已放弃：sessionId={}", session.id());
        }
    }
}
