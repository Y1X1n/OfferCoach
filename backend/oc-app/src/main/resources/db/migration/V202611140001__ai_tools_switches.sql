-- AI 工具能力开关与朗读默认音色；与 ai_runtime_config 同源，供三个工具服务只读。
ALTER TABLE ai_runtime_config
    ADD COLUMN asr_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN tts_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN ocr_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN tts_default_voice VARCHAR(16) NOT NULL DEFAULT 'stella';

ALTER TABLE ai_runtime_config
    ADD CONSTRAINT ck_ai_runtime_tts_voice CHECK (tts_default_voice IN ('stella', 'tide'));

COMMENT ON COLUMN ai_runtime_config.asr_enabled IS '语音识别（语音作答）总开关';
COMMENT ON COLUMN ai_runtime_config.tts_enabled IS '语音合成（题目朗读）总开关';
COMMENT ON COLUMN ai_runtime_config.ocr_enabled IS '文字识别（扫描件简历）总开关';
COMMENT ON COLUMN ai_runtime_config.tts_default_voice IS '朗读默认音色：stella 女声 / tide 男声';
