package com.offercoach.interview.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.offercoach.ai.tools.api.ToolDtos;
import com.offercoach.ai.tools.error.ToolErrorKind;
import com.offercoach.ai.tools.error.ToolException;
import com.offercoach.ai.tools.gateway.AiToolGateway;
import com.offercoach.interview.session.application.QuestionContextBuilder.QuestionContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndustryQuestionSearchServiceTest {

    private final AiToolGateway gateway = mock(AiToolGateway.class);

    @Test
    void lookupBuildsIndustryQueryAndMapsEvidence() {
        when(gateway.search(anyLong(), anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(List.of(
                        new ToolDtos.SearchResult(
                                1.2, "护理岗面试常考题", "https://a.example/1", "静脉输液与院感管理", "", "", "牛客", "", "2026-11-01", 3),
                        new ToolDtos.SearchResult(
                                0.9, "护士面经", "https://a.example/2", "三查七对与交接班", "", "", "知乎", "", "2026-10-01", 2)));

        IndustryQuestionSearchService service = new IndustryQuestionSearchService(gateway, true, 5);
        QuestionContext context =
                new QuestionContext("医疗健康", List.of("静脉输液", "院感"), List.of("静脉输液", "院感"), List.of(), "3 年三甲医院经验", "护士");

        var result = service.lookup(7L, context);

        assertThat(result).isPresent();
        assertThat(result.get().query()).contains("护士").contains("静脉输液").contains("面经");
        assertThat(result.get().items()).hasSize(2);
        assertThat(result.get().items().get(0).site()).isEqualTo("牛客");
        assertThat(result.get().brief()).contains("护理岗面试常考题").contains("三查七对");
    }

    @Test
    void lookupFallsBackToKeywordsWhenTitleBlank() {
        when(gateway.search(anyLong(), anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(List.of(
                        new ToolDtos.SearchResult(1.0, "题", "https://a.example/1", "摘要", "", "", "站点", "", "", 1)));

        IndustryQuestionSearchService service = new IndustryQuestionSearchService(gateway, true, 5);
        QuestionContext context = new QuestionContext("通用", List.of("成本核算"), List.of(), List.of(), "", " ");

        var result = service.lookup(1L, context);

        assertThat(result).isPresent();
        assertThat(result.get().query()).contains("成本核算");
    }

    @Test
    void lookupReturnsEmptyWhenGatewayFails() {
        when(gateway.search(anyLong(), anyString(), anyInt(), any(), anyBoolean()))
                .thenThrow(new ToolException(ToolErrorKind.UPSTREAM_FAILED, "upstream down"));

        IndustryQuestionSearchService service = new IndustryQuestionSearchService(gateway, true, 5);

        assertThat(service.lookup(1L, context())).isEmpty();
    }

    @Test
    void lookupReturnsEmptyWhenDisabled() {
        IndustryQuestionSearchService service = new IndustryQuestionSearchService(gateway, false, 5);

        assertThat(service.lookup(1L, context())).isEmpty();
    }

    @Test
    void lookupReturnsEmptyWhenNoResults() {
        when(gateway.search(anyLong(), anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(List.of());

        IndustryQuestionSearchService service = new IndustryQuestionSearchService(gateway, true, 5);

        assertThat(service.lookup(1L, context())).isEmpty();
    }

    private static QuestionContext context() {
        return new QuestionContext("后端", List.of("Java"), List.of("Java"), List.of(), "summary", "后端工程师");
    }
}
