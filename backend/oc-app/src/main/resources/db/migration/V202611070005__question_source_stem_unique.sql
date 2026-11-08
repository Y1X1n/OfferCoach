-- 同一来源文件可能包含多道题，按 (repo, path, stem hash) 去重而不是按文件去重。
ALTER TABLE question_bank DROP CONSTRAINT IF EXISTS question_bank_source_repo_source_path_key;
DROP INDEX IF EXISTS uq_question_source_repo_source_path;

ALTER TABLE question_bank ADD COLUMN IF NOT EXISTS stem_hash CHAR(32)
    GENERATED ALWAYS AS (md5(stem)) STORED;

CREATE UNIQUE INDEX IF NOT EXISTS uq_question_source_stem
    ON question_bank (source_repo, source_path, stem_hash);