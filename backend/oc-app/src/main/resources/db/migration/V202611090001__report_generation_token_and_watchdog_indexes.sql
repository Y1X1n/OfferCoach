ALTER TABLE interview_report ADD COLUMN IF NOT EXISTS generation_token VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_answer_scoring ON interview_answer (submitted_at) WHERE status = 'SCORING';
CREATE INDEX IF NOT EXISTS idx_session_preparing ON interview_session (updated_at) WHERE status = 'PREPARING';
CREATE INDEX IF NOT EXISTS idx_session_idle ON interview_session (status, last_active_at)
    WHERE status IN ('IN_PROGRESS', 'PAUSED');
CREATE INDEX IF NOT EXISTS idx_resume_parsing ON resume (updated_at)
    WHERE parse_status IN ('PENDING', 'PARSING') AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_question_family_lower ON question_bank (status, lower(job_family), dimension, difficulty);