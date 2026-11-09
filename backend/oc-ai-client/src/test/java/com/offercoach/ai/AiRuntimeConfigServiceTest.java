package com.offercoach.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

/**
 * 管理端 baseUrl 校验：仅 https，本机地址允许 http。
 */
class AiRuntimeConfigServiceTest {

    @Test
    void acceptsHttpsAndTrimsTrailingSlash() {
        assertEquals(
                "https://api.deepseek.com/v1",
                AiRuntimeConfigService.validateBaseUrl(" https://api.deepseek.com/v1/ ", false));
    }

    @Test
    void allowsHttpForLocalhostAndLoopbackOnly() {
        assertEquals(
                "http://127.0.0.1:8080/v1", AiRuntimeConfigService.validateBaseUrl("http://127.0.0.1:8080/v1", true));
        assertEquals("http://localhost:8000", AiRuntimeConfigService.validateBaseUrl("http://localhost:8000", true));
        BizException exception = assertThrows(
                BizException.class, () -> AiRuntimeConfigService.validateBaseUrl("http://api.example.com/v1", false));
        assertEquals(ErrorCode.VALIDATION_FAILED, exception.errorCode());
        assertThrows(BizException.class, () -> AiRuntimeConfigService.validateBaseUrl("https://127.0.0.1/v1", false));
        assertThrows(BizException.class, () -> AiRuntimeConfigService.validateBaseUrl("https://10.0.0.1/v1", false));
        assertThrows(BizException.class, () -> AiRuntimeConfigService.validateBaseUrl("https://127.0.0.2/v1", false));
        assertThrows(BizException.class, () -> AiRuntimeConfigService.validateBaseUrl("https://0.0.0.0/v1", false));
    }

    @Test
    void rejectsUnsupportedSchemeAndMalformedUrl() {
        assertThrows(BizException.class, () -> AiRuntimeConfigService.validateBaseUrl("ftp://example.com/v1", false));
        assertThrows(BizException.class, () -> AiRuntimeConfigService.validateBaseUrl("api.deepseek.com/v1", false));
        assertThrows(BizException.class, () -> AiRuntimeConfigService.validateBaseUrl("https://", false));
        assertThrows(BizException.class, () -> AiRuntimeConfigService.validateBaseUrl(" ", false));
        assertThrows(BizException.class, () -> AiRuntimeConfigService.validateBaseUrl(null, false));
    }

    @Test
    void detectsNonPublicAddressesWithoutDns() throws UnknownHostException {
        for (String literal : new String[] {
            "127.0.0.2",
            "10.0.0.1",
            "172.16.1.1",
            "192.168.1.1",
            "169.254.169.254",
            "100.100.100.200",
            "100.64.0.1",
            "0.0.0.0",
            "198.18.0.1",
            "198.51.100.9",
            "203.0.113.9",
            "224.0.0.1",
            "fc00::1",
            "fd12:3456::1",
            "fe80::1",
            "::1"
        }) {
            assertTrue(
                    AiRuntimeConfigService.isNonPublicAddress(InetAddress.getByName(literal)),
                    literal + " should be non-public");
        }
        assertFalse(AiRuntimeConfigService.isNonPublicAddress(InetAddress.getByName("8.8.8.8")));
        assertFalse(AiRuntimeConfigService.isNonPublicAddress(InetAddress.getByName("2001:4860:4860::8888")));
    }
}
