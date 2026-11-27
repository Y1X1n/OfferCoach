package com.offercoach.pay.service.channel;

import com.offercoach.pay.service.domain.PaymentOrder;

/**
 * 小程序内支付扩展点：用一次性授权码换取买家标识，再创建可由 {@code my.tradePay} 调起的交易。
 *
 * <p>与 {@link PaymentChannel#prepay} 的扫码下单互斥——同一 {@code out_trade_no} 在支付宝侧只能存在一笔交易，
 * 因此小程序订单在创建时不预生成扫码交易。</p>
 */
public interface MiniAppPaymentChannel {

    /**
     * 创建小程序内支付交易。
     *
     * @param order    本地待支付订单
     * @param authCode 客户端 {@code my.getAuthCode} 取得的一次性授权码
     * @return 支付宝交易号，直接传给 {@code my.tradePay({tradeNO})}
     */
    String miniPrepay(PaymentOrder order, String authCode);
}
