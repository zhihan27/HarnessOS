CREATE TABLE IF NOT EXISTS model_configs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    provider VARCHAR(32) NOT NULL DEFAULT 'openai',
    base_url VARCHAR(512) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    api_token_ciphertext TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_model_configs_active ON model_configs (active) WHERE active = TRUE;
