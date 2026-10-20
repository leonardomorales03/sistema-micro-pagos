-- =============================================================
--  beforeMigrate.sql — CALLBACK FLYWAY (evento BEFORE_MIGRATE)
--
--  Flyway lo ejecuta automáticamente ANTES de aplicar las migraciones
--  versionadas (V1…Vn) de esta misma carpeta (spring.flyway.locations).
--  No es una migración: no aparece en flyway_schema_history.
--
--  MOTIVO (hallazgo): V1__base_schema.sql declara `users.email CITEXT`,
--  pero V1 es inmutable y no crea extensiones. El único bootstrap que
--  las creaba es backend/infra/postgres/initdb/00-init-user-db.sql, y
--  sólo para la base `micropay` del volumen Docker. En cualquier base
--  nueva (CI, Testcontainers, `createdb` manual, scratch) V1 fallaba:
--      ERROR: type "citext" does not exist
--
--  Este callback garantiza las extensiones en CUALQUIER base nueva,
--  sin modificar V1 y sin depender del volumen/initdb de Docker.
--
--  NOTAS:
--    · Sólo extensiones "trusted" (creables por el owner de la BD).
--      `pg_stat_statements` requiere superusuario → se mantiene
--      únicamente en el initdb de Docker y NO se incluye aquí para
--      no romper el migrate con un error de privilegios.
--    · CREATE EXTENSION IF NOT EXISTS es idempotente: se ejecuta en
--      cada arranque y en bases ya inicializadas sin efecto alguno.
-- =============================================================

CREATE EXTENSION IF NOT EXISTS "citext";      -- users.email (CITEXT) — requerida por V1
CREATE EXTENSION IF NOT EXISTS "pgcrypto";    -- funciones criptográficas / gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";   -- uuid_generate_v4() (compatibilidad)
CREATE EXTENSION IF NOT EXISTS "btree_gist";  -- índices GiST para rangos
