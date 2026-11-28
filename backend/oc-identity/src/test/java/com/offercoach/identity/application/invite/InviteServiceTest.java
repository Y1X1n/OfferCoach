package com.offercoach.identity.application.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.application.settings.PlatformSettingsService;
import com.offercoach.identity.domain.quota.QuotaType;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

/**
 * 邀请奖励的发放边界：自己邀请自己、同设备、名额用尽、重复回调。
 *
 * <p>奖励是直接发额度，错一次就是白送；这里用捕获实际执行的 SQL 来断言"到底有没有写奖励行、
 * 有没有调用 grant"，而不是逐条核对内部调用顺序。</p>
 */
class InviteServiceTest {

    private static final long INVITEE = 7L;
    private static final long INVITER = 9L;
    private static final String CODE = "ABCDEFGHJK";
    private static final String DEVICE = "device-hash-1";

    private JdbcTemplate jdbc;
    private QuotaService quotaService;
    private PlatformSettingsService settingsService;
    private InviteService service;
    private final List<String> executed = new ArrayList<>();

    private boolean inviterFound = true;
    private long inviterId = INVITER;
    private String inviterDevice = "other-device";
    private int rewardInsertResult = 1;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        quotaService = mock(QuotaService.class);
        settingsService = mock(PlatformSettingsService.class);
        executed.clear();
        inviterFound = true;
        inviterId = INVITER;
        inviterDevice = "other-device";
        rewardInsertResult = 1;
        when(settingsService.snapshot()).thenReturn(snapshot(5, 20));

        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            executed.add(sql);
            return sql.startsWith("INSERT INTO invite_reward") ? rewardInsertResult : 1;
        });
        // 真的调用生产代码里的 RowMapper，顺带覆盖 id / device 两个字段的映射
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            executed.add(invocation.getArgument(0));
            if (!inviterFound) {
                return List.of();
            }
            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(inviterId);
            when(rs.getString("signup_device_hash")).thenReturn(inviterDevice);
            return List.of(mapper.mapRow(rs, 0));
        });
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    executed.add(sql);
                    // ensureInviteCode 的读取：返回已有个人码，避免走生成重试分支
                    return sql.contains("SELECT invite_code") ? CODE : null;
                });
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(null);

        service = new InviteService(jdbc, settingsService, quotaService);
    }

    @Test
    void invalidCodeFailsBeforeAnyRewardWrite() {
        inviterFound = false;

        assertThatThrownBy(() -> service.onRegistered(INVITEE, "nope", "203.0.113.9", DEVICE))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> {
                    BizException biz = (BizException) exception;
                    assertThat(biz.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(biz.details().toString()).contains("inviteCode");
                });

        assertThat(executed).noneMatch(sql -> sql.startsWith("INSERT INTO invite_reward"));
        verifyNoGrant();
    }

    @Test
    void selfInviteIsIgnored() {
        inviterId = INVITEE;

        service.onRegistered(INVITEE, CODE, "203.0.113.9", DEVICE);

        assertThat(executed).noneMatch(sql -> sql.contains("referred_by"));
        assertThat(executed).noneMatch(sql -> sql.startsWith("INSERT INTO invite_reward"));
        verifyNoGrant();
    }

    /** 同设备只建立推荐关系，不发奖励：否则一个人刷两个账号就能自邀套额度。 */
    @Test
    void sameDeviceRecordsRelationshipWithoutReward() {
        inviterDevice = DEVICE;

        service.onRegistered(INVITEE, CODE, "203.0.113.9", DEVICE);

        assertThat(executed).anyMatch(sql -> sql.contains("SET referred_by"));
        assertThat(executed).noneMatch(sql -> sql.startsWith("INSERT INTO invite_reward"));
        verifyNoGrant();
    }

    /**
     * 当日名额已满：必须靠 {@code todayCount < cap} 这一层拦住，而不是 cap 为 0 的短路——
     * 后者只能证明"关闭奖励时不发"，证明不了"名额用尽时不发"。
     */
    @Test
    void dailyCapReachedRewardsNothing() {
        when(settingsService.snapshot()).thenReturn(snapshot(1, 20));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    // countToday 的 SQL 带时区换算，countTotal 不带
                    String sql = invocation.getArgument(0);
                    return sql.contains("AT TIME ZONE") ? 1L : 5L;
                });

        service.onRegistered(INVITEE, CODE, "203.0.113.9", "another-device");

        assertThat(executed).anyMatch(sql -> sql.contains("SET referred_by"));
        verifyNoGrant();
    }

    @Test
    void disabledCapsRewardNothing() {
        when(settingsService.snapshot()).thenReturn(snapshot(0, 20));

        service.onRegistered(INVITEE, CODE, "203.0.113.9", "another-device");

        verifyNoGrant();
    }

    /** 同一被邀请人重复回调（ON CONFLICT DO NOTHING）不能再发一次奖励。 */
    @Test
    void duplicateInviteeDoesNotGrantTwice() {
        rewardInsertResult = 0;

        service.onRegistered(INVITEE, CODE, "203.0.113.9", "another-device");

        verifyNoGrant();
    }

    @Test
    void rewardedInviteGrantsBothSides() {
        service.onRegistered(INVITEE, CODE, "203.0.113.9", "another-device");

        verify(quotaService)
                .grant(
                        eq(INVITER),
                        eq(QuotaType.INTERVIEW),
                        eq(1),
                        eq("INVITE_REWARD"),
                        anyString(),
                        any(),
                        anyString());
        verify(quotaService)
                .grant(eq(INVITEE), eq(QuotaType.RESUME), eq(1), eq("INVITE_REWARD"), anyString(), any(), anyString());
    }

    /**
     * 互邀并发（A→B 与 B→A）靠固定加锁顺序避免死锁：必须先锁较小 id。
     */
    @Test
    void advisoryLocksAreTakenInAscendingIdOrder() {
        service.onRegistered(INVITEE, CODE, "203.0.113.9", "another-device");

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).query(eq("SELECT pg_advisory_xact_lock(?)"), any(ResultSetExtractor.class), eq(INVITEE));
        order.verify(jdbc).query(eq("SELECT pg_advisory_xact_lock(?)"), any(ResultSetExtractor.class), eq(INVITER));
    }

    /**
     * 个人码碰撞：唯一键冲突会把整个事务置为 aborted，必须回滚到 SAVEPOINT 再重试，
     * 否则后续语句全部失败、注册整体 500。这条是注册链路的关键分支。
     */
    @Test
    void inviteCodeCollisionRollsBackToSavepointAndRetries() {
        // 首次读取：还没有个人码
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    executed.add(invocation.getArgument(0));
                    return null;
                });
        doAnswer(invocation -> {
                    executed.add(invocation.getArgument(0));
                    return null;
                })
                .when(jdbc)
                .execute(anyString());

        int[] codeUpdates = {0};
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            executed.add(sql);
            if (sql.contains("SET invite_code")) {
                if (codeUpdates[0]++ == 0) {
                    throw new DuplicateKeyException("invite_code 冲突");
                }
                return 1;
            }
            return 1;
        });

        // 不带邀请码：只走"生成个人码"这一段
        service.onRegistered(INVITEE, null, "203.0.113.9", DEVICE);

        assertThat(executed).contains("SAVEPOINT invite_code_gen");
        assertThat(executed).contains("ROLLBACK TO SAVEPOINT invite_code_gen");
        assertThat(executed.stream()
                        .filter(sql -> sql.contains("SET invite_code"))
                        .count())
                .as("碰撞后必须重试一次")
                .isEqualTo(2);
    }

    private void verifyNoGrant() {
        verify(quotaService, never()).grant(anyLong(), any(), anyInt(), anyString(), anyString(), any(), anyString());
    }

    private static PlatformSettingsService.Snapshot snapshot(int inviteDailyCap, int inviteTotalCap) {
        return new PlatformSettingsService.Snapshot(
                true,
                3,
                5,
                0,
                0,
                1,
                1,
                inviteDailyCap,
                inviteTotalCap,
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
