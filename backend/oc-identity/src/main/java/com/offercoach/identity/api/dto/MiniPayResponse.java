package com.offercoach.identity.api.dto;

import com.offercoach.pay.api.PayDtos;

/**
 * 小程序内支付响应；tradeNo 直接交给 my.tradePay。
 */
public record MiniPayResponse(String orderNo, String tradeNo, String status) {

    public static MiniPayResponse from(PayDtos.MiniPayView view) {
        return new MiniPayResponse(view.orderNo(), view.tradeNo(), view.status());
    }
}
