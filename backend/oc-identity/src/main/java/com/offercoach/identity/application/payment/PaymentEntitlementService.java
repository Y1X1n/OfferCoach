package com.offercoach.identity.application.payment;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.port.PaymentEntitlementRepository;
import com.offercoach.identity.application.port.PaymentOrderFactsPort;
import com.offercoach.identity.application.port.QuotaRepository;
import com.offercoach.identity.application.port.SubscriptionRepository;
import com.offercoach.identity.application.quota.QuotaService;
import com.offercoach.identity.domain.quota.QuotaAccount;
import com.offercoach.identity.domain.quota.QuotaLedgerEntry;
import com.offercoach.identity.domain.quota.QuotaType;
import com.offercoach.identity.domain.quota.SubscriptionPlan;
import com.offercoach.pay.api.PayDtos;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付成功后的套餐与额度发放。
 *
 * <p>由 oc-pay 内部通知触发；{@code payment_entitlement.order_no} 唯一约束保证同一订单只发放一次。
 * 首次发放会结束当前生效订阅、切换套餐并追加减面试 / 简历解析额度，全程同一事务。</p>
 *
 * <p>内部令牌只证明调用方是受信服务，不证明通知内容真实；因此发放前必须回查 {@code payment_order}，
 * 校验订单已支付且用户、套餐、金额与通知完全一致，避免令牌泄漏或通知被篡改时凭空发放 PRO。</p>
 */
@Service
@RequiredArgsConstructor
public class PaymentEntitlementService {

    private static final Logger log = LoggerFactory.getLogger(PaymentEntitlementService.class);
    private static final String PURCHASE_REASON = "PLAN_PURCHASE";
    private static final String ORDER_REF_TYPE = "ORDER";
    private static final int DEFAULT_PERIOD_DAYS = 30;
    private static final String ORDER_PAID = "PAID";

    private final QuotaRepository quotaRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentEntitlementRepository entitlementRepository;
    private final PaymentOrderFactsPort orderFactsPort;
    private final QuotaService quotaService;

    @Transactional
    public PayDtos.EntitlementAck apply(PayDtos.EntitlementNotice notice) {
        if (notice == null || notice.orderNo() == null || notice.orderNo().isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("field", "orderNo"));
        }
        verifyOrderFacts(notice);
        boolean first = entitlementRepository.tryInsert(
                notice.orderNo(), notice.userId(), notice.planCode(), notice.amountCents());
        if (!first) {
            log.info("支付额度已发放，忽略重复通知：orderNo={}", notice.orderNo());
            return new PayDtos.EntitlementAck(false, true);
        }
        SubscriptionPlan plan = quotaRepository
                .findPlan(notice.planCode())
                .filter(SubscriptionPlan::active)
                .orElseThrow(
                        () -> new BizException(ErrorCode.VALIDATION_FAILED, Map.of("planCode", notice.planCode())));
        int periodDays = plan.periodDays() > 0 ? plan.periodDays() : DEFAULT_PERIOD_DAYS;
        LocalDate today = LocalDate.now();
        boolean hasQuota = quotaRepository.findQuota(notice.userId()).isPresent();
        if (!hasQuota) {
            // 新用户：直接按购买套餐初始化（含 PLAN_GRANT 流水），避免先发 FREE 再叠加。
            quotaService.initializeQuota(notice.userId(), plan.code());
        }
        quotaRepository.updatePlanPeriod(notice.userId(), plan.code(), today, today.plusDays(periodDays));
        if (hasQuota) {
            grant(notice, QuotaType.INTERVIEW, plan.interviewCredits());
            grant(notice, QuotaType.RESUME, plan.resumeCredits());
        }
        Instant now = Instant.now();
        subscriptionRepository.expireActive(notice.userId());
        subscriptionRepository.insertActive(notice.userId(), plan.code(), now, now.plus(Duration.ofDays(periodDays)));
        log.info(
                "支付额度发放完成：orderNo={} userId={} plan={} periodDays={}",
                notice.orderNo(),
                notice.userId(),
                plan.code(),
                periodDays);
        return new PayDtos.EntitlementAck(true, false);
    }

    private void grant(PayDtos.EntitlementNotice notice, QuotaType type, int credits) {
        if (credits <= 0) {
            return;
        }
        quotaRepository.increase(notice.userId(), type, credits);
        QuotaAccount after = quotaRepository.findQuota(notice.userId()).orElse(null);
        int balance = after == null ? credits : balanceOf(after, type);
        quotaRepository.insertLedgerIfAbsent(new QuotaLedgerEntry(
                null,
                notice.userId(),
                type.name(),
                credits,
                balance,
                PURCHASE_REASON,
                ORDER_REF_TYPE,
                null,
                "order-purchase:" + notice.orderNo() + ":" + type.name()));
    }

    private static int balanceOf(QuotaAccount quota, QuotaType type) {
        return type == QuotaType.INTERVIEW ? quota.interviewCredits() : quota.resumeCredits();
    }

    /**
     * 回查订单事实：只有真实已支付、且用户/套餐/金额完全一致的订单才允许发放。
     */
    private void verifyOrderFacts(PayDtos.EntitlementNotice notice) {
        PaymentOrderFactsPort.Facts facts =
                orderFactsPort.find(notice.orderNo()).orElse(null);
        if (facts == null) {
            throw new BizException(
                    ErrorCode.VALIDATION_FAILED, Map.of("orderNo", "订单不存在", "detail", "payment order not found"));
        }
        if (!ORDER_PAID.equals(facts.status())) {
            throw new BizException(
                    ErrorCode.VALIDATION_FAILED, Map.of("orderNo", "订单未支付", "detail", "status=" + facts.status()));
        }
        boolean sameUser = facts.userId() == notice.userId();
        boolean samePlan = facts.planCode() != null && facts.planCode().equals(notice.planCode());
        boolean sameAmount = facts.amountCents() == notice.amountCents();
        if (!sameUser || !samePlan || !sameAmount) {
            log.error(
                    "支付发放请求与订单事实不一致，已拒绝：orderNo={} noticeUser={} orderUser={} noticePlan={} orderPlan={}",
                    notice.orderNo(),
                    notice.userId(),
                    facts.userId(),
                    notice.planCode(),
                    facts.planCode());
            throw new BizException(
                    ErrorCode.VALIDATION_FAILED, Map.of("orderNo", "订单信息不一致", "detail", "order facts mismatch"));
        }
    }
}
