-- =============================================================================
--  V10 — Admin Backend API + RBAC + Adjustments + Freeze wallet + KYC queue (T24)
--  Dueña exclusiva de V10. V1..V9 son inmutables.
--  Contenido:
--    1. Tabla `audit_events` (APPEND-ONLY) — Property 31: toda mutación crítica
--       deja traza. Sólo INSERT; sin UPDATE/DELETE desde la aplicación.
--    2. Nuevo estado `UNDER_REVIEW` para `wallets.status` (freeze de compliance):
--       bloquea débitos, pero permite créditos entrantes.
--  NOTA: `transactions.type` NO tiene CHECK → no se toca (el nuevo valor de app
--        ADJUSTMENT se persiste sin cambios de esquema).
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
--  1. audit_events (append-only)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE audit_events (
    id              UUID              PRIMARY KEY,
    actor_user_id   UUID              NULL REFERENCES users(id),
    action          VARCHAR(100)      NOT NULL,
    entity_type     VARCHAR(50)       NOT NULL,
    entity_id       UUID              NULL,
    correlation_id  UUID              NULL,
    metadata        JSONB             NULL,
    ip_address      INET              NULL,
    user_agent      TEXT              NULL,
    created_at      TIMESTAMPTZ       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE audit_events IS
    'APPEND-ONLY audit trail (Property 31). Nunca UPDATE/DELETE; sólo INSERT desde la app.';
COMMENT ON COLUMN audit_events.actor_user_id IS 'Usuario que realizó la acción (NULL = sistema)';
COMMENT ON COLUMN audit_events.action IS 'Acción de negocio (WALLET_ADJUSTMENT, WALLET_FREEZE, KYC_APPROVE, ROLE_GRANT, ...)';
COMMENT ON COLUMN audit_events.entity_type IS 'Tipo de entidad afectada (WALLET, USER, KYC_DOCUMENT, ...)';
COMMENT ON COLUMN audit_events.entity_id IS 'ID de la entidad afectada (sin FK: append-only tolera borrados)';
COMMENT ON COLUMN audit_events.metadata IS 'Contexto JSON libre de la acción (amount, currency, direction, reason, ...)';

CREATE INDEX ix_audit_entity   ON audit_events(entity_type, entity_id);
CREATE INDEX ix_audit_actor    ON audit_events(actor_user_id);
CREATE INDEX ix_audit_created  ON audit_events(created_at DESC);

-- ─────────────────────────────────────────────────────────────────────────────
--  2. wallets.status → permitir UNDER_REVIEW
--  V1 declaró el CHECK inline (nombre autogenerado `wallets_status_check`).
--  Se dropea de forma defensiva y se crea constraint nombrado `ck_wallets_status`.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS wallets_status_check;
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS ck_wallets_status;

ALTER TABLE wallets ADD CONSTRAINT ck_wallets_status
    CHECK (status IN ('ACTIVE','FROZEN','CLOSED','UNDER_REVIEW'));

COMMENT ON COLUMN wallets.status IS
    'ACTIVE (opera full) | FROZEN | UNDER_REVIEW (bloquea débitos, permite créditos) | CLOSED';
