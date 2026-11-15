-- AI 工具能力的平台策略上限；与既有每日面试/解析上限同源，管理端可在线调整。
-- 0 表示不限（与既有字段语义一致）。
ALTER TABLE platform_settings
    ADD COLUMN tts_per_question_limit INT NOT NULL DEFAULT 3
        CHECK (tts_per_question_limit >= 0),
    ADD COLUMN ocr_daily_limit INT NOT NULL DEFAULT 5
        CHECK (ocr_daily_limit >= 0),
    ADD COLUMN search_daily_limit INT NOT NULL DEFAULT 20
        CHECK (search_daily_limit >= 0),
    ADD COLUMN fetch_daily_limit INT NOT NULL DEFAULT 20
        CHECK (fetch_daily_limit >= 0);

COMMENT ON COLUMN platform_settings.tts_per_question_limit IS '单题朗读次数上限（0=不限）';
COMMENT ON COLUMN platform_settings.ocr_daily_limit IS '每日简历 OCR 次数上限（0=不限）';
COMMENT ON COLUMN platform_settings.search_daily_limit IS '每日联网搜索次数上限（0=不限）';
COMMENT ON COLUMN platform_settings.fetch_daily_limit IS '每日网页抓取次数上限（0=不限）';