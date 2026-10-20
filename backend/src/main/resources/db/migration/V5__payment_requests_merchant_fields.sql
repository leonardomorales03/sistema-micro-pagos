-- =============================================================
--  V5__payment_requests_merchant_fields.sql
--  La tabla `payment_requests` YA EXISTE (creada en V1).
--  Regla: NO modificar V1. Esta migración sólo AGREGA lo que falta
--  para el flujo de comercio (T19) y amplía el CHECK de status.
-- =============================================================

SET search_path TO public;

-- ─────────────────────────────────────────────────────────────
--  1. Columnas nuevas del flujo merchant (nullable para no romper
--     filas existentes creadas por el esquema base V1).
-- ─────────────────────────────────────────────────────────────
ALTER TABLE payment_requests
    ADD COLUMN IF NOT EXISTS order_ref   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS webhook_url VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS paid_at     TIMESTAMPTZ;

COMMENT ON COLUMN payment_requests.order_ref   IS 'Referencia de orden del comercio (idempotencia externa de su lado)';
COMMENT ON COLUMN payment_requests.webhook_url IS 'URL del backend del comercio a notificar (CREATED/PAID/EXPIRED/REFUNDED); NULL = sin webhook';
COMMENT ON COLUMN payment_requests.paid_at     IS 'Instante en que el request se liquidó (PAID)';

-- ─────────────────────────────────────────────────────────────
--  2. Ampliar CHECK de status: V1 permite PENDING/PAID/CANCELLED/EXPIRED;
--     T19 añade REFUNDED. El constraint inline de V1 se auto-nombró
--     `payment_requests_status_check`.
-- ─────────────────────────────────────────────────────────────
ALTER TABLE payment_requests DROP CONSTRAINT IF EXISTS payment_requests_status_check;
ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_status_check
    CHECK (status IN ('PENDING','PAID','CANCELLED','EXPIRED','REFUNDED'));

-- ─────────────────────────────────────────────────────────────
--  3. Índices de apoyo para el listado por comercio (created_by)
--     y la búsqueda por referencia de orden.
-- ─────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS ix_payreq_created_by ON payment_requests(created_by);
CREATE INDEX IF NOT EXISTS ix_payreq_order_ref  ON payment_requests(order_ref) WHERE order_ref IS NOT NULL;
