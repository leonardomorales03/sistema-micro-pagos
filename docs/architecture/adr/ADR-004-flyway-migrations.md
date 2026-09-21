# ADR-004 — Migraciones Flyway vs JPA auto-DDL Validate

| Campo       | Valor                                      |
|-----------|--------------------------------------------|
| **ID**     | ADR-004                                   |
| **Estado** | Aceptado — 2026-09-21                   |
| **Autor**  | MicroPagos Architecture Team              |
| **Área**  | Schema / Evolución base datos                |

---

## 1. Contexto

El sistema debe tener **versionar** evolución del schema** base datos (usuarios/transacciones suscripciones con:
- Entornos dev / test / sandbox / prod
- **Deployments zero-downtime migrations (blue/green).
- Garantizar schema idéntico en todos environments.
- **No perder datos existentes** en producción (nunca drop tables.

Opciones evaluadas:
1.  **JPA auto-ddl `create-drop | update` → FÁCIL pero PELIGROSO destruir prod.
2.  **Liquibase** → potente, XML verbose.
3.  **Flyway Community Edition ← ** (Flyway 10.

## 2. Decisión

**Flyway 10+** en `backend/src/main/resources/db/migration/`.

**Reglas de uso Flyway obligatorias:

### Naming estricto: `V{VERSION}__{snake_case_descripcion.sql`.
```
V1__base_schema.sql           — 9 Tablas iniciales
V2__add_kyc_tables.sql    — KYC
V3__payment_requests.sql       — PaymentRequests
V4__subscriptions.sql        — Subscriptions
...
```

**Spring Boot Configuración:
```yaml
spring.flyway:
  enabled: true
  locations: classpath:db/migration
  baseline-on-migrate: false
  out-of-order: false          # NUNCA permitir migraciones fuera de orden
  validate-on-migrate: true     # Validar checksum en cada startup
  encoding: UTF-8
```

JPA: **`validate` SOLO: spring.jpa.hibernate.ddl-auto: validate

## 3. Consecuencias

### Positivas
✅ **Schema versionado auditado por Git.
✅ **100% reproducible en todos entornos.
✅ **Checksum integridad corrompe modificar una aplicada corrompe.
✅ **Rollback forward-only** migraciones aplicadas → NUNCA down scripts rollback forward.
✅ **Compatible zero-downtime deploy añadir constraints y constraints.
✅ **Tests integrar con GitHub Actions migra antes deploy.

### Negativas
⚠️ **No rollback automático scripts DROP rollback manual via nuevas scripts añadir columnas en su lugar.
⚠️ **Nueva columna nullable + default producción debe aplicar PRIMERO el código leer la columna + nullable.

### Neutros
⚖️ Las migraciones add columna nullable con default vía SQL `ALTER TABLE` con lock time con PostgreSQL `pg_repack` futuro.
⚖️ NUNCA `UPDATE` masivos directos en Flyway sin backup.

