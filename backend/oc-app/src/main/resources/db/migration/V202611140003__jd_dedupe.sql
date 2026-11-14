-- JD 去重：同一用户、相同内容（content_hash）只保留一条未删除记录。
-- 历史重复行按"最早一条"归并：先把会话引用改指保留行，再软删除重复行（保留数据可回溯）。

WITH duplicates AS (
    SELECT id, keeper_id
    FROM (
        SELECT id,
               MIN(id) OVER (PARTITION BY user_id, content_hash) AS keeper_id
        FROM job_description
        WHERE deleted_at IS NULL
    ) ranked
    WHERE id <> keeper_id
)
UPDATE interview_session s
SET jd_id = d.keeper_id
FROM duplicates d
WHERE s.jd_id = d.id;

WITH duplicates AS (
    SELECT id
    FROM (
        SELECT id,
               MIN(id) OVER (PARTITION BY user_id, content_hash) AS keeper_id
        FROM job_description
        WHERE deleted_at IS NULL
    ) ranked
    WHERE id <> keeper_id
)
UPDATE job_description j
SET deleted_at = now(), updated_at = now()
FROM duplicates d
WHERE j.id = d.id;

-- 用唯一索引替换原普通索引：并发插入由数据库兜底，应用层冲突转为"复用/提示已存在"。
DROP INDEX IF EXISTS idx_jd_content_hash;

CREATE UNIQUE INDEX uq_jd_user_content_hash
    ON job_description (user_id, content_hash)
    WHERE deleted_at IS NULL;

COMMENT ON INDEX uq_jd_user_content_hash IS '同一用户相同内容 JD 只能有一条未删除记录';
