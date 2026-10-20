-- =============================================================
--  V13__referrals.sql — Referrals & Rewards (T27 / Requisito 31)
--
--  Modelo de negocio reconciliado con la config EXISTENTE
--  (micropay.referrals.*): cuando el invitado completa una ACCIÓN
--  CUALIFICANTE (un retiro >= withdrawal-threshold-amount, p. ej. 10 USD),
--  el referrer recibe un bono = reward-percent x monto cualificante.
--  La spec 31.2/31.3 habla genéricamente de "qualifying action"; aquí se
--  materializa como el retiro completado (ver WithdrawalService + ReferralService).
--
--  `referrals` es la tabla de atribución que ANTES NO EXISTÍA. El `code` de
--  users ya se genera en el signup (User.registerNew) y la atribución al
--  registrarse se guarda en users.referred_by; esta tabla crea una fila por
--  par (referrer, invitee) para trackear estado y bono.
--
--  DESVIACIÓN respecto al design.md (documentada): el design proponía
--  `code VARCHAR(16) PRIMARY KEY`, lo que impediría que un mismo referrer
--  tuviera MÁS DE UN invitado (colisión de PK con el mismo referral_code).
--  Se usa `id UUID PRIMARY KEY` + `code VARCHAR(16) NOT NULL` (código del
--  referrer) manteniendo la garantía de unicidad del par (referrer, invitee)
--  y de un solo invitee por referral.
-- =============================================================

SET search_path TO public;

CREATE TABLE referrals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(16) NOT NULL,               -- referral_code del referrer
    referrer_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee_id      UUID NULL REFERENCES users(id) ON DELETE SET NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','QUALIFIED','EXPIRED')),
    referrer_bonus  NUMERIC(19,4) NULL,
    invitee_bonus   NUMERIC(19,4) NULL,
    bonus_tx_id     UUID NULL REFERENCES transactions(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    qualified_at    TIMESTAMPTZ NULL
);

-- Un solo invitee por referral (un usuario sólo puede ser referido una vez)
CREATE UNIQUE INDEX ux_referrals_invitee ON referrals(invitee_id) WHERE invitee_id IS NOT NULL;

-- Garantía de UN SOLO bono por par (referrer, invitee)
CREATE UNIQUE INDEX ux_referrals_referrer_invitee
    ON referrals(referrer_id, invitee_id) WHERE invitee_id IS NOT NULL;

-- Listado de referidos por referrer (dashboard /me/referrals)
CREATE INDEX ix_referrals_referrer ON referrals(referrer_id);
CREATE INDEX ix_referrals_code ON referrals(code);

COMMENT ON TABLE  referrals IS 'Atribución de referidos por par (referrer, invitee) — bono REFERRAL_REWARD idempotente (Requisito 31)';
COMMENT ON COLUMN referrals.code IS 'referral_code del referrer (users.referral_code)';
COMMENT ON COLUMN referrals.status IS 'PENDING (atribuido) / QUALIFIED (bono pagado) / EXPIRED';
COMMENT ON COLUMN referrals.referrer_bonus IS 'Bono pagado al referrer = reward-percent x monto cualificante (moneda del monto cualificante)';
COMMENT ON COLUMN referrals.invitee_bonus IS 'Bono opcional al invitee (NULL si no aplica)';
COMMENT ON COLUMN referrals.bonus_tx_id IS 'Transacción REFERRAL_REWARD del crédito al referrer';
COMMENT ON COLUMN referrals.qualified_at IS 'Instante en que se cumplió la acción cualificante y se pagó el bono';

-- =============================================================
--  FIN V13 — referrals
-- =============================================================
