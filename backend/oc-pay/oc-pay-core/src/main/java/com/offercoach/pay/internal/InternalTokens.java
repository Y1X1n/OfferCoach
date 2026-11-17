package com.offercoach.pay.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部令牌比较工具：常量时间比较，避免时序侧信道。
 */
public final class InternalTokens {

    private InternalTokens() {}

    /**
     * 期望令牌为空视为未配置，任何调用都不通过（fail-closed）。
     */
    public static boolean matches(String expected, String provided) {
        if (expected == null || expected.isBlank() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
}
