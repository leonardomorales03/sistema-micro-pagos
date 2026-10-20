-- =============================================================
--  V8__notifications.sql — Notificaciones a usuarios (T22 / Requisito 25)
--
--  Log append-only por usuario de todas las notificaciones emitidas
--  (canal IN_APP persistido; EMAIL/otros canales se registran igual
--  para trazabilidad).  Índice de no leídas para el badge/contador.
-- =============================================================

SET search_path TO public;

CREATE TABLE user_notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    channel         VARCHAR(20) NOT NULL
                        CHECK (channel IN ('IN_APP','EMAIL','SMS','PUSH')),
    status          VARCHAR(20) NOT NULL
                        CHECK (status IN ('QUEUED','SENT','FAILED','READ')),
    correlation_id  UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at         TIMESTAMPTZ
);

-- Listado por usuario (más recientes primero) y contador de no leídas
CREATE INDEX idx_notifs_user_time ON user_notifications(user_id, created_at DESC);
CREATE INDEX idx_notifs_user_unread ON user_notifications(user_id) WHERE read_at IS NULL;

COMMENT ON TABLE  user_notifications IS 'Log append-only de notificaciones por usuario (Requisito 25.3)';
COMMENT ON COLUMN user_notifications.event_type IS 'Tipo/template del evento (SUBSCRIPTION_CHARGED, TX_RECEIVED, TOPUP_SUCCEEDED, ...)';
COMMENT ON COLUMN user_notifications.payload    IS 'Datos del evento serializados (JSONB)';
COMMENT ON COLUMN user_notifications.channel    IS 'Canal de entrega solicitado (IN_APP persistido en este log)';
COMMENT ON COLUMN user_notifications.status     IS 'QUEUED/SENT/FAILED y READ cuando el usuario lo marca leído';
COMMENT ON COLUMN user_notifications.read_at    IS 'Instante de lectura (NULL = no leída)';
