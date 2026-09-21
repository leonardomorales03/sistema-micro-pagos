-- =============================================================
--  V1__base_schema.sql — FLYWAY MIGRACIÓN INICIAL
--  Base de datos OLTP principal del sistema de micro-pagos
--  NOTAS:
--    · IDs: UUIDv4 (gen_random_uuid())
--    · CHECK constraints a nivel BD (doble aseguramiento reglas negocio)
--    · Índices en columnas de búsqueda frecuente y FK
--    · TZ: America/Bogota (seteada en postgresql.conf/initdb)
--    · Comentarios en cada tabla/columna para documentación en psql /\d
-- =============================================================

SET search_path TO public;

-- ─────────────────────────────────────────────────────────────
--  1. TABLA users (Aggregate Root: User)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE users (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                   CITEXT NOT NULL,
    full_name               VARCHAR(160) NOT NULL,
    phone_number            VARCHAR(32),
    password_hash           VARCHAR(255) NOT NULL,
    kyc_level               VARCHAR(16) NOT NULL DEFAULT 'LEVEL_0'
                                CHECK (kyc_level IN ('LEVEL_0','LEVEL_1','LEVEL_2')),
    status                  VARCHAR(32) NOT NULL DEFAULT 'PENDING_EMAIL_VERIFICATION'
                                CHECK (status IN ('ACTIVE','BLOCKED','PENDING_EMAIL_VERIFICATION','DELETED')),
    email_verified          BOOLEAN NOT NULL DEFAULT FALSE,
    referral_code           VARCHAR(16) NOT NULL UNIQUE,
    referred_by             UUID REFERENCES users(id) ON DELETE SET NULL,
    roles                   TEXT[] NOT NULL DEFAULT ARRAY['ROLE_USER']::TEXT[],
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_users_email ON users(email);
CREATE INDEX ix_users_referred_by ON users(referred_by) WHERE referred_by IS NOT NULL;
CREATE INDEX ix_users_kyc_level ON users(kyc_level);
CREATE INDEX ix_users_status ON users(status);

COMMENT ON TABLE users IS 'Aggregate User: identidad, roles, nivel KYC, referral, estado';
COMMENT ON COLUMN users.email IS 'case-insensitive (CITEXT) — único; login';
COMMENT ON COLUMN users.password_hash IS 'Argon2/bcrypt hash; NUNCA texto plano';
COMMENT ON COLUMN users.referral_code IS '8 chars alfanuméricos; único por usuario';

-- ─────────────────────────────────────────────────────────────
--  2. TABLA kyc_documents (Documentos por usuario)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE kyc_documents (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type                    VARCHAR(16) NOT NULL,
    number                  VARCHAR(32) NOT NULL,
    country                 VARCHAR(2) NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED')),
    rejection_reason        VARCHAR(255),
    payload                 JSONB,
    submitted_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at             TIMESTAMPTZ,
    reviewed_by             UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_kyc_docs_type_num_country ON kyc_documents(type, number, country);
CREATE INDEX ix_kyc_docs_user_id ON kyc_documents(user_id);
CREATE INDEX ix_kyc_docs_status ON kyc_documents(status);

COMMENT ON TABLE kyc_documents IS 'Documentos de identidad KYC por usuario (CC/NIT/CE/Passport)';

-- ─────────────────────────────────────────────────────────────
--  3. TABLA wallets (Aggregate Root: Wallet — una por usuario y moneda)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE wallets (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    currency                VARCHAR(3) NOT NULL,
    balance                 NUMERIC(24,8) NOT NULL DEFAULT 0
                                CHECK (balance >= 0),
    reserved                NUMERIC(24,8) NOT NULL DEFAULT 0
                                CHECK (reserved >= 0 AND reserved <= balance),
    status                  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                                CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
    created_by              UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                 BIGINT NOT NULL DEFAULT 0 -- optimistic locking @Version
);

CREATE UNIQUE INDEX ux_wallets_user_currency ON wallets(user_id, currency);
CREATE INDEX ix_wallets_currency ON wallets(currency);
CREATE INDEX ix_wallets_status ON wallets(status);

COMMENT ON TABLE wallets IS 'Wallet cuenta individual; UNIQUE (user_id, currency)';
COMMENT ON COLUMN wallets.balance IS 'Saldo actual (fondos confirmados) — NUMERIC(24,8) CHECK >=0 (BR#01)';
COMMENT ON COLUMN wallets.reserved IS 'Fondos con hold (retiro 48h) — disponible = balance - reserved';
COMMENT ON COLUMN wallets.version IS 'JPA @Version para locking optimista — evita double spend';

-- ─────────────────────────────────────────────────────────────
--  4. TABLA transactions (Aggregate Root: Transaction)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE transactions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type                    VARCHAR(32) NOT NULL,
    short_code              VARCHAR(8) UNIQUE,
    wallet_from_id          UUID REFERENCES wallets(id) ON DELETE SET NULL,
    wallet_to_id            UUID REFERENCES wallets(id) ON DELETE SET NULL,
    gross_amount            NUMERIC(24,8) NOT NULL
                                CHECK (gross_amount > 0),
    net_amount              NUMERIC(24,8) NOT NULL,
    fee_amount              NUMERIC(24,8) NOT NULL DEFAULT 0
                                CHECK (fee_amount >= 0),
    currency                VARCHAR(3) NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED',
                                                   'CANCELLED','REFUNDED','EXPIRED','ON_HOLD')),
    external_ref            VARCHAR(64),
    note                    VARCHAR(255),
    failure_reason          VARCHAR(512),
    created_by              UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_tx_status           ON transactions(status);
CREATE INDEX ix_tx_type             ON transactions(type);
CREATE INDEX ix_tx_created_at       ON transactions(created_at DESC);
CREATE INDEX ix_tx_wallet_from       ON transactions(wallet_from_id) WHERE wallet_from_id IS NOT NULL;
CREATE INDEX ix_tx_wallet_to         ON transactions(wallet_to_id)   WHERE wallet_to_id IS NOT NULL;
CREATE INDEX ix_tx_currency          ON transactions(currency);
CREATE INDEX ix_tx_created_by        ON transactions(created_by)    WHERE created_by IS NOT NULL;
CREATE UNIQUE INDEX ux_tx_external_ref ON transactions(external_ref) WHERE external_ref IS NOT NULL;

COMMENT ON TABLE transactions IS 'Aggregate Transaction; cada fila = operación financiera + 2 filas en account_moves';
COMMENT ON COLUMN transactions.short_code IS 'Código 6 chars alfanuméricos (QR PaymentRequest)';
COMMENT ON COLUMN transactions.gross_amount IS 'Monto bruto (usuario cobra esto); SIEMPRE > 0';
COMMENT ON COLUMN transactions.fee_amount IS 'Fee del proveedor/plataforma; >= 0';

-- ─────────────────────────────────────────────────────────────
--  5. TABLA account_moves — LEDGER DOBLE ENTRADA (BR#05)
--     Cada Transaction -> 2 AccountMove rows (debito + credito)
--     Para DEPOSIT: solo +1 AccountMove (wallet_to_id a crédito, wallet_from NULL)
--     Para WITHDRAW: solo -1 AccountMove (wallet_from_id a débito, wallet_to NULL)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE account_moves (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id               UUID NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    transaction_id          UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    amount                  NUMERIC(24,8) NOT NULL,  -- + crédito | - débito
    balance_after           NUMERIC(24,8) NOT NULL,  -- snapshop wallet post-move (BR#06)
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_account_moves_wallet_created
    ON account_moves(wallet_id, created_at DESC);
CREATE INDEX ix_account_moves_transaction
    ON account_moves(transaction_id);

-- Regla doble entrada: P2P exactamente 2 moves por transacción
-- (Deposit/Withdraw/FeeAdjustment se manejan con 1 move; la validación
--  exacta la hace capa app con excepción BR#06 — check a nivel app).
COMMENT ON TABLE account_moves IS 'Libro mayor / ledger double entry; 2 filas por P2P Transaction (BR#06)';
COMMENT ON COLUMN account_moves.balance_after IS 'Snapshop del wallet.balance DESPUÉS de aplicar el move — auditoría';

-- ─────────────────────────────────────────────────────────────
--  6. TABLA sessions — Refresh tokens JWT HttpOnly Cookie
-- ─────────────────────────────────────────────────────────────
CREATE TABLE sessions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash      VARCHAR(96) NOT NULL UNIQUE, -- SHA256 hex
    device_fingerprint      VARCHAR(64),
    user_agent              TEXT,
    ip_address              INET,
    issued_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at              TIMESTAMPTZ NOT NULL,
    revoked_at              TIMESTAMPTZ,
    revoked_reason          VARCHAR(32),
    rotated                 BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_sessions_user_id ON sessions(user_id);
CREATE INDEX ix_sessions_expires ON sessions(expires_at) WHERE revoked_at IS NULL;

COMMENT ON TABLE sessions IS 'Sesiones refresh-token JWT; logout invalida por user_id o por token';
COMMENT ON COLUMN sessions.refresh_token_hash IS 'SHA256 del refresh raw — NUNCA almacenamos raw';
COMMENT ON COLUMN sessions.revoked_reason IS 'LOGOUT | PASSWORD_CHANGE | ADMIN_KICK | REFRESH_ROTATED';

-- ─────────────────────────────────────────────────────────────
--  7. TABLA payment_requests — QR Links comercio (PaymentRequest)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE payment_requests (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_to_id            UUID NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    short_code              VARCHAR(8) NOT NULL UNIQUE,
    amount                  NUMERIC(24,8) NOT NULL CHECK (amount > 0),
    currency                VARCHAR(3) NOT NULL,
    description             VARCHAR(255),
    status                  VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING','PAID','CANCELLED','EXPIRED')),
    paid_transaction_id     UUID REFERENCES transactions(id) ON DELETE SET NULL,
    created_by              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at              TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '24 hours',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    payload_metadata        JSONB
);

CREATE INDEX ix_payreq_status       ON payment_requests(status);
CREATE INDEX ix_payreq_wallet_to    ON payment_requests(wallet_to_id);
CREATE INDEX ix_payreq_short_code   ON payment_requests(short_code);
CREATE INDEX ix_payreq_expires      ON payment_requests(expires_at) WHERE status = 'PENDING';

COMMENT ON TABLE payment_requests IS 'PaymentRequest QR / Link pago: short_code 6 chars, expira en 24h';

-- ─────────────────────────────────────────────────────────────
--  8. TABLA notifications — EMAIL + PUSH por usuario plantillas
-- ─────────────────────────────────────────────────────────────
CREATE TABLE notifications (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel                 VARCHAR(8) NOT NULL CHECK (channel IN ('EMAIL','PUSH','ALL')),
    template                VARCHAR(64) NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING','SENT','FAILED','OPENED','CLICKED')),
    to_address              VARCHAR(255),
    subject                 VARCHAR(255),
    payload                 JSONB NOT NULL,
    retry_count             INTEGER NOT NULL DEFAULT 0,
    last_error              TEXT,
    sent_at                 TIMESTAMPTZ,
    opened_at               TIMESTAMPTZ,
    clicked_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_notif_user_created ON notifications(user_id, created_at DESC);
CREATE INDEX ix_notif_status       ON notifications(status) WHERE status IN ('PENDING','FAILED');

COMMENT ON TABLE notifications IS 'Cola notificaciones; worker async procesa PENDING';

-- ─────────────────────────────────────────────────────────────
--  9. TABLA risk_incidents — RiskEngine detecciones
-- ─────────────────────────────────────────────────────────────
CREATE TABLE risk_incidents (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule                    VARCHAR(64) NOT NULL,
    user_id                 UUID REFERENCES users(id) ON DELETE SET NULL,
    wallet_id               UUID REFERENCES wallets(id) ON DELETE SET NULL,
    severity                VARCHAR(8) NOT NULL
                                CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    status                  VARCHAR(16) NOT NULL DEFAULT 'OPEN'
                                CHECK (status IN ('OPEN','INVESTIGATING','RESOLVED','DISMISSED')),
    ip_address              INET,
    device_fingerprint      VARCHAR(64),
    payload                 JSONB,
    created_by_rule_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_by             UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at             TIMESTAMPTZ,
    resolution_note         VARCHAR(512),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_risk_user_rule       ON risk_incidents(user_id, rule);
CREATE INDEX ix_risk_status_severity ON risk_incidents(status, severity);

COMMENT ON TABLE risk_incidents IS 'Incidentes del RiskEngine velocity counters (Redis) + reglas ML';

-- ─────────────────────────────────────────────────────────────
--  TRIGGER: Validación consistencia monedas Transaction ↔ Wallets
--           (PostgreSQL NO permite subquery en CHECK constraint;
--            la alternativa estándar es un BEFORE trigger).
--  Regla:
--    • Si wallet_from_id  NO NULL → wallets.currency = transactions.currency
--    • Si wallet_to_id    NO NULL → wallets.currency = transactions.currency
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION fn_validate_tx_currencies_consistent()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    w_from_cur VARCHAR(3);
    w_to_cur   VARCHAR(3);
BEGIN
    IF NEW.wallet_from_id IS NOT NULL THEN
        SELECT currency INTO w_from_cur FROM wallets w WHERE w.id = NEW.wallet_from_id LIMIT 1;
        IF w_from_cur IS NULL THEN
            RAISE EXCEPTION '[chk_tx_currency] wallet_from_id % no existe', NEW.wallet_from_id;
        END IF;
        IF w_from_cur <> NEW.currency THEN
            RAISE EXCEPTION '[chk_tx_currency] wallet_from_id % moneda % <> tx.currency %',
                NEW.wallet_from_id, w_from_cur, NEW.currency;
        END IF;
    END IF;

    IF NEW.wallet_to_id IS NOT NULL THEN
        SELECT currency INTO w_to_cur FROM wallets w WHERE w.id = NEW.wallet_to_id LIMIT 1;
        IF w_to_cur IS NULL THEN
            RAISE EXCEPTION '[chk_tx_currency] wallet_to_id % no existe', NEW.wallet_to_id;
        END IF;
        IF w_to_cur <> NEW.currency THEN
            RAISE EXCEPTION '[chk_tx_currency] wallet_to_id % moneda % <> tx.currency %',
                NEW.wallet_to_id, w_to_cur, NEW.currency;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_tx_currency ON transactions;
CREATE TRIGGER trg_validate_tx_currency
    BEFORE INSERT OR UPDATE OF wallet_from_id, wallet_to_id, currency
    ON transactions
    FOR EACH ROW
    EXECUTE FUNCTION fn_validate_tx_currencies_consistent();

-- ─────────────────────────────────────────────────────────────
--  TRIGGERS — updated_at automáticos (no requerir desde JPA/app)
--  NOTA: la función trigger set_updated_at() es creada por initdb
--        en 00-init-user-db.sql; si no existe, crearla aquí también:
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_users_updated_at            ON users;
DROP TRIGGER IF EXISTS trg_wallets_updated_at          ON wallets;
DROP TRIGGER IF EXISTS trg_transactions_updated_at     ON transactions;
DROP TRIGGER IF EXISTS trg_payment_requests_updated_at ON payment_requests;
DROP TRIGGER IF EXISTS trg_risk_incidents_updated_at   ON risk_incidents;

CREATE TRIGGER trg_users_updated_at            BEFORE UPDATE ON users            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_wallets_updated_at          BEFORE UPDATE ON wallets          FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_transactions_updated_at     BEFORE UPDATE ON transactions     FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_payment_requests_updated_at BEFORE UPDATE ON payment_requests FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_risk_incidents_updated_at   BEFORE UPDATE ON risk_incidents   FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================
--  FIN V1 — 9 TABLAS CORE + TRIGGERS updated_at
-- =============================================================
