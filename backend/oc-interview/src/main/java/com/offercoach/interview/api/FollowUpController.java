package com.offercoach.interview.api;

import com.offercoach.identity.security.CurrentUser;
import com.offercoach.interview.session.application.FollowUpService;
import com.offercoach.interview.session.application.FollowUpService.FollowUpQuestion;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 单题追问端点；每场最多 2 道，计入题量、不额外扣额度。
 */
@RestController
@RequestMapping("/api/v1/interviews")
public class FollowUpController {

    private final FollowUpService followUpService;
    private final CurrentUser currentUser;

    public FollowUpController(FollowUpService followUpService, CurrentUser currentUser) {
        this.followUpService = followUpService;
        this.currentUser = currentUser;
    }

    @PostMapping("/{sessionId}/questions/{questionId}/follow-up")
    public FollowUpResponse followUp(
            @PathVariable("sessionId") long sessionId, @PathVariable("questionId") long questionId) {
        FollowUpQuestion question = followUpService.add(currentUser.userId(), sessionId, questionId);
        return new FollowUpResponse(
                Long.toString(question.id()),
                question.seq(),
                question.text(),
                question.dimension(),
                question.difficulty(),
                question.referencePoints());
    }

    public record FollowUpResponse(
            String id, int seq, String text, String dimension, String difficulty, List<String> referencePoints) {}
}
