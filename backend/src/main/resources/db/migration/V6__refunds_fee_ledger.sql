-- =============================================================
--  V6__refunds_fee_ledger.sql
--  NUEVAS TABLAS: refunds + fee_ledger (T21 — Refunds + FeeEngine)
--  Regla: NO modificar V1..V5. Incremental.
--  Nota: transactions.fee_amount y los enums REFUND/REFUNDED ya
--  existen en V1; aquí sólo se crean las tablas de comisiones y
--  reembolsos que faltaban.
-- =============================================================

SET search_path TO public;

-- ─────────────────────────────────────────────────────────────
--  1. refunds — reembolsos totales/parciales de una transacción
--     origen completada. refund_tx_id UNIQUE apunta a la
--     transacción de reverse transfer (vínculo bidireccional).
-- ─────────────────────────────────────────────────────────────
CREATE TABLE refunds (
    id                      UUID PRIMARY KEY,
    source_tx_id            UUID NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT,
    refunding_user_id       UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    refunded_amount         NUMERIC(24,8) NOT NULL CHECK (refunded_amount > 0),
    currency                VARCHAR(3) NOT NULL,
    type                    VARCHAR(16) NOT NULL CHECK (type IN ('FULL','PARTIAL')),
    reason                  TEXT,
    refund_tx_id            UUID NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT refunds_refund_tx_unq UNIQUE (refund_tx_id)
);

CREATE INDEX idx_refunds_source ON refunds(source_tx_id);
CREATE INDEX idx_refunds_refunding_user ON refunds(refunding_user_id);

COMMENT ON TABLE refunds IS 'Reembolsos (FULL/PARTIAL) de una transacción origen; vínculo bidireccional vía refund_tx_id';
COMMENT ON COLUMN refunds.refund_tx_id IS 'Transaction REFUND (reverse transfer) que movió los fondos de vuelta';

-- ─────────────────────────────────────────────────────────────
--  2. fee_ledger — libro de comisiones por transacción
--     (conciliación financiera + dashboard segmentado por tipo).
-- ─────────────────────────────────────────────────────────────
CREATE TABLE fee_ledger (
    id                      UUID PRIMARY KEY,
    transaction_id          UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    amount                  NUMERIC(24,8) NOT NULL CHECK (amount >= 0),
    currency                VARCHAR(3) NOT NULL,
    fee_type                VARCHAR(20) NOT NULL CHECK (fee_type IN ('FLAT','PERCENTAGE','FX_SPREAD')),
    rule_version            VARCHAR(50) NOT NULL,
    description             TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fees_tx ON fee_ledger(transaction_id);
CREATE INDEX idx_fees_type_created ON fee_ledger(fee_type, created_at DESC);

COMMENT ON TABLE fee_ledger IS 'Asiento de comisión calculada por el FeeEngine; ligado a la transacción que la originó';
COMMENT ON COLUMN fee_ledger.rule_version IS 'Versión de la regla de pricing aplicada (trazabilidad histórica)';
