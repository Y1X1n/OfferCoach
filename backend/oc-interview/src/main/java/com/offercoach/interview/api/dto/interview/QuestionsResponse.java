package com.offercoach.interview.api.dto.interview;

import java.util.List;

/**
 * 题目列表包装；未到题不返回。
 */
public record QuestionsResponse(List<QuestionItemResponse> items) {}
