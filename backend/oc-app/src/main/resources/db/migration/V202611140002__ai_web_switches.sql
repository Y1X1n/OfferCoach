-- 联网检索 / 抓取能力开关；与 ai_runtime_config 同源，供 oc-ai-search / oc-ai-fetch 只读。
ALTER TABLE ai_runtime_config
    ADD COLUMN search_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN fetch_enabled  BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN ai_runtime_config.search_enabled IS '联网检索（目标岗位搜索）总开关';
COMMENT ON COLUMN ai_runtime_config.fetch_enabled IS '网页抓取（岗位链接抓取）总开关';

-- JD 来源标注：手工粘贴 / 搜索兜底 / 链接抓取。
ALTER TABLE job_description
    ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_url  VARCHAR(1024);

ALTER TABLE job_description
    ADD CONSTRAINT ck_jd_source_type CHECK (source_type IN ('MANUAL', 'SEARCH', 'URL'));

COMMENT ON COLUMN job_description.source_type IS 'JD 来源：MANUAL 手工粘贴 / SEARCH 搜索兜底 / URL 链接抓取';
COMMENT ON COLUMN job_description.source_url IS '来源链接（source_type 为 SEARCH/URL 时记录）';
