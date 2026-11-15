package com.offercoach.ai.tools.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * 工具客户端纯逻辑：URI 拼接与上游错误分类。
 */
class DeepSeekToolsClientTest {

    @Test
    void uriOfKeepsV1WhenBaseAlreadyEndsWithV1() {
        assertThat(DeepSeekToolsClient.uriOf("https://api.deepseek.com/v1", "/tools/asr"))
                .isEqualTo(URI.create("https://api.deepseek.com/v1/tools/asr"));
    }

    @Test
    void uriOfAppendsV1WhenBaseIsBare() {
        assertThat(DeepSeekToolsClient.uriOf("https://api.deepseek.com", "/tools/search"))
                .isEqualTo(URI.create("https://api.deepseek.com/v1/tools/search"));
    }

    @Test
    void uriOfTrimsTrailingSlash() {
        assertThat(DeepSeekToolsClient.uriOf("https://api.deepseek.com/v1/", "/tools/tts"))
                .isEqualTo(URI.create("https://api.deepseek.com/v1/tools/tts"));
    }

    @Test
    void uriOfThrowsNotConfiguredForInvalidBase() {
        assertThatThrownBy(() -> DeepSeekToolsClient.uriOf("http://bad host", "/tools/tts"))
                .isInstanceOf(ToolException.class)
                .satisfies(exception ->
                        assertThat(((ToolException) exception).kind()).isEqualTo(ToolErrorKind.NOT_CONFIGURED));
    }

    @Test
    void classifyUpstreamMapsDocumentedStatuses() {
        assertThat(DeepSeekToolsClient.classifyUpstream(400, "bad request")).isEqualTo(ToolErrorKind.INVALID_INPUT);
        assertThat(DeepSeekToolsClient.classifyUpstream(400, "Content Exists Risk"))
                .isEqualTo(ToolErrorKind.CONTENT_REJECTED);
        assertThat(DeepSeekToolsClient.classifyUpstream(402, null)).isEqualTo(ToolErrorKind.INSUFFICIENT_BALANCE);
        assertThat(DeepSeekToolsClient.classifyUpstream(403, null)).isEqualTo(ToolErrorKind.BLOCKED);
        assertThat(DeepSeekToolsClient.classifyUpstream(404, null)).isEqualTo(ToolErrorKind.NO_CONTENT);
        assertThat(DeepSeekToolsClient.classifyUpstream(408, null)).isEqualTo(ToolErrorKind.UPSTREAM_TIMEOUT);
        assertThat(DeepSeekToolsClient.classifyUpstream(413, null)).isEqualTo(ToolErrorKind.TOO_LARGE);
        assertThat(DeepSeekToolsClient.classifyUpstream(429, null)).isEqualTo(ToolErrorKind.UPSTREAM_BUSY);
        assertThat(DeepSeekToolsClient.classifyUpstream(503, null)).isEqualTo(ToolErrorKind.UPSTREAM_BUSY);
        assertThat(DeepSeekToolsClient.classifyUpstream(504, null)).isEqualTo(ToolErrorKind.UPSTREAM_TIMEOUT);
        assertThat(DeepSeekToolsClient.classifyUpstream(500, null)).isEqualTo(ToolErrorKind.UPSTREAM_FAILED);
    }

    @Test
    void retryableKindsMatchGatewayRetryPolicy() {
        assertThat(ToolErrorKind.UPSTREAM_BUSY.retryable()).isTrue();
        assertThat(ToolErrorKind.UPSTREAM_TIMEOUT.retryable()).isTrue();
        assertThat(ToolErrorKind.UPSTREAM_FAILED.retryable()).isTrue();
        assertThat(ToolErrorKind.INVALID_INPUT.retryable()).isFalse();
        assertThat(ToolErrorKind.NOT_CONFIGURED.retryable()).isFalse();
    }
}
