CREATE TABLE IF NOT EXISTS model_configs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    provider VARCHAR(32) NOT NULL DEFAULT 'openai',
    model_type VARCHAR(16) NOT NULL DEFAULT 'chat',
    base_url VARCHAR(512) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    api_token_ciphertext TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE model_configs ADD COLUMN IF NOT EXISTS model_type VARCHAR(16) NOT NULL DEFAULT 'chat';
UPDATE model_configs SET model_type = 'chat' WHERE model_type IS NULL;
COMMENT ON COLUMN model_configs.model_type IS '模型类型：chat 聊天模型，embedding 向量模型';
-- 每种模型类型各自只能有一个启用配置，聊天模型和向量模型互不影响。
DROP INDEX IF EXISTS uq_model_configs_active;
CREATE UNIQUE INDEX IF NOT EXISTS uq_model_configs_active_type ON model_configs (model_type) WHERE active = TRUE;
