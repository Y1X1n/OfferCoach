package com.offercoach.pay.service.domain;

/**
 * 支付异常类型：资金已发生但权益或订单状态无法自动收敛的场景。
 *
 * <p>写入 {@code payment_exception} 后需人工跟进（退款、补发或忽略），不可静默丢弃。</p>
 */
public enum PaymentExceptionKind {
    /** 已支付回调命中非待支付订单（多为本地已关单后用户仍完成支付）。 */
    PAID_ON_INACTIVE_ORDER,
    /** 回调或对账金额与订单金额不一致。 */
    AMOUNT_MISMATCH,
    /** 回调渠道与订单渠道不一致。 */
    CHANNEL_MISMATCH,
    /** 回调携带的订单号在本地不存在。 */
    UNKNOWN_ORDER,
    /** 订单过期后无法确认渠道已关单，已置 FAILED 等待人工核对。 */
    CLOSE_UNCERTAIN,
    /** 额度发放被 oc-app 永久拒绝（4xx），重试不会成功。 */
    ENTITLEMENT_REJECTED,
    /** 额度发放重试次数耗尽。 */
    ENTITLEMENT_DEAD;

    public String code() {
        return name();
    }
}
