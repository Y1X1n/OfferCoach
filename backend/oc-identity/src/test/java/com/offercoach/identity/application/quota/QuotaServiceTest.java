package com.offercoach.identity.application.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.port.QuotaRepository;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.identity.config.QuotaProperties;
import com.offercoach.identity.domain.quota.QuotaAccount;
import com.offercoach.identity.domain.quota.QuotaLedgerEntry;
import com.offercoach.identity.domain.quota.QuotaType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 额度扣减/返还/发放的钱语义：幂等键、行锁、余额不足、条件更新影响 0 行。
 *
 * <p>额度是这个产品的计价单位，这些边界错一个就是"多扣 / 少扣 / 重复发放"，所以每条都单独钉住。</p>
 */
class QuotaServiceTest {

    private static final long USER_ID = 7L;

    private QuotaRepository quotaRepository;
    private PlatformSettingsService settingsService;
    private StringRedisTemplate redisTemplate;
    private RedisCounterGuard counterGuard;
    private QuotaService service;

    @BeforeEach
    void setUp() {
        quotaRepository = mock(QuotaRepository.class);
        settingsService = mock(PlatformSettingsService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        counterGuard = mock(RedisCounterGuard.class);
        service = new QuotaService(
                quotaRepository,
                new QuotaProperties(),
                settingsService,
                redisTemplate,
                mock(ObjectMapper.class),
                counterGuard);

        when(quotaRepository.findQuota(USER_ID)).thenReturn(Optional.of(account("PRO", 10, 10)));
        when(quotaRepository.lockQuota(USER_ID)).thenReturn(Optional.of(account("PRO", 10, 10)));
        when(quotaRepository.decrease(anyLong(), any(), anyInt())).thenReturn(1);
        when(quotaRepository.increase(anyLong(), any(), anyInt())).thenReturn(1);
    }

    // ---- 入参校验 ----

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> service.charge(USER_ID, QuotaType.INTERVIEW, 0, "R", "SESSION", 1L, "k"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verifyNoInteractions(quotaRepository);
    }

    @Test
    void rejectsBlankIdempotencyKey() {
        assertThatThrownBy(() -> service.charge(USER_ID, QuotaType.INTERVIEW, 1, "R", "SESSION", 1L, " "))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void rejectsBlankReason() {
        assertThatThrownBy(() -> service.grant(USER_ID, QuotaType.RESUME, 1, null, "USER", 1L, "k"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    // ---- 幂等 ----

    /**
     * 同一幂等键重放：不改余额、不写第二条流水，直接把首次的 balance_after 返回给调用方。
     */
    @Test
    void replaysExistingLedgerWithoutMovingBalance() {
        when(quotaRepository.findLedger(USER_ID, "charge-1"))
                .thenReturn(Optional.of(new QuotaLedgerEntry(
                        9L, USER_ID, "INTERVIEW", -2, 4, "SESSION_CHARGE", "SESSION", 33L, "charge-1")));

        QuotaResult result =
                service.charge(USER_ID, QuotaType.INTERVIEW, 2, "SESSION_CHARGE", "SESSION", 33L, "charge-1");

        assertThat(result.charged()).isFalse();
        assertThat(result.replayed()).isTrue();
        assertThat(result.balanceAfter()).isEqualTo(4);
        verify(quotaRepository, never()).decrease(anyLong(), any(), anyInt());
        verify(quotaRepository, never()).insertLedger(any());
    }

    // ---- 扣减 ----

    @Test
    void chargeFailsWhenBalanceInsufficient() {
        when(quotaRepository.lockQuota(USER_ID)).thenReturn(Optional.of(account("FREE", 1, 5)));

        assertThatThrownBy(() -> service.charge(USER_ID, QuotaType.INTERVIEW, 2, "SESSION_CHARGE", "SESSION", 1L, "k"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> {
                    BizException biz = (BizException) exception;
                    assertThat(biz.errorCode()).isEqualTo(ErrorCode.QUOTA_EXCEEDED);
                    assertThat(biz.details())
                            .containsEntry("required", 2)
                            .containsEntry("balance", 1)
                            .containsEntry("planCode", "FREE");
                });

        verify(quotaRepository, never()).decrease(anyLong(), any(), anyInt());
    }

    /** 行锁读到的余额够、条件扣减却影响 0 行：说明并发把余额吃掉了，必须按不足处理。 */
    @Test
    void chargeFailsWhenConditionalDecreaseHitsNoRow() {
        when(quotaRepository.decrease(anyLong(), any(), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> service.charge(USER_ID, QuotaType.INTERVIEW, 2, "SESSION_CHARGE", "SESSION", 1L, "k"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.QUOTA_EXCEEDED);

        verify(quotaRepository, never()).insertLedger(any());
    }

    @Test
    void chargeWritesNegativeLedgerWithBalanceAfter() {
        // 两类额度给不同数值，INTERVIEW/RESUME 走反了才会被发现
        when(quotaRepository.lockQuota(USER_ID)).thenReturn(Optional.of(account("PRO", 10, 99)));

        QuotaResult result =
                service.charge(USER_ID, QuotaType.INTERVIEW, 3, "SESSION_CHARGE", "SESSION", 42L, "charge-42");

        assertThat(result.charged()).isTrue();
        assertThat(result.balanceAfter()).isEqualTo(7);

        // 扣减必须走 decrease，不能走 increase（否则余额会越扣越多）
        verify(quotaRepository).decrease(USER_ID, QuotaType.INTERVIEW, 3);
        verify(quotaRepository, never()).increase(anyLong(), any(), anyInt());

        ArgumentCaptor<QuotaLedgerEntry> ledger = ArgumentCaptor.forClass(QuotaLedgerEntry.class);
        verify(quotaRepository).insertLedger(ledger.capture());
        assertThat(ledger.getValue().quotaType()).isEqualTo("INTERVIEW");
        assertThat(ledger.getValue().delta()).isEqualTo(-3);
        assertThat(ledger.getValue().balanceAfter()).isEqualTo(7);
        assertThat(ledger.getValue().idempotencyKey()).isEqualTo("charge-42");
        assertThat(ledger.getValue().refId()).isEqualTo(42L);
    }

    // ---- 返还 / 发放 ----

    @Test
    void refundWritesPositiveLedger() {
        QuotaResult result =
                service.refund(USER_ID, QuotaType.INTERVIEW, 5, "RESUME_PARSE_FAILED", "RESUME", 8L, "refund-8");

        assertThat(result.balanceAfter()).isEqualTo(15);
        // 返还走 increase：如果误走 decrease，余额会被反向扣掉，只断言 delta 看不出来
        verify(quotaRepository).increase(USER_ID, QuotaType.INTERVIEW, 5);
        verify(quotaRepository, never()).decrease(anyLong(), any(), anyInt());

        ArgumentCaptor<QuotaLedgerEntry> ledger = ArgumentCaptor.forClass(QuotaLedgerEntry.class);
        verify(quotaRepository).insertLedger(ledger.capture());
        assertThat(ledger.getValue().quotaType()).isEqualTo("INTERVIEW");
        assertThat(ledger.getValue().delta()).isEqualTo(5);
    }

    /** 加额度却影响 0 行：只能是没有额度行，抛 404 让调用方事务回滚，不能假成功。 */
    @Test
    void grantFailsWhenIncreaseHitsNoRow() {
        when(quotaRepository.increase(anyLong(), any(), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> service.grant(USER_ID, QuotaType.RESUME, 1, "INVITE_REWARD", "USER", 2L, "k"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(quotaRepository, never()).insertLedger(any());
    }

    @Test
    void lockFailureIsResourceNotFound() {
        when(quotaRepository.lockQuota(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(USER_ID, QuotaType.RESUME, 1, "INVITE_REWARD", "USER", 2L, "k"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * 历史用户没有额度行：先按 FREE 初始化（含两条初始发放流水），再继续本次变更——
     * 否则"给他人发奖励"会以 404 打断注册事务。
     */
    @Test
    void missingQuotaRowIsInitializedBeforeApplyingDelta() {
        when(quotaRepository.findQuota(USER_ID)).thenReturn(Optional.empty()).thenReturn(Optional.empty());
        when(quotaRepository.findPlan(anyString())).thenReturn(Optional.empty());

        service.grant(USER_ID, QuotaType.INTERVIEW, 1, "INVITE_REWARD", "USER", 2L, "invite-2");

        verify(quotaRepository).insertQuotaIfAbsent(any(QuotaAccount.class));
        // FREE 套餐两项初始发放 + 本次 1 条
        verify(quotaRepository, times(3)).insertLedger(any());
    }

    /** 缓存清理只是加速层，失败不能让已生效的扣减回滚。 */
    @Test
    void cacheEvictionFailureDoesNotBreakCharge() {
        when(redisTemplate.delete(anyString())).thenThrow(new IllegalStateException("redis down"));

        QuotaResult result = service.charge(USER_ID, QuotaType.INTERVIEW, 1, "SESSION_CHARGE", "SESSION", 1L, "k");

        assertThat(result.charged()).isTrue();
    }

    // ---- 每日名额 ----

    @Test
    void dailySessionReservationSkippedWhenCapDisabled() {
        when(settingsService.snapshot()).thenReturn(snapshot(0, 0));

        service.reserveDailySession(USER_ID);

        verifyNoInteractions(quotaRepository);
    }

    @Test
    void dailySessionReservationFailsWhenAtomicIncrementHitsCap() {
        when(settingsService.snapshot()).thenReturn(snapshot(3, 0));
        when(quotaRepository.incrementDailyIfUnderCap(eq(USER_ID), any(LocalDate.class), eq(3)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.reserveDailySession(USER_ID))
                .isInstanceOf(BizException.class)
                .satisfies(exception ->
                        assertThat(((BizException) exception).details()).containsEntry("type", "DAILY_SESSION"));
    }

    @Test
    void dailyResumeParseGoesThroughCounterGuard() {
        when(settingsService.snapshot()).thenReturn(snapshot(0, 10));
        when(counterGuard.tryAcquire(anyString(), eq(10), any())).thenReturn(false);

        assertThatThrownBy(() -> service.reserveDailyResumeParse(USER_ID))
                .isInstanceOf(BizException.class)
                .satisfies(exception ->
                        assertThat(((BizException) exception).details()).containsEntry("type", "DAILY_RESUME_PARSE"));
    }

    private static QuotaAccount account(String planCode, int interview, int resume) {
        // 与生产一致用业务日期（Asia/Shanghai），避免 UTC 跨天时测试与实现不在同一天
        LocalDate today = QuotaService.businessDate();
        return new QuotaAccount(USER_ID, planCode, interview, resume, today, today.plusMonths(1), today, 0);
    }

    private static PlatformSettingsService.Snapshot snapshot(int dailyInterviewLimit, int dailyResumeLimit) {
        return new PlatformSettingsService.Snapshot(
                true,
                dailyInterviewLimit,
                dailyResumeLimit,
                0,
                0,
                1,
                1,
                5,
                20,
                0,
                0,
                10,
                20,
                50,
                50,
                null,
                OffsetDateTime.now());
    }
}
