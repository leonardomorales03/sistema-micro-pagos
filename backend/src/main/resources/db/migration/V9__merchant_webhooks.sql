-- =============================================================
--  V9__merchant_webhooks.sql — Webhooks a merchants (T22 / Requisito 25.4, 21.6)
--
--  · merchant_webhook_configs: 1 configuración por comercio (endpoint, secreto
--    de firma HMAC-SHA256 y máscara de eventos suscritos).
--  · webhook_deliveries: log append-only de intentos de entrega con backoff
--    (1m / 5m / 15m / 1h / 6h / 24h) e idempotencia por event_id.
-- =============================================================

SET search_path TO public;

CREATE TABLE merchant_webhook_configs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    endpoint_url     VARCHAR(1024) NOT NULL,
    signing_secret   VARCHAR(255) NOT NULL,
    subscribed_events TEXT[] NOT NULL DEFAULT ARRAY['*']::TEXT[],
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE webhook_deliveries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    merchant_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    attempt_number  INT NOT NULL DEFAULT 1 CHECK (attempt_number >= 1),
    status          VARCHAR(20) NOT NULL
                        CHECK (status IN ('QUEUED','SUCCEEDED','FAILED','RETRYABLE_FAIL')),
    http_status     INT,
    request_body    JSONB,
    response_body   TEXT,
    next_retry_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at    TIMESTAMPTZ
);

-- Cola de reintentos del scheduler + historial por comercio
CREATE INDEX idx_webhook_next_retry ON webhook_deliveries(next_retry_at, status);
CREATE INDEX idx_webhook_deliveries_merchant ON webhook_deliveries(merchant_user_id, created_at DESC);
CREATE INDEX idx_webhook_deliveries_event ON webhook_deliveries(event_id);

COMMENT ON TABLE  merchant_webhook_configs IS 'Configuración de webhook saliente por comercio (única por usuario)';
COMMENT ON COLUMN merchant_webhook_configs.signing_secret IS 'Secreto HMAC-SHA256 usado en X-Signature (t=<ts>,v1=<hmac>)';
COMMENT ON COLUMN merchant_webhook_configs.subscribed_events IS 'Máscara de eventos; ["*"] = todos';
COMMENT ON TABLE  webhook_deliveries IS 'Log append-only de intentos de entrega (Requisito 25.4)';
COMMENT ON COLUMN webhook_deliveries.event_id IS 'Idempotency key del evento (mismo id en todos los reintentos)';
COMMENT ON COLUMN webhook_deliveries.next_retry_at IS 'Próximo reintento programado (NULL = no reintentable)';
