package com.offercoach.pay.api;

import java.time.Instant;
import java.util.List;

/**
 * 支付服务与 oc-app 之间的共享契约 DTO。
 *
 * <p>金额一律为整数分（{@code amountCents}）；时间戳为 UTC ISO-8601。渠道编码见
 * {@code WECHAT / ALIPAY / MOCK}，订单状态见 {@code CREATED / PAID / FAILED / REFUNDED / CLOSED}。</p>
 */
public final class PayDtos {

    private PayDtos() {}

    /**
     * 创建支付订单（oc-app → oc-pay 内部接口）。
     *
     * @param userId         下单用户，作为额度发放归属
     * @param planCode       套餐编码，引用 subscription_plan.code
     * @param subject        渠道展示的商品标题
     * @param amountCents    应付金额（分）
     * @param currency       币种，默认 CNY
     * @param channel        支付渠道：wechat / alipay / mock
     * @param idempotencyKey 幂等键；同一用户内唯一，重复请求返回首次订单
     * @param clientIp       用户下单来源 IP，供渠道风控
     * @param payMode        支付形态：QR 扫码（缺省） / MINI 小程序内调起；MINI 不预生成扫码交易
     */
    public record CreateOrderRequest(
            long userId,
            String planCode,
            String subject,
            int amountCents,
            String currency,
            String channel,
            String idempotencyKey,
            String clientIp,
            String payMode) {

        /** 兼容旧调用方：缺省按扫码下单。 */
        public CreateOrderRequest(
                long userId,
                String planCode,
                String subject,
                int amountCents,
                String currency,
                String channel,
                String idempotencyKey,
                String clientIp) {
            this(userId, planCode, subject, amountCents, currency, channel, idempotencyKey, clientIp, null);
        }
    }

    /**
     * 小程序内支付请求：{@code authCode} 由客户端 {@code my.getAuthCode} 一次性取得。
     */
    public record MiniPayRequest(String authCode) {}

    /**
     * 小程序内支付结果：{@code tradeNo} 直接交给 {@code my.tradePay}。
     */
    public record MiniPayView(String orderNo, String tradeNo, String status) {}

    /**
     * 订单视图；前端据此展示二维码/状态。
     */
    public record OrderView(
            long orderId,
            String orderNo,
            long userId,
            String planCode,
            String subject,
            int amountCents,
            String currency,
            String channel,
            String channelMethod,
            String status,
            String codeUrl,
            Instant expiresAt,
            Instant paidAt,
            int refundedCents,
            Instant createdAt,
            Instant updatedAt) {}

    /**
     * 发起退款（oc-app → oc-pay）。
     */
    public record RefundRequest(int amountCents, String reason, String idempotencyKey) {}

    /**
     * 退款结果。
     */
    public record RefundView(
            String refundNo,
            String orderNo,
            int amountCents,
            String status,
            String channelRefundId,
            Instant createdAt) {}

    /**
     * 单渠道可用性：是否启用与首选的支付方式（QR / PAGE / MOCK）。
     */
    public record ChannelInfo(String channel, boolean enabled, String method) {}

    /**
     * 渠道可用性总览；前端按此决定展示哪些支付方式。
     */
    public record ChannelOverview(List<ChannelInfo> channels) {}

    /**
     * 支付成功后向 oc-app 下发的额度发放通知（oc-pay → oc-app 内部接口）。
     */
    public record EntitlementNotice(
            String orderNo,
            long userId,
            String planCode,
            int amountCents,
            String currency,
            String channel,
            String providerTxnId,
            Instant paidAt) {}

    /**
     * 额度发放回执；{@code alreadyApplied} 表示此前已发放（幂等命中）。
     */
    public record EntitlementAck(boolean applied, boolean alreadyApplied) {}

    /**
     * 渠道回调应答体；微信要求 {@code {"code":"SUCCESS"}}，支付宝要求纯文本 {@code success}。
     */
    public record NotifyAck(String code, String message) {}
}
