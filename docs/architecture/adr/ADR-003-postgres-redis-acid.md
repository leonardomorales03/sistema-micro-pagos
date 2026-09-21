# ADR-003 — PostgreSQL 16 ACID + Redis 7.2 y Transacciones Doble Entrada

| Campo       | Valor                                      |
|-----------|--------------------------------------------|
| **ID**     | ADR-003                                   |
| **Estado** | Aceptado — 2026-09-21                   |
| **Autor**  | MicroPagos Architecture Team              |
| **Área**  | Persistencia / Consistencia          |

---

## 1. Contexto

Sistema de micro-pagos **OBLIGATORIO ACID estricto:
- Transferencias **P2P** (saldo insuficiente → rollback total
- **SALDO** **DOBLE** (Transaction debe** **Ledger doble entrada**
- **Concurrencia extrema (1000 txns simultáneas (JMeter 35 propiedades ACID** correctas.
- **Velocity counters risk engine**: 5 reglas Redis en tiempo real.
- **Bloqueos distribuidos wallet por 10s durante transferencias.
- **Caché** listados recientes → reads frecuentes.

Evaluamos:
- **Postgres único + Redis (elegido
- **MySQL 8 → CHECK constraints JSON pobre; Postgres CHECK constraints robustos.
- **MongoDB** → No ACID multi-documento pre-4.0, pobre.
- **CockroachDB** → Overkill local.

## 2. Decisión

| Uso | Motor | Imagen Docker | Objetivo |
|---|---|---|---|
| **Datos OLTP** | **PostgreSQL 16 | `postgres:16-alpine | Fuente de VERDAD, ledger, tablas 12 tablas, ACID FK, CHECK, constraints, índices, concurrencia.
| **Caché counters | **Redis 7.2** | `redis:7.2-alpine` | Bloqueos, velocity, RiskEngine, caché listados, ShedLock scheduler distribuido.

**Garantías obligatoriasPostgres:
1.  `spring.jpa.hibernate.ddl-auto**:`validate` — NUNCA create-drop (Flyway ADR-004).
2.  **Constraint `CHECK (balance >= 0)` en tabla wallets.
3.  **Optimistic locking** `@Version` campo `version` en `WalletEntity`.
4.  **Double-entry ledger en `account_moves`:
    - Cada **Transaction** genera **2 movimientos**: debit en wallet_origen + crédito wallet_destino.
    - `TransactionService` con `@Transactional(propagation = REQUIRES_NEW, isolation = SERIALIZABLE o READ_COMMITTED + PostgreSQL advisory locks wallet id wallet
5.  **Índices obligatorios:
    - `(wallet_id, created_at DESC)
    - `(from_wallet_id)
    - `(to_wallet_id
    - `transactions.currency (
    - `users.email` UNIQUE
6.  **Postgres **`pgcrypto` extension UUIDs; `citext emails case-insensitive.

Redis:
1.  `wallet-lock:{id}} → TTL 10s SETNX
2.  `velocity:{userId}:{ruleName}}:window TTL dinámicoRiskEngine
3.  `txn-recent:{userId}` → TTL 30s caché lista
4.  `balance:{walletId}` → invalidación inmediata write-through
5.  `pay-code:{shortCode}` → TTL 24h PaymentRequest QR
6.  ShedLock `shedlock:{task}` → 10m lock distribuido

## 3. Consecuencias

### Positivas
✅ **ACID 100% PostgreSQL garantiza, 0 race conditions posibles con locks + constraints + locks + locks.
✅ **Double entry ledger** auditabilidad perfecta.
✅ **Redis velocity RiskEngine de riesgo en tiempo real (nanosegundos latencia).
✅ **Testcontainers en tests de integración → base reales → 0 mocks en tests integración.
✅ **Postgres 16 + JSONB notifications payload flexibles.

### Negativas / Riesgos
⚠️ **Postgres single point of failure si master down → replicarse con replica futura.
⚠️ **Redis datos de caché por Redis →** cae** al caer debe rehidratar desde Postgres, **nunca fuente verdad.
⚠️ **Optimistic locking puede fallos concurrentes → reintentar con exponential backoff.
⚠️ **Memory Redis 512MB local → aumentar producción.

### Mitigación
⚖️ Healthchecks readiness + retries con backoff.
⚖️ Spring Retry Template para todas txns y exponential backoff.
⚖️ Sentry /actuator/health para bases de datos.

