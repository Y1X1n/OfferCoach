package com.offercoach.ai;

/**
 * AI 调用场景，用于日志与档位选择；新增场景须同步 {@code ai_call_log.scene} 口径与前端筛选。
 */
public enum AiScene {
    RESUME_PARSE,
    JD_ANALYZE,
    JD_MATCH,
    QUESTION_GEN,
    FEEDBACK,
    REPORT,
    ASR,
    TTS,
    OCR,
    SEARCH,
    FETCH
}
