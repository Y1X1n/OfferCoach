-- 索引命名与 dev/conventions.md §5（uq_<table>_<cols>）对齐；仅重命名，不改约束语义。
ALTER INDEX IF EXISTS uq_jd_user_content_hash RENAME TO uq_job_description_user_id_content_hash;

COMMENT ON INDEX uq_job_description_user_id_content_hash IS '同一用户相同内容 JD 只能有一条未删除记录';