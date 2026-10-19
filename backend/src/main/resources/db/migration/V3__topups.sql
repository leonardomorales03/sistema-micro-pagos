-- =============================================================
--  V3__topups.sql
--  NUEVA TABLA topups (recargas / payment intents proveedor externo)
--  Regla: NO modificar V1 ni V2. Incremental.
-- =============================================================

SET search_path TO public;

-- ─────────────────────────────────────────────────────────────
--  topups: registra intentos de recarga desde proveedor
--  externo (hoy Sandbox Stripe, mañana Stripe Real).
--  UNIQUE(provider_ref) → idempotencia webhook.
--  FK user_id + wallet_id existentes.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE topups (
    id                      UUID PRIMARY KEY,
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wallet_id               UUID NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,

    gross_amount            NUMERIC(24,8) NOT NULL CHECK (gross_amount > 0),
    net_amount              NUMERIC(24,8) NOT NULL CHECK (net_amount > 0),
    fee_amount              NUMERIC(24,8) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    currency                VARCHAR(3) NOT NULL,

    provider                VARCHAR(32) NOT NULL DEFAULT 'STRIPE_MOCK',
    provider_ref            VARCHAR(128) NOT NULL,
    checkout_url            VARCHAR(1024),

    status                  VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT'
                            CHECK (status IN ('PENDING_PAYMENT','PAID','FAILED','EXPIRED','CANCELLED','REFUNDED')),

    failure_reason          VARCHAR(512),
    paid_at                 TIMESTAMPTZ,
    transaction_ref         UUID,  -- nullable: apunta a transactions.id una vez generado el DEPOSIT_TOPUP ledger
    success_url             VARCHAR(1024),
    cancel_url              VARCHAR(1024),
    idempotency_key         VARCHAR(128),
    metadata_json           TEXT,

    created_by              UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT topups_provider_ref_unq UNIQUE (provider_ref)
);

CREATE INDEX topups_user_id_idx   ON topups(user_id);
CREATE INDEX topups_wallet_id_idx ON topups(wallet_id);
CREATE INDEX topups_status_idx    ON topups(status);
CREATE INDEX topups_created_at_idx ON topups(created_at DESC);

-- Trigger auto updated_at
CREATE OR REPLACE FUNCTION trg_topups_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_updated_at_topups
BEFORE UPDATE ON topups
FOR EACH ROW EXECUTE FUNCTION trg_topups_set_updated_at();
