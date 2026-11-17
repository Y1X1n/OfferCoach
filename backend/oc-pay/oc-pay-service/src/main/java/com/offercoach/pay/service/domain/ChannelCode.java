package com.offercoach.pay.service.domain;

import java.util.Locale;

/**
 * 支付渠道编码；与 {@code payment_order.channel} 的 CHECK 值一致。
 */
public enum ChannelCode {
    WECHAT("wechat", "QR"),
    ALIPAY("alipay", "QR"),
    MOCK("mock", "MOCK");

    private final String code;
    private final String method;

    ChannelCode(String code, String method) {
        this.code = code;
        this.method = method;
    }

    /**
     * 渠道小写编码，用于接口入参与回调路径。
     */
    public String code() {
        return code;
    }

    /**
     * 首选支付方式：QR 扫码 / MOCK 本地模拟。
     */
    public String method() {
        return method;
    }

    /**
     * 由接口入参解析渠道；未知或为空回退 MOCK 之外的 null 由调用方判定。
     */
    public static ChannelCode fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        for (ChannelCode code : values()) {
            if (code.code.equals(normalized)) {
                return code;
            }
        }
        return null;
    }
}
