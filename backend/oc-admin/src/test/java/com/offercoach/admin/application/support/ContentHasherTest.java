package com.offercoach.admin.application.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Prompt 内容摘要使用标准 SHA-256。
 */
class ContentHasherTest {

    @Test
    void hashesEmptyStringWithKnownVector() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ContentHasher.sha256Hex(""));
    }

    @Test
    void hashesAsciiWithKnownVector() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ContentHasher.sha256Hex("abc"));
    }

    @Test
    void hashesChineseContentAsLowerHex() {
        String hash = ContentHasher.sha256Hex("你是资深技术面试官");
        assertEquals(64, hash.length());
        assertEquals(hash.toLowerCase(), hash);
    }
}
