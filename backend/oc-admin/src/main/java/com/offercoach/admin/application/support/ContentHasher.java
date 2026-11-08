package com.offercoach.admin.application.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Prompt 内容摘要：SHA-256 小写十六进制，用于版本内容寻址。
 */
public final class ContentHasher {

    private ContentHasher() {}

    public static String sha256Hex(String content) {
        if (content == null) {
            throw new IllegalArgumentException("content 不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
