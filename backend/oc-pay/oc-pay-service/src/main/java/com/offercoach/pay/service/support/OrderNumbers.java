package com.offercoach.pay.service.support;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 订单号 / 退款号生成：时间前缀 + 随机后缀，便于人工排查且不暴露自增 ID。
 */
public final class OrderNumbers {

    private static final DateTimeFormatter PREFIX = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private OrderNumbers() {}

    public static String orderNo() {
        return "OC" + LocalDateTime.now(ZoneOffset.ofHours(8)).format(PREFIX) + randomDigits(6);
    }

    public static String refundNo() {
        return "RF" + LocalDateTime.now(ZoneOffset.ofHours(8)).format(PREFIX) + randomDigits(6);
    }

    private static String randomDigits(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }
}
