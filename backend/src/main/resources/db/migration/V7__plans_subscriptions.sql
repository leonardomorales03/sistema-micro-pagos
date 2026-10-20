-- =============================================================
--  V7__plans_subscriptions.sql  (T20 — Plans & Subscriptions)
--  Regla: V1 es inmutable. Esta migración CREA las tablas nuevas
--  `plans` y `subscriptions` (Req. 22.1–22.6).
--  Jobs de facturación usan ShedLock (Redis), por lo que NO se
--  requiere tabla de locks en Postgres.
-- =============================================================

SET search_path TO public;

-- ─────────────────────────────────────────────────────────────
--  1. TABLA plans (plantilla de cobro recurrente del comercio)
-- ─────────────────────────────────────────────────────────────
--  NOTA: la columna de intervalo se nombra `billing_interval` (no
--  `interval`) para evitar ambigüedad con la palabra reservada SQL.
CREATE TABLE plans (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_user_id    UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name                VARCHAR(255) NOT NULL,
    amount              NUMERIC(24,8) NOT NULL CHECK (amount > 0),
    currency            VARCHAR(3) NOT NULL,
    billing_interval    VARCHAR(10) NOT NULL
                            CHECK (billing_interval IN ('DAY','WEEK','MONTH','YEAR')),
    interval_count      INT NOT NULL DEFAULT 1 CHECK (interval_count >= 1),
    trial_days          INT NULL CHECK (trial_days IS NULL OR trial_days >= 0),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_plans_merchant ON plans(merchant_user_id);
CREATE INDEX ix_plans_status   ON plans(status);

COMMENT ON TABLE plans IS 'Plan de suscripción ofrecido por un comercio (Requirement 22.1)';
COMMENT ON COLUMN plans.billing_interval IS 'DAY/WEEK/MONTH/YEAR (BillingInterval)';
COMMENT ON COLUMN plans.interval_count   IS 'Cada cuántos intervalos se cobra (>=1)';
COMMENT ON COLUMN plans.trial_days       IS 'Días de prueba (NULL = sin trial)';

-- ─────────────────────────────────────────────────────────────
--  2. TABLA subscriptions (mandato de cobro de un suscriptor)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE subscriptions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id                 UUID NOT NULL REFERENCES plans(id) ON DELETE RESTRICT,
    subscriber_id           UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    subscriber_wallet_id    UUID NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    status                  VARCHAR(20) NOT NULL
                                CHECK (status IN ('TRIALING','ACTIVE','PAST_DUE','CANCELED','PAUSED')),
    current_period_start    TIMESTAMPTZ NOT NULL,
    next_billing_date       TIMESTAMPTZ NOT NULL,
    canceled_at             TIMESTAMPTZ NULL,
    dunning_attempts        INT NOT NULL DEFAULT 0 CHECK (dunning_attempts >= 0),
    last_billing_result     TEXT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Índice clave del scheduler: buscar las suscripciones vencidas por cobrar.
CREATE INDEX idx_subs_next_billing ON subscriptions(next_billing_date, status);
CREATE INDEX ix_subs_plan          ON subscriptions(plan_id);
CREATE INDEX ix_subs_subscriber    ON subscriptions(subscriber_id);

COMMENT ON TABLE subscriptions IS 'Mandato de suscripción de un usuario a un Plan (Requirement 22.2)';
COMMENT ON COLUMN subscriptions.next_billing_date IS 'Fecha del próximo cobro (≤ now → elegible para el scheduler)';
COMMENT ON COLUMN subscriptions.dunning_attempts  IS 'Reintentos fallidos acumulados (política dunning)';
COMMENT ON COLUMN subscriptions.last_billing_result IS 'Resultado del último cobro: SUCCESS | motivo de fallo';
