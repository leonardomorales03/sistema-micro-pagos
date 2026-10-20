# Micropay Backend

Backend del sistema de micro-pagos construido con Spring Boot 3.3, Java 21 y arquitectura hexagonal. Expone una API REST versionada bajo `/api/v1` y centraliza la lógica transaccional, seguridad, auditoría, conciliación y flujos financieros del proyecto.

## Stack

- Java 21
- Spring Boot 3.3.4
- PostgreSQL 16
- Redis 7.2
- Flyway
- Spring Security + JWT
- Stripe Sandbox
- ShedLock
- jqwik

## Funcionalidades principales

- Auth, registro y sesiones
- Wallets y transferencias P2P
- Top-ups y withdrawals
- Payment requests públicas y comercio
- Refunds y fee engine
- Suscripciones
- Notificaciones y webhooks firmados
- Risk engine con `428 Precondition Required`
- Administración, RBAC y auditoría
- Reportes, conciliación, FX por flag y referidos

## Estructura

```text
backend/
├── infra/                         # Docker compose local, Nginx e initdb
├── performance/                   # JMeter y SQL de validación
├── src/main/java/                 # Código fuente
├── src/main/resources/            # application*.yml y migraciones Flyway
├── src/test/java/                 # Unit, property e integration tests
├── Dockerfile
├── micropay-postman-collection.json
└── pom.xml
```

## Requisitos

- Java 21
- Maven 3.9+
- Docker / Docker Compose

## Base de datos local

Si usas el `docker compose` del proyecto sin cambios, estos son los datos de conexión de desarrollo:

- Host: `localhost`
- Puerto: `5432`
- Base de datos: `micropay`
- Usuario: `app_user`
- Contraseña: `changeme_local_only`

URL JDBC:

```text
jdbc:postgresql://localhost:5432/micropay
```

Base de datos de tests:

- Host: `localhost`
- Puerto: `5432`
- Base de datos: `micropay_test`
- Usuario: `app_user`
- Contraseña: `changeme_local_only`

Si levantaste la infraestructura con un `.env` propio, la contraseña efectiva puede cambiar.

## Levantar el backend local

Infraestructura:

```bash
cd backend/infra
docker compose up -d
```

Aplicación:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## URLs útiles

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Actuator health: `http://localhost:8080/actuator/health`
- Adminer: `http://localhost:8081`

## Configuración

Las variables base están en el `.env.example` de la raíz del repo. Las más importantes para este módulo son:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_JWT_SECRET_KEY`
- `STRIPE_SECRET_KEY`
- `STRIPE_WEBHOOK_SIGNING_SECRET`
- `FEATURE_MULTICURRENCY_ENABLED`

## Migraciones

- Flyway gestiona el esquema.
- `V1__base_schema.sql` es la base inmutable.
- El proyecto incluye migraciones `V1` a `V13`.
- `beforeMigrate.sql` prepara extensiones de PostgreSQL necesarias.

## Testing

Validación de límites:

```bash
bash scripts/check-boundaries.sh
```

Tests unitarios y property:

```bash
mvn -f backend/pom.xml test
```

Validación funcional usada en T28:

```bash
mvn -f backend/pom.xml verify -Pintegration,local-progress
```

Validación estricta con cobertura:

```bash
mvn -f backend/pom.xml verify -Pintegration
```

Estado actual:

- `verify -Pintegration,local-progress` está verde.
- `verify -Pintegration` todavía falla por cobertura JaCoCo global pendiente.

## Postman

La colección está en:

```text
backend/micropay-postman-collection.json
```

Incluye endpoints, descripciones y ejemplos de respuesta.

## CI

Workflow principal:

```text
.github/workflows/ci.yml
```

Incluye jobs de boundaries, backend tests, build Docker y análisis opcional con Sonar.

## Notas operativas

- El health de `mail` puede aparecer `DOWN` en local si no hay SMTP, sin afectar el core.
- La API usa respuestas de error RFC 7807 con `error_code`.
- La infraestructura del backend vive dentro de `backend/infra` para mantener autonomía del módulo.
