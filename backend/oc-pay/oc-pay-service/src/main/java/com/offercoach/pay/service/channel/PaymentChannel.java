package com.offercoach.pay.service.channel;

import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.PaymentOrder;
import com.offercoach.pay.service.domain.PaymentRefund;
import java.time.Instant;
import java.util.Map;

/**
 * 支付渠道适配器 SPI：下单、查询、关单、退款与回调解析。
 *
 * <p>实现方只负责与渠道对接；订单状态、幂等与额度发放由应用层统一处理。</p>
 */
public interface PaymentChannel {

    /**
     * 渠道编码。
     */
    ChannelCode code();

    /**
     * 渠道是否已启用且凭据齐备。
     */
    boolean available();

    /**
     * 渠道回调路径，例如 {@code /pay/notify/wechat}。
     */
    String notifyPath();

    /**
     * 渠道下单，返回二维码链接或预支付标识。
     */
    Prepay prepay(PaymentOrder order, String clientIp);

    /**
     * 主动查询订单支付状态（回调缺失时的兜底）。
     */
    ChannelOrderState query(PaymentOrder order);

    /**
     * 关闭未支付订单。
     *
     * <p>返回 {@code true} 表示渠道已确认该订单不可再支付（或渠道无需关单），调用方可将本地订单置为 CLOSED；
     * 返回 {@code false} 表示无法确认（渠道未配置、网络失败、渠道异常），调用方必须保留待支付状态等待下轮重试，
     * 否则用户事后完成支付会变成「已付款但本地已关单」的资损场景。</p>
     */
    boolean close(PaymentOrder order);

    /**
     * 发起退款。
     */
    RefundOutcome refund(PaymentOrder order, PaymentRefund refund);

    /**
     * 解析并验签渠道回调，返回归一化结果。
     */
    ChannelNotify parseNotify(Map<String, String> headers, String body);

    /**
     * 回调应答体；微信要求的 {@code {"code":"SUCCESS"}} 或支付宝的纯文本 {@code success} 由调用方按渠道返回。
     */
    String successResponseBody();

    /**
     * 渠道下单结果。
     */
    record Prepay(String codeUrl, String prepayId) {}

    /**
     * 渠道订单状态；{@code amountCents} 为渠道侧金额（渠道未返回时为 null），用于对账时交叉校验。
     */
    record ChannelOrderState(
            boolean paid, String providerTxnId, Instant paidAt, String tradeState, Integer amountCents) {}

    /**
     * 退款结果。
     */
    record RefundOutcome(boolean success, String channelRefundId, String tradeState, String message) {}

    /**
     * 归一化回调：无论渠道差异，应用层只需 orderNo + 是否支付 + 金额 + 渠道流水号。
     */
    record ChannelNotify(
            String orderNo,
            String providerTxnId,
            boolean paid,
            Integer amountCents,
            String tradeState,
            String eventType,
            Instant paidAt) {}
}
