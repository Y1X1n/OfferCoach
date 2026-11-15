package com.offercoach.ai.tools.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 工具网关纯逻辑：内部端点响应体解析与错误码识别。
 *
 * <p>夹具按 oc-ai-* 工具服务的内部响应形状（camelCase）构造；DeepSeek 原始 snake_case
 * 已在各服务内部转换（见 SearchController / AsrController 等）。</p>
 */
class AiToolGatewayTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseSearchMapsInternalPayload() {
        String json =
                """
                {"results":[
                  {"rerankScore":1.8,"title":"高级后端工程师","url":"https://jobs.example.com/1",
                   "snippet":"片段","summary":"摘要","content":"岗位职责与任职要求",
                   "siteName":"招聘站","siteIcon":"https://jobs.example.com/i.png",
                   "date":"2026-11-01","authority":4}
                ]}
                """;

        List<ToolDtos.SearchResult> results = AiToolGateway.parseSearch(mapper, json);

        assertThat(results).hasSize(1);
        ToolDtos.SearchResult first = results.get(0);
        assertThat(first.rerankScore()).isEqualTo(1.8);
        assertThat(first.title()).isEqualTo("高级后端工程师");
        assertThat(first.url()).isEqualTo("https://jobs.example.com/1");
        assertThat(first.content()).isEqualTo("岗位职责与任职要求");
        assertThat(first.siteName()).isEqualTo("招聘站");
        assertThat(first.siteIcon()).isEqualTo("https://jobs.example.com/i.png");
        assertThat(first.date()).isEqualTo("2026-11-01");
        assertThat(first.authority()).isEqualTo(4);
    }

    @Test
    void parseSearchUsesDefaultsWhenOptionalFieldsMissing() {
        List<ToolDtos.SearchResult> results = AiToolGateway.parseSearch(mapper, "{\"results\":[{\"title\":\"x\"}]}");

        assertThat(results).hasSize(1);
        ToolDtos.SearchResult only = results.get(0);
        assertThat(only.authority()).isEqualTo(-1);
        assertThat(only.siteName()).isNull();
        assertThat(only.siteIcon()).isNull();
        assertThat(only.date()).isNull();
    }

    @Test
    void parseSearchYieldsEmptyListWhenNoResults() {
        assertThat(AiToolGateway.parseSearch(mapper, "{}")).isEmpty();
    }

    @Test
    void parseAsrMapsResult() {
        ToolDtos.AsrResult result =
                AiToolGateway.parseAsr(mapper, "{\"requestId\":\"r1\",\"text\":\"你好\",\"durationMs\":21500}");

        assertThat(result.requestId()).isEqualTo("r1");
        assertThat(result.text()).isEqualTo("你好");
        assertThat(result.durationMs()).isEqualTo(21500);
    }

    @Test
    void parseAsrAllowsMissingDuration() {
        assertThat(AiToolGateway.parseAsr(mapper, "{\"text\":\"ok\"}").durationMs())
                .isNull();
    }

    @Test
    void parseOcrMapsPagesAndFillsMissingIndex() {
        String json = "{\"id\":\"o1\",\"pageCount\":2,\"pages\":["
                + "{\"index\":0,\"text\":\"第一页\",\"finishReason\":\"stop\"},"
                + "{\"text\":\"第二页\"}]}";

        ToolDtos.OcrResult result = AiToolGateway.parseOcr(mapper, json);

        assertThat(result.id()).isEqualTo("o1");
        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(result.pages()).hasSize(2);
        assertThat(result.pages().get(1).index()).isEqualTo(1);
        assertThat(result.pages().get(1).finishReason()).isEqualTo("stop");
    }

    @Test
    void parseFetchMapsTitleAndContent() {
        ToolDtos.FetchResponse response = AiToolGateway.parseFetch(mapper, "{\"title\":\"岗位\",\"content\":\"# 正文\"}");

        assertThat(response.title()).isEqualTo("岗位");
        assertThat(response.content()).isEqualTo("# 正文");
    }

    @Test
    void parseThrowsUpstreamFailedOnMalformedJson() {
        assertThatThrownBy(() -> AiToolGateway.parseAsr(mapper, "not-json"))
                .isInstanceOf(ToolException.class)
                .satisfies(exception ->
                        assertThat(((ToolException) exception).kind()).isEqualTo(ToolErrorKind.UPSTREAM_FAILED));
    }

    @Test
    void kindFromBodyPrefersDeclaredCode() {
        assertThat(AiToolGateway.kindFromBody(mapper, "{\"code\":\"BLOCKED\",\"message\":\"x\"}"))
                .isEqualTo(ToolErrorKind.BLOCKED);
        assertThat(AiToolGateway.kindFromBody(mapper, "{\"code\":\"NOT_CONFIGURED\"}"))
                .isEqualTo(ToolErrorKind.NOT_CONFIGURED);
        assertThat(AiToolGateway.kindFromBody(mapper, "{\"code\":\"BUSY\"}")).isEqualTo(ToolErrorKind.BUSY);
    }

    @Test
    void kindFromBodyIgnoresUnknownCodeAndNonJson() {
        assertThat(AiToolGateway.kindFromBody(mapper, "{\"code\":\"NOPE\"}")).isNull();
        assertThat(AiToolGateway.kindFromBody(mapper, "{\"message\":\"no code\"}"))
                .isNull();
        assertThat(AiToolGateway.kindFromBody(mapper, "plain text")).isNull();
        assertThat(AiToolGateway.kindFromBody(mapper, "")).isNull();
        assertThat(AiToolGateway.kindFromBody(mapper, null)).isNull();
    }
}
