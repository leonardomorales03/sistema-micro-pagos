-- ============================================================
--  00-init-user-db.sql — Script inicial PostgreSQL
--  Ejecutado POR postgres:16-alpine en primer arranque
--  docker-entrypoint-initdb.d (sólo si el volumen está vacío)
-- ============================================================

-- ──────────────────────────────────────────────
--  1. ROLES
-- ──────────────────────────────────────────────
-- Asegurar existencia del usuario de conexión (owner de tablas)
DO $$
BEGIN
  IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles WHERE rolname = 'app_user'
  ) THEN
      CREATE ROLE app_user LOGIN PASSWORD '${POSTGRES_PASSWORD:-changeme_local_only}';
  END IF;
END
$$;

-- ──────────────────────────────────────────────
--  2. BASE DE DATOS
-- ──────────────────────────────────────────────
SELECT 'CREATE DATABASE micropay OWNER app_user'
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = 'micropay'
)\gexec

REVOKE ALL ON DATABASE micropay FROM PUBLIC;
GRANT  ALL ON DATABASE micropay TO app_user;

-- ──────────────────────────────────────────────
--  3. CONECTAR A micropay & EXTENSIONES REQUERIDAS
-- ──────────────────────────────────────────────
\connect micropay

CREATE EXTENSION IF NOT EXISTS "pgcrypto";        -- gen_random_uuid(), pgcrypto
CREATE EXTENSION IF NOT EXISTS "citext";           -- case-insensitive texto (email UQ)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";         -- uuid_generate_v4() compatibilidad
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";   -- análisis de queries lentas
CREATE EXTENSION IF NOT EXISTS "btree_gist";       -- índices GiST para rangos
GRANT ALL ON SCHEMA public TO app_user;
GRANT ALL ON ALL FUNCTIONS IN SCHEMA public TO app_user;

-- ──────────────────────────────────────────────
--  4. PARAMETROS GLOBALES ESQUEMA
-- ──────────────────────────────────────────────
ALTER DATABASE micropay SET timezone TO 'America/Bogota';
ALTER ROLE app_user SET search_path TO public;
ALTER ROLE app_user SET timezone TO 'America/Bogota';

-- ──────────────────────────────────────────────
--  5. COMENTARIOS INFORMATIVOS
-- ──────────────────────────────────────────────
COMMENT ON DATABASE micropay IS
    'Sistema de Micro-Pagos — Base de datos OLTP principal (ACID)';
