package com.offercoach.identity.api.dto;

/**
 * 小程序内支付请求；authCode 由 my.getAuthCode 一次性取得。
 */
public record MiniPayRequest(String authCode) {}
