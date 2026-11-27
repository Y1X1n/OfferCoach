package com.offercoach.identity.api.dto;

/**
 * POST /api/v1/orders 请求体。
 *
 * @param planCode 套餐编码
 * @param channel  支付渠道：wechat / alipay / mock
 * @param payMode  支付形态：QR 扫码（缺省） / MINI 小程序内调起
 */
public record CreateOrderRequest(String planCode, String channel, String payMode) {}
