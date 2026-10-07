-- =============================================================
--  V2__users_auth_kyc_tier.sql
--  NUEVA TABLA user_profiles (1:1 con users) + Tier Limits por KYC level
--  + Índices KYC + profile user lookup único
--  NUNCA MODIFICAR V1; esta es la migración incrementativa post-v1 T16.
-- =============================================================

SET search_path TO public;

-- ─────────────────────────────────────────────────────────────
--  1. TABLA user_profiles (1:1 con users — Aggregate User extiende profile DTO embedded)
--     Mantiene settings usuario, preferencias, preferencia de moneda,
--     theme/language, notification channels, datos de perfil que NO están
--     en la tabla users core (datos frecuentes vs datos opcionales).
-- ─────────────────────────────────────────────────────────────
CREATE TABLE user_profiles (
    user_id                 UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    language                VARCHAR(5) NOT NULL DEFAULT 'es_CO'
                                CHECK (language IN ('es_CO','es_ES','en_US','pt_BR')),
    theme                   VARCHAR(16) NOT NULL DEFAULT 'SYSTEM'
                                CHECK (theme IN ('LIGHT','DARK','SYSTEM')),
    preferred_currency      VARCHAR(3) NOT NULL DEFAULT 'USD',
    timezone                VARCHAR(64) NOT NULL DEFAULT 'America/Bogota',
    push_enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    email_notif_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    marketing_opt_in        BOOLEAN NOT NULL DEFAULT FALSE,
    address_line            VARCHAR(200),
    address_city            VARCHAR(100),
    address_country         VARCHAR(2),
    billing_tax_id          VARCHAR(32),
    profile_picture_url     VARCHAR(512),
    bio                     VARCHAR(200),
    referral_bonus_claimed  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE user_profiles IS 'Perfil extendido 1:1 con users — preferencias UI, notificaciones, dirección, settings';
COMMENT ON COLUMN user_profiles.referral_bonus_claimed IS 'TRUE si el usuario ya cobró bono referido (para no duplicar)';

-- ─────────────────────────────────────────────────────────────
--  2. TABLA tier_limits — límites por KYC level + country + moneda
--     (RiskEngine lee esta tabla para enforcement).
--     Populada por seed data.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE tier_limits (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kyc_level           VARCHAR(16) NOT NULL
                            CHECK (kyc_level IN ('LEVEL_0','LEVEL_1','LEVEL_2')),
    country             VARCHAR(2) NOT NULL DEFAULT 'CO',
    currency            VARCHAR(3) NOT NULL DEFAULT 'USD',
    max_single_tx       NUMERIC(24,8) NOT NULL CHECK (max_single_tx > 0),
    max_daily_volume    NUMERIC(24,8) NOT NULL CHECK (max_daily_volume >= 0),
    max_monthly_volume  NUMERIC(24,8) NOT NULL CHECK (max_monthly_volume >= 0),
    max_wallet_balance  NUMERIC(24,8) NOT NULL CHECK (max_wallet_balance >= 0),
    max_topups_per_day  INTEGER NOT NULL DEFAULT 10 CHECK (max_topups_per_day >= 0),
    max_withdrawals_per INTEGER NOT NULL DEFAULT 10 CHECK (max_withdrawals_per >= 0),
    withdrawal_tier_fee NUMERIC(8,4) NOT NULL DEFAULT 0 CHECK (withdrawal_tier_fee >= 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (kyc_level, country, currency)
);

COMMENT ON TABLE tier_limits IS 'Catálogo límites operativos por KYC+País+Moneda (RiskEngine enforcement)';

CREATE INDEX ix_tierlimits_country_level ON tier_limits(country, kyc_level);

-- Seed límites razonables CO (USD/COP) — valores placeholder, se cambian por admin luego
INSERT INTO tier_limits (kyc_level,country,currency,max_single_tx,max_daily_volume,max_monthly_volume,max_wallet_balance,max_topups_per_day,max_withdrawals_per,withdrawal_tier_fee) VALUES
 ('LEVEL_0','CO','USD', 200,       400,        2000,          5000,          2,                 1,                       0.01),
 ('LEVEL_1','CO','USD', 2000,      10000,      50000,         50000,         20,                10,                      0.005),
 ('LEVEL_2','CO','USD', 50000,     200000,     1000000,       2000000,       200,               100,                     0.002),
 ('LEVEL_0','CO','COP', 1000000,   2000000,    10000000,      25000000,      2,                 1,                       0.01),
 ('LEVEL_1','CO','COP', 10000000,  50000000,   250000000,     250000000,     20,                10,                      0.005),
 ('LEVEL_2','CO','COP', 250000000, 1000000000, 5000000000,    10000000000,   200,               100,                     0.002);

-- Backfill: crea row user_profiles para usuarios existentes (vacío por ahora, en V2 seed base si hubiera)
INSERT INTO user_profiles (user_id)
SELECT id FROM users u WHERE NOT EXISTS (SELECT 1 FROM user_profiles p WHERE p.user_id = u.id);

-- Trigger updated_at user_profiles y tier_limits (reusamos set_updated_at() declarada en V1)
DROP TRIGGER IF EXISTS trg_user_profiles_updated_at ON user_profiles;
CREATE TRIGGER trg_user_profiles_updated_at BEFORE UPDATE ON user_profiles FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_tier_limits_updated_at ON tier_limits;
CREATE TRIGGER trg_tier_limits_updated_at BEFORE UPDATE ON tier_limits FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================
--  FIN V2 — user_profiles + tier_limits + seed límites
-- =============================================================
