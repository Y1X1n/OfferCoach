package com.offercoach.ai.search.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.ai.AiRuntimeConfigService;
import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.client.DeepSeekToolsClient;
import com.offercoach.ai.tools.config.ToolsProperties;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.internal.InternalAuth;
import com.offercoach.ai.tools.log.ToolCallLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 内部检索端点：鉴权、能力开关、入参校验与调用日志。
 */
class SearchControllerTest {

    private static final long USER_ID = 7L;

    private DeepSeekToolsClient client;
    private InternalAuth auth;
    private ToolCallLogger callLogger;
    private AiRuntimeConfigService runtimeConfig;
    private HttpServletRequest http;
    private SearchController controller;

    @BeforeEach
    void setUp() {
        client = mock(DeepSeekToolsClient.class);
        auth = mock(InternalAuth.class);
        callLogger = mock(ToolCallLogger.class);
        runtimeConfig = mock(AiRuntimeConfigService.class);
        http = mock(HttpServletRequest.class);
        when(auth.requireUser(http)).thenReturn(USER_ID);
        when(runtimeConfig.tools())
                .thenReturn(new AiRuntimeConfigService.ToolSettings(true, true, true, true, true, "stella"));
        controller = new SearchController(
                client, auth, callLogger, new ObjectMapper(), runtimeConfig, new ToolsProperties());
    }

    @Test
    void unauthorizedRequestPropagatesBeforeAnyWork() {
        when(auth.requireUser(http)).thenThrow(new ToolException(ToolErrorKind.UNAUTHORIZED));

        assertThatThrownBy(() -> controller.search(request("后端", 5), http))
                .isInstanceOf(ToolException.class)
                .satisfies(exception ->
                        assertThat(((ToolException) exception).kind()).isEqualTo(ToolErrorKind.UNAUTHORIZED));
    }

    @Test
    void disabledSearchIsReportedAsNotConfigured() {
        when(runtimeConfig.tools())
                .thenReturn(new AiRuntimeConfigService.ToolSettings(true, true, true, false, true, "stella"));

        assertThatThrownBy(() -> controller.search(request("后端", 5), http))
                .isInstanceOf(ToolException.class)
                .satisfies(exception ->
                        assertThat(((ToolException) exception).kind()).isEqualTo(ToolErrorKind.NOT_CONFIGURED));
    }

    @Test
    void blankQueryRejected() {
        assertThatThrownBy(() -> controller.search(request("   ", 5), http))
                .isInstanceOf(ToolException.class)
                .satisfies(exception ->
                        assertThat(((ToolException) exception).kind()).isEqualTo(ToolErrorKind.INVALID_INPUT));
    }

    @Test
    void oversizedQueryRejected() {
        assertThatThrownBy(() -> controller.search(request("x".repeat(129), 5), http))
                .isInstanceOf(ToolException.class)
                .satisfies(exception ->
                        assertThat(((ToolException) exception).kind()).isEqualTo(ToolErrorKind.INVALID_INPUT));
    }

    @Test
    void outOfRangeCountRejected() {
        assertThatThrownBy(() -> controller.search(request("后端", 0), http))
                .isInstanceOf(ToolException.class)
                .satisfies(exception ->
                        assertThat(((ToolException) exception).kind()).isEqualTo(ToolErrorKind.INVALID_INPUT));
    }

    @Test
    void successMapsResultsAndLogsSuccess() {
        when(client.search(eq("后端"), eq(5), isNull(), eq(false), isNull()))
                .thenReturn("{\"results\":[{\"rerankScore\":1.2,\"title\":\"岗位\",\"url\":\"https://x/1\"}]}");

        ToolDtos.SearchResponse response = controller.search(request("后端", 5), http);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).title()).isEqualTo("岗位");
        verify(callLogger).success(eq(USER_ID), eq("SEARCH"), eq("tools-search"), anyLong(), isNull(), isNull());
    }

    @Test
    void upstreamFailureIsLoggedAndRethrown() {
        when(client.search(eq("后端"), eq(5), isNull(), eq(false), isNull()))
                .thenThrow(new ToolException(ToolErrorKind.UPSTREAM_BUSY));

        assertThatThrownBy(() -> controller.search(request("后端", 5), http)).isInstanceOf(ToolException.class);

        verify(callLogger).failed(eq(USER_ID), eq("SEARCH"), eq("tools-search"), anyLong(), eq("UPSTREAM_BUSY"));
    }

    private static ToolDtos.SearchRequest request(String query, Integer count) {
        return new ToolDtos.SearchRequest(query, count, null, null, null);
    }
}
