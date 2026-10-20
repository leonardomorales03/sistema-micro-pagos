-- =============================================================================
--  V11 — Reports, Exports CSV asíncronos + Reconciliación (T25)
--  Dueña exclusiva de V11. V1..V10 son inmutables.
--  Contenido:
--    1. `report_exports`             — cola de exportaciones CSV asíncronas.
--    2. `reconciliation_runs`        — ejecuciones del job de reconciliación.
--    3. `reconciliation_mismatches`  — discrepancias encontradas por run.
--  Requisitos: 28.1, 28.3, 28.4, 28.5.
-- =============================================================================

SET search_path TO public;

-- ─────────────────────────────────────────────────────────────────────────────
--  1. report_exports — cola de exportaciones CSV asíncronas
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE report_exports (
    id              UUID          PRIMARY KEY,
    requested_by    UUID          NOT NULL REFERENCES users(id),
    type            VARCHAR(40)   NOT NULL,
    filters         JSONB         NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'QUEUED',
    file_path       VARCHAR(1024) NULL,
    download_token  VARCHAR(128)  NULL UNIQUE,
    row_count       BIGINT        NULL,
    error_message   VARCHAR(512)  NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMPTZ   NULL,
    completed_at    TIMESTAMPTZ   NULL,
    expires_at      TIMESTAMPTZ   NULL
);

ALTER TABLE report_exports ADD CONSTRAINT ck_report_exports_status
    CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED'));

CREATE INDEX ix_report_exports_requested_by ON report_exports(requested_by);
CREATE INDEX ix_report_exports_status       ON report_exports(status);
CREATE INDEX ix_report_exports_created_at   ON report_exports(created_at DESC);

COMMENT ON TABLE  report_exports IS 'Cola de exportaciones CSV asíncronas (Requisito 28.1)';
COMMENT ON COLUMN report_exports.type IS 'TX_HISTORY | TOPUPS | WITHDRAWALS | ANNUAL_TAX_SUMMARY';
COMMENT ON COLUMN report_exports.filters IS 'Filtros aplicados (JSONB): from/to/status/tipo/moneda/year';
COMMENT ON COLUMN report_exports.status IS 'QUEUED (encolado) | RUNNING | COMPLETED | FAILED';
COMMENT ON COLUMN report_exports.file_path IS 'Ruta del CSV en el storage (local/S3-compat)';
COMMENT ON COLUMN report_exports.download_token IS 'Token opaco de descarga temporal (único)';
COMMENT ON COLUMN report_exports.expires_at IS 'Caducidad del enlace de descarga';

-- ─────────────────────────────────────────────────────────────────────────────
--  2. reconciliation_runs — ejecuciones del job de reconciliación
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE reconciliation_runs (
    id              UUID        PRIMARY KEY,
    triggered_by    UUID        NULL REFERENCES users(id),
    provider        VARCHAR(40) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    period_start    TIMESTAMPTZ NULL,
    period_end      TIMESTAMPTZ NULL,
    internal_count  INT         NOT NULL DEFAULT 0,
    provider_count  INT         NOT NULL DEFAULT 0,
    mismatch_count  INT         NOT NULL DEFAULT 0,
    notes           VARCHAR(512) NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ NULL
);

ALTER TABLE reconciliation_runs ADD CONSTRAINT ck_reconciliation_runs_status
    CHECK (status IN ('RUNNING','COMPLETED','FAILED'));

CREATE INDEX ix_reconciliation_runs_created_at ON reconciliation_runs(created_at DESC);

COMMENT ON TABLE  reconciliation_runs IS 'Ejecuciones del job de reconciliación interno vs proveedor (Requisito 28.3)';
COMMENT ON COLUMN reconciliation_runs.triggered_by IS 'Usuario que disparó el run (NULL = job programado)';
COMMENT ON COLUMN reconciliation_runs.status IS 'RUNNING | COMPLETED | FAILED';

-- ─────────────────────────────────────────────────────────────────────────────
--  3. reconciliation_mismatches — discrepancias por run
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE reconciliation_mismatches (
    id              UUID          PRIMARY KEY,
    run_id          UUID          NOT NULL REFERENCES reconciliation_runs(id) ON DELETE CASCADE,
    kind            VARCHAR(40)   NOT NULL,
    internal_tx_id  UUID          NULL,
    provider_ref    VARCHAR(64)   NULL,
    internal_amount NUMERIC(24,8) NULL,
    provider_amount NUMERIC(24,8) NULL,
    currency        VARCHAR(3)    NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    resolution_note VARCHAR(512)  NULL,
    resolved_by     UUID          NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

ALTER TABLE reconciliation_mismatches ADD CONSTRAINT ck_reconciliation_mismatches_kind
    CHECK (kind IN ('MISSING_AT_PROVIDER','AMOUNT_MISMATCH','UNEXPECTED_PROVIDER_ENTRY'));
ALTER TABLE reconciliation_mismatches ADD CONSTRAINT ck_reconciliation_mismatches_status
    CHECK (status IN ('NEW','INVESTIGATING','RESOLVED'));

CREATE INDEX ix_reconciliation_mismatches_run_id ON reconciliation_mismatches(run_id);
CREATE INDEX ix_reconciliation_mismatches_status ON reconciliation_mismatches(status);

COMMENT ON TABLE  reconciliation_mismatches IS 'Discrepancias detectadas por un reconciliation_run';
COMMENT ON COLUMN reconciliation_mismatches.kind IS 'MISSING_AT_PROVIDER | AMOUNT_MISMATCH | UNEXPECTED_PROVIDER_ENTRY';
COMMENT ON COLUMN reconciliation_mismatches.status IS 'NEW | INVESTIGATING | RESOLVED';

-- =============================================================================
--  FIN V11
-- =============================================================================
