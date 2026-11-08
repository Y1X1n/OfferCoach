package com.offercoach.interview.session.domain;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.util.Map;
import java.util.Set;

/**
 * 会话状态机：声明合法迁移，禁止跨级跳转；数据库行级条件更新作为最终兜底。
 */
public final class SessionStateMachine {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            SessionStatus.CREATED, Set.of(SessionStatus.PREPARING, SessionStatus.ABANDONED),
            SessionStatus.PREPARING, Set.of(SessionStatus.READY, SessionStatus.FAILED, SessionStatus.ABANDONED),
            SessionStatus.READY, Set.of(SessionStatus.IN_PROGRESS, SessionStatus.ABANDONED),
            SessionStatus.IN_PROGRESS,
                    Set.of(SessionStatus.PAUSED, SessionStatus.REPORT_GENERATING, SessionStatus.ABANDONED),
            SessionStatus.PAUSED,
                    Set.of(SessionStatus.IN_PROGRESS, SessionStatus.REPORT_GENERATING, SessionStatus.ABANDONED),
            SessionStatus.FINISHED, Set.of(SessionStatus.REPORT_GENERATING),
            SessionStatus.REPORT_GENERATING, Set.of(SessionStatus.COMPLETED, SessionStatus.REPORT_FAILED),
            SessionStatus.REPORT_FAILED, Set.of(SessionStatus.REPORT_GENERATING));

    private SessionStateMachine() {}

    public static boolean canTransit(String from, String to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * 校验迁移，非法时抛 STATE_CONFLICT 并带当前状态。
     */
    public static void require(String from, String to) {
        if (!canTransit(from, to)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, Map.of("status", from, "target", to));
        }
    }
}
