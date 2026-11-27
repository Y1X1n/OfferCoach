package com.offercoach.interview.qbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offercoach.interview.qbank.QuestionRetriever.RetrieveQuery;
import com.offercoach.interview.support.JsonCodec;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QuestionBankRetrieverTest {

    private final QuestionBankMapper mapper = mock(QuestionBankMapper.class);
    private final QuestionBankRetriever retriever =
            new QuestionBankRetriever(mapper, new JsonCodec(new ObjectMapper()));

    @Test
    void industryFamilyWithoutOwnQuestionsDoesNotFallBackToGenericBank() {
        when(mapper.selectPublishedByFamily(eq("医疗健康"), anyInt())).thenReturn(List.of());

        List<QuestionRetriever.RetrievedQuestion> result =
                retriever.retrieve(new RetrieveQuery("医疗健康", Set.of("静脉输液"), 10));

        assertThat(result).isEmpty();
        verify(mapper, never()).selectPublishedByFamily(eq("通用"), anyInt());
        verify(mapper, never()).selectPublished(anyInt());
    }

    @Test
    void technicalFamilyStillUsesGenericSupplement() {
        when(mapper.selectPublishedByFamily(eq("后端"), anyInt())).thenReturn(List.of());
        when(mapper.selectPublishedByFamily(eq("通用"), anyInt())).thenReturn(List.of(row(1L, "通用题")));

        List<QuestionRetriever.RetrievedQuestion> result =
                retriever.retrieve(new RetrieveQuery("后端", Set.of("Java"), 10));

        assertThat(result).hasSize(1);
        verify(mapper).selectPublishedByFamily(eq("通用"), anyInt());
    }

    private static QuestionBankDO row(long id, String stem) {
        QuestionBankDO row = new QuestionBankDO();
        row.setId(id);
        row.setTitle(stem);
        row.setJobFamily("通用");
        row.setDimension("DEPTH");
        row.setDifficulty("MEDIUM");
        row.setTagsJoined("");
        row.setStem(stem);
        row.setScoringPoints("[]");
        return row;
    }
}
