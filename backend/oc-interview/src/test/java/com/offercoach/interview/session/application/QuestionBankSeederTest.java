package com.offercoach.interview.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offercoach.interview.qbank.QuestionBankMapper;
import com.offercoach.interview.session.application.QuestionContextBuilder.QuestionContext;
import com.offercoach.interview.support.JsonCodec;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QuestionBankSeederTest {

    private final QuestionBankMapper mapper = mock(QuestionBankMapper.class);
    private final QuestionBankSeeder seeder = new QuestionBankSeeder(mapper, new JsonCodec(new ObjectMapper()));

    @Test
    void seedsGeneratedQuestionAsDraftWithIndustryFamily() {
        when(mapper.insertGenerated(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyLong()))
                .thenReturn(1);

        int inserted = seeder.seed(
                7L,
                new QuestionContext("医疗健康", List.of("静脉输液", "院感", "交接班"), List.of(), List.of(), "", "护士"),
                List.of(new QuestionBankSeeder.SeedQuestion(
                        "DEPTH",
                        "MEDIUM",
                        "夜班交接时如何保证危重患者信息不遗漏？",
                        List.of("交接流程", "核对机制"),
                        "考察交接班规范",
                        "https://a.example/1")));

        assertThat(inserted).isEqualTo(1);
        verify(mapper)
                .insertGenerated(
                        eq("夜班交接时如何保证危重患者信息不遗漏？"),
                        eq("医疗健康"),
                        eq("DEPTH"),
                        eq("MEDIUM"),
                        eq("静脉输液\u001f院感\u001f交接班"),
                        eq("夜班交接时如何保证危重患者信息不遗漏？"),
                        anyString(),
                        eq("ai-search"),
                        eq("医疗健康"),
                        eq("https://a.example/1"),
                        eq(7L));
    }

    @Test
    void seedFailureIsSwallowed() {
        when(mapper.insertGenerated(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyLong()))
                .thenThrow(new RuntimeException("db down"));

        int inserted = seeder.seed(
                1L,
                new QuestionContext("销售", List.of("大客户"), List.of(), List.of(), "", "销售经理"),
                List.of(new QuestionBankSeeder.SeedQuestion("FIT", "EASY", "如何开拓大客户？", List.of(), "", null)));

        assertThat(inserted).isZero();
    }

    @Test
    void skipsBlankQuestions() {
        int inserted = seeder.seed(
                1L,
                new QuestionContext("通用", List.of(), List.of(), List.of(), "", "岗位"),
                List.of(new QuestionBankSeeder.SeedQuestion("FIT", "EASY", "  ", List.of(), "", null)));

        assertThat(inserted).isZero();
    }
}
