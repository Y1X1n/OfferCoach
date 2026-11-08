package com.offercoach.interview.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuestionPlanTest {

    @Test
    void mixedPlanKeepsExactSlotCount() {
        for (int count = 1; count <= 20; count++) {
            assertThat(QuestionPlan.of(count, "MIXED").slots()).hasSize(count);
        }
    }

    @Test
    void singleMixedQuestionIsSupported() {
        QuestionPlan plan = QuestionPlan.of(1, "MIXED");

        assertThat(plan.slots()).hasSize(1);
        assertThat(plan.bankMinimum()).isEqualTo(1);
    }

    @Test
    void fixedDifficultyKeepsAllSlots() {
        assertThat(QuestionPlan.of(5, "HARD").slots()).hasSize(5).allSatisfy(slot -> assertThat(slot.difficulty())
                .isEqualTo("HARD"));
    }
}
