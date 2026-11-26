-- AI 调用关联会话与 Trace：此前只能按用户/场景查，报告或反馈异常时无法从会话回溯到具体模型调用。
-- 所有列允许为空，兼容历史数据；session_id 不建外键，避免日志表被业务数据删除阻塞。
ALTER TABLE ai_call_log
    ADD COLUMN session_id     BIGINT,
    ADD COLUMN request_id     VARCHAR(64),
    ADD COLUMN trace_id       VARCHAR(64),
    ADD COLUMN prompt_version VARCHAR(64);

CREATE INDEX idx_ai_call_session ON ai_call_log (session_id, created_at DESC);
CREATE INDEX idx_ai_call_request ON ai_call_log (request_id);
CREATE INDEX idx_ai_call_model ON ai_call_log (model, created_at DESC);
