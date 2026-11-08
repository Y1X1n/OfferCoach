-- 管理端可在线切换 LLM 配置；API Key 只存 AES-GCM 密文，不落明文。
CREATE TABLE ai_runtime_config (
    id             SMALLINT PRIMARY KEY CHECK (id = 1),
    enabled        BOOLEAN,
    base_url       VARCHAR(512),
    api_key_cipher TEXT,
    model          VARCHAR(128),
    small_model    VARCHAR(128),
    updated_by     BIGINT REFERENCES app_user (id),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE ai_runtime_config IS '单行运行时 AI 配置；空字段回退 oc.ai.* 环境默认值';
COMMENT ON COLUMN ai_runtime_config.api_key_cipher IS 'AES-GCM(v1:base64(iv||ciphertext))，密钥来自 OC_AI_SECRET';

INSERT INTO ai_runtime_config (id) VALUES (1) ON CONFLICT (id) DO NOTHING;