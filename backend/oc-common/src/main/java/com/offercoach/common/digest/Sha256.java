package com.offercoach.common.digest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 内容摘要工具：统一 SHA-256 十六进制摘要，供缓存键与去重哈希复用。
 */
public final class Sha256 {

    private Sha256() {}

    /**
     * 返回 UTF-8 文本的 SHA-256 小写十六进制摘要；{@code null} 按空串处理。
     */
    public static String hex(String value) {
        byte[] input = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
