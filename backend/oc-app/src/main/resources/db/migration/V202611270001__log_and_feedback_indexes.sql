-- 补两个缺失的索引：
-- 1) ai_call_log.user_id：管理端 AI 调用检索按 user_id 过滤，此前只有 created_at/scene/session_id，
--    该查询退化为全表扫描。
-- 2) answer_feedback.user_id：注销清理按 user_id 删除评分记录，同样退化为全表扫描。
-- 用普通 CREATE INDEX（非 CONCURRENTLY）：Flyway 在单事务内执行，CONCURRENTLY 不允许在事务块中运行。
CREATE INDEX IF NOT EXISTS idx_ai_call_user_time ON ai_call_log (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_answer_feedback_user ON answer_feedback (user_id);
