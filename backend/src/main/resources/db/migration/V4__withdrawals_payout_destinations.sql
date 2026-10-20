-- =============================================================
--  V4__withdrawals_payout_destinations.sql
--  NUEVAS TABLAS: payout_destinations + withdrawals
--  Regla: NO modificar V1/V2/V3. Incremental.
-- =============================================================

SET search_path TO public;

-- ─────────────────────────────────────────────────────────────
--  1. payout_destinations — cuentas externas destino de retiros.
--     SEGURIDAD: sólo se guarda el número ENMASCARADO + last4.
--     UNIQUE (user_id, account_number_masked) evita duplicados.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE payout_destinations (
    id                      UUID PRIMARY KEY,
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type                    VARCHAR(24) NOT NULL DEFAULT 'BANK_ACCOUNT'
                            CHECK (type IN ('BANK_ACCOUNT')),
    masked_label            VARCHAR(120) NOT NULL,
    bank_name               VARCHAR(120),
    account_type            VARCHAR(32),
    account_number_masked   VARCHAR(64) NOT NULL,
    last4                   VARCHAR(4),
    holder_name             VARCHAR(160),
    country                 VARCHAR(2),
    currency                VARCHAR(3),
    is_default              BOOLEAN NOT NULL DEFAULT FALSE,
    verified                BOOLEAN NOT NULL DEFAULT FALSE,
    provider_token_ref      VARCHAR(128),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT payout_destinations_user_masked_unq UNIQUE (user_id, account_number_masked)
);

CREATE INDEX payout_destinations_user_id_idx ON payout_destinations(user_id);
CREATE INDEX payout_destinations_default_idx ON payout_destinations(user_id) WHERE is_default = TRUE;
CREATE UNIQUE INDEX payout_destinations_provider_token_unq
    ON payout_destinations(provider_token_ref) WHERE provider_token_ref IS NOT NULL;

-- ─────────────────────────────────────────────────────────────
--  2. withdrawals — solicitudes de retiro + reserva de fondos.
--     provider_ref UNIQUE → idempotencia webhook payout.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE withdrawals (
    id                      UUID PRIMARY KEY,
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wallet_id               UUID NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    payout_destination_id   UUID NOT NULL REFERENCES payout_destinations(id) ON DELETE RESTRICT,

    gross_amount            NUMERIC(24,8) NOT NULL CHECK (gross_amount > 0),
    net_amount              NUMERIC(24,8) NOT NULL CHECK (net_amount > 0),
    fee_amount              NUMERIC(24,8) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    currency                VARCHAR(3) NOT NULL,

    provider                VARCHAR(32) NOT NULL DEFAULT 'STRIPE_MOCK',
    provider_ref            VARCHAR(128),
    status                  VARCHAR(32) NOT NULL DEFAULT 'REQUESTED'
                            CHECK (status IN ('REQUESTED','PROCESSING','COMPLETED','FAILED','CANCELLED')),

    failure_reason          VARCHAR(512),
    note                    VARCHAR(255),
    metadata_json           TEXT,
    created_by              UUID REFERENCES users(id) ON DELETE SET NULL,
    requested_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at            TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT withdrawals_provider_ref_unq UNIQUE (provider_ref)
);

CREATE INDEX withdrawals_user_id_idx   ON withdrawals(user_id);
CREATE INDEX withdrawals_wallet_id_idx ON withdrawals(wallet_id);
CREATE INDEX withdrawals_status_idx    ON withdrawals(status);
CREATE INDEX withdrawals_requested_at_idx ON withdrawals(requested_at DESC);

-- ─────────────────────────────────────────────────────────────
--  3. Triggers updated_at
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION trg_payout_destinations_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_updated_at_payout_destinations
BEFORE UPDATE ON payout_destinations
FOR EACH ROW EXECUTE FUNCTION trg_payout_destinations_set_updated_at();

CREATE OR REPLACE FUNCTION trg_withdrawals_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_updated_at_withdrawals
BEFORE UPDATE ON withdrawals
FOR EACH ROW EXECUTE FUNCTION trg_withdrawals_set_updated_at();
