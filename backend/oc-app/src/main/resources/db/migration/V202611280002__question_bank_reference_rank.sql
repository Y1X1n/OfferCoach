-- 出题检索的排序：优先带参考答案的题。
--
-- 两个检索查询此前都是
--   ORDER BY (CASE WHEN reference_answer IS NOT NULL AND btrim(reference_answer) <> ''
--                  THEN 0 ELSE 1 END), id
-- 表达式排序无法走索引：22,409 行的题库里 AI-AGENT 一个岗位就有 12,557 条待排序的行，
-- 每次出题都要把它们全读出来再 top-N 排序（实测 58ms / 9,694 buffers）。
--
-- 改成 STORED 生成列 + 部分索引后，同一个查询 0.09ms / 29 buffers（family 查询），
-- 无 family 的全局检索 0.35ms / 16 buffers。生成列随 reference_answer 自动维护，
-- 导入与后台编辑都不需要改写入语句。
ALTER TABLE question_bank
    ADD COLUMN IF NOT EXISTS has_reference_answer boolean
    GENERATED ALWAYS AS (reference_answer IS NOT NULL AND btrim(reference_answer) <> '') STORED;

COMMENT ON COLUMN question_bank.has_reference_answer IS '是否有非空参考答案；出题检索的排序键（生成列，不可手动写入）';

-- 按岗位检索：filter 与排序键同在一个索引里。
CREATE INDEX IF NOT EXISTS idx_question_family_rank
    ON question_bank (lower(job_family), has_reference_answer DESC, id)
    WHERE status = 'PUBLISHED';

-- 不限定岗位的全局检索：只需要排序键，LIMIT 让扫描提前停止。
CREATE INDEX IF NOT EXISTS idx_question_published_rank
    ON question_bank (has_reference_answer DESC, id)
    WHERE status = 'PUBLISHED';

COMMENT ON INDEX idx_question_family_rank IS '出题检索（限定岗位）：按岗位过滤 + 优先带参考答案';
COMMENT ON INDEX idx_question_published_rank IS '出题检索（不限岗位）：优先带参考答案';
