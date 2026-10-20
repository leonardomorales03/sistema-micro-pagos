-- =============================================================================
--  V12 — Transaction metadata (multi-moneda / FX, T26)
--  Dueña exclusiva de V12. V1..V11 son inmutables.
--  Contenido:
--    1. `transactions.metadata` — metadatos JSONB de la transacción
--       (rate FX usado, origen de la cotización y otros extras de la tx).
--  Requisitos: 29.1, 29.2, 29.3.
-- =============================================================================

SET search_path TO public;

-- ─────────────────────────────────────────────────────────────────────────────
--  1. transactions.metadata — JSONB nullable, retrocompatible con filas previas
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE transactions ADD COLUMN metadata JSONB NULL;

COMMENT ON COLUMN transactions.metadata IS
    'Metadatos JSONB de la transacción (p. ej. {fxBase,fxQuote,fxRate,fxSource} en conversiones FX). NULL para tx previas.';
