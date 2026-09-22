# Plan de Implementación: Sistema de Micro-Pagos

## Visión General

Este plan implementa un sistema de micro-pagos con arquitectura hexagonal usando Java y Spring Boot. El sistema gestiona wallets digitales, procesa transacciones con garantías ACID, implementa caché Redis para optimización, y está preparado para producción con testing comprehensivo y CI/CD.

## Tareas

- [x] 1. Configurar estructura del proyecto y dependencias
  - Crear proyecto Spring Boot con Maven
  - Agregar dependencias: Spring Data JPA, PostgreSQL, Redis, jqwik, Testcontainers
  - Configurar estructura de paquetes según arquitectura hexagonal (domain, application, infrastructure, api)
  - Configurar application.properties con variables de entorno
  - _Requisitos: 5.1, 5.2, 8.3_

- [x] 2. Implementar capa de dominio
  - [x] 2.1 Crear value objects del dominio
    - Implementar `WalletId` con generación UUID
    - Implementar `TransactionId` con generación UUID
    - Implementar `Money` con validación de montos positivos y operaciones aritméticas
    - Definir escala y redondeo de `Money` (scale fija + estrategia de rounding explícita)
    - Definir límites de negocio para montos (mínimo/máximo) y validaciones correspondientes
    - Definir si el sistema soporta una sola moneda o múltiples (y, si aplica, agregar `Currency` como value object)
    - _Requisitos: 1.2, 2.5, 2.6, 14.1, 14.2, 14.3_
  
  - [x] 2.2 Implementar entidad Wallet
    - Crear clase `Wallet` con id, owner, balance
    - Implementar constructor que inicializa balance en cero
    - Implementar método `debit()` con validación de fondos suficientes
    - Implementar método `credit()` para agregar fondos
    - _Requisitos: 1.1, 1.2, 2.1, 2.5_
  
  - [x]* 2.3 Escribir property test para Wallet
    - **Property 1: Balance inicial cero**
    - **Property 9: Prevención de balances negativos**
    - **Valida: Requisitos 1.1, 2.5**
  
  - [x] 2.4 Implementar entidad Transaction
    - Crear clase `Transaction` con id, sourceWalletId, destinationWalletId, amount, timestamp, status
    - Implementar constructor que genera ID y timestamp automáticamente
    - Implementar métodos `markCompleted()` y `markFailed()`
    - _Requisitos: 2.3, 2.6_
  
  - [ ]* 2.5 Escribir property test para Transaction
    - **Property 7: Completitud de registro de transacciones**
    - **Property 10: Unicidad de identificadores de transacción**
    - **Valida: Requisitos 2.3, 2.6**
  
  - [x] 2.6 Crear excepciones de dominio
    - Implementar `DomainException` como clase base
    - Implementar `InsufficientFundsException`
    - Implementar `WalletNotFoundException`
    - Implementar `DuplicateWalletException`
    - Implementar `InvalidAmountException`
    - Implementar `ConcurrencyConflictException` para conflictos de concurrencia (si se usa locking optimista)
    - Implementar `IdempotencyConflictException` para solicitudes duplicadas con payload diferente
    - _Requisitos: 10.1, 10.2, 10.3, 12.3, 13.4_

- [x] 3. Definir puertos (interfaces)
  - [x] 3.1 Crear interfaz WalletRepository
    - Definir métodos: save, findById, findByOwner, existsByOwner
    - _Requisitos: 1.3, 1.5, 6.3_
  
  - [x] 3.2 Crear interfaz TransactionRepository
    - Definir métodos: save, findById, findByWalletId, findByWalletIdAndDateRange
    - _Requisitos: 2.3, 6.4_
  
  - [x] 3.3 Crear interfaz CachePort
    - Definir métodos: getBalance, putBalance, invalidateBalance
    - _Requisitos: 4.1, 4.3_

  - [ ] 3.4 Crear interfaz IdempotencyRepository (o TransactionRequestRepository)
    - Registrar y consultar solicitudes por `idempotencyKey`
    - Guardar huella del request (hash) + resultado (transactionId/response)
    - Detectar conflicto: misma key con request diferente
    - _Requisitos: 11.3, 12.1, 12.2, 12.3, 12.4_

  - [ ] 3.5 Crear interfaz AuditPort (o AuditRepository)
    - Registrar eventos de auditoría (creación de wallet, transacción iniciada, transacción completada/fallida)
    - Aceptar `correlationId` y `actor` (cuando exista autenticación)
    - _Requisitos: 15.1, 15.2, 15.3, 16.2_

- [x] 4. Implementar casos de uso (application layer)
  - [x] 4.1 Implementar CreateWalletUseCase
    - Validar que no existe wallet para el owner
    - Crear nueva wallet con balance cero
    - Persistir wallet usando repository
    - Retornar WalletId generado
    - _Requisitos: 1.1, 1.2, 1.5_
  
  - [ ]* 4.2 Escribir property tests para CreateWalletUseCase
    - **Property 2: Unicidad de identificadores de wallet**
    - **Property 4: Prevención de wallets duplicadas**
    - **Valida: Requisitos 1.2, 1.5**
  
  - [x] 4.3 Implementar GetWalletUseCase
    - Consultar caché primero
    - Si no está en caché, consultar repository y cachear
    - Manejar fallback si caché falla
    - Retornar información completa de wallet
    - _Requisitos: 1.3, 1.4, 4.1, 4.2, 4.5_
  
  - [ ]* 4.4 Escribir property tests para GetWalletUseCase
    - **Property 3: Round-trip de información de wallet**
    - **Property 14: Resiliencia ante fallo de caché**
    - **Valida: Requisitos 1.3, 1.4, 4.5**
  
  - [x] 4.5 Implementar ProcessTransactionUseCase con @Transactional
    - Validar que ambas wallets existen
    - Crear objeto Transaction
    - Ejecutar debit en wallet origen
    - Ejecutar credit en wallet destino
    - Persistir ambas wallets
    - Marcar transacción como completada y persistir
    - Invalidar caché de ambas wallets
    - Manejar excepciones y marcar transacción como fallida
    - Definir estrategia de concurrencia para evitar “double spend”
      - Opción A (recomendada): locking optimista (campo `version`) + reintentos controlados
      - Opción B: locking pesimista (select for update) con orden de locks consistente
    - Definir orden determinista para bloquear wallets (por ejemplo, ordenar por `walletId`) y evitar deadlocks
    - En caso de errores de concurrencia/serialización/deadlock, aplicar política de reintentos con backoff y límite
    - Integrar auditoría (registrar intento, éxito y fallo)
    - _Requisitos: 2.1, 2.2, 2.4, 2.5, 2.6, 3.1, 3.3, 4.3, 13.1, 13.2, 13.3, 13.4, 15.1_
  
  - [x]* 4.6 Escribir property tests para ProcessTransactionUseCase
    - **Property 5: Validación de fondos suficientes**
    - **Property 6: Conservación de suma total de balances**
    - **Property 8: Atomicidad en fallos**
    - **Property 13: Consistencia de caché después de transacción**
    - **Property 22: Idempotencia de creación de transacción** (misma key + mismo request ⇒ mismo resultado)
    - **Property 23: No doble gasto bajo concurrencia** (no permite balances negativos ni inconsistencias)
    - **Valida: Requisitos 2.1, 2.2, 2.4, 4.3, 12.2, 13.1**
  
  - [x] 4.7 Implementar GetTransactionHistoryUseCase
    - Implementar consulta por walletId
    - Implementar consulta por walletId y rango de fechas
    - Implementar consulta por transactionId
    - _Requisitos: 6.4_
  
  - [x]* 4.8 Escribir property tests para GetTransactionHistoryUseCase
    - **Property 15: Filtrado correcto de transacciones**
    - **Valida: Requisitos 6.4**

  - [ ] 4.9 Implementar idempotencia para creación de transacciones
    - Requerir `idempotencyKey` (por header) para el endpoint de crear transacción
    - Antes de procesar: consultar `IdempotencyRepository`
      - Si existe y el request coincide: retornar el mismo resultado (mismo transactionId)
      - Si existe y el request difiere: retornar conflicto (409) indicando uso incorrecto de la key
    - Después de procesar: persistir resultado asociado a la key (incluyendo estado COMPLETED/FAILED)
    - _Requisitos: 11.3, 12.1, 12.2, 12.3, 12.4_

- [x] 5. Checkpoint - Verificar lógica de dominio y casos de uso
  - Asegurar que todos los tests pasen, preguntar al usuario si surgen dudas.

- [x] 6. Implementar adaptadores de infraestructura
  - [x] 6.1 Crear entidades JPA
    - Crear `WalletEntity` con anotaciones JPA
    - Crear `TransactionEntity` con anotaciones JPA
    - Configurar relaciones y constraints
    - Agregar índices en columnas frecuentemente consultadas
    - Implementar columna `version` en `WalletEntity` si se usa locking optimista
    - Definir constraints/índices para idempotencia si se persiste en PostgreSQL
    - _Requisitos: 6.1, 6.2, 6.5, 12.4, 13.2_
  
  - [x] 6.2 Implementar PostgresWalletRepository
    - Extender JpaRepository
    - Implementar conversión entre Wallet (dominio) y WalletEntity (JPA)
    - Implementar todos los métodos de WalletRepository port
    - _Requisitos: 5.3, 6.1, 6.3_
  
  - [x] 6.3 Implementar PostgresTransactionRepository
    - Extender JpaRepository
    - Implementar conversión entre Transaction (dominio) y TransactionEntity (JPA)
    - Implementar queries personalizadas para filtrado por fecha
    - _Requisitos: 5.3, 6.2, 6.3, 6.4_
  
  - [ ]* 6.4 Escribir property test para integridad referencial
    - **Property 16: Integridad referencial**
    - **Valida: Requisitos 6.5**
  
  - [x] 6.5 Implementar RedisCacheAdapter
    - Configurar RedisTemplate con serialización apropiada
    - Implementar getBalance con manejo de Optional
    - Implementar putBalance con TTL de 5 minutos
    - Implementar invalidateBalance
    - Manejar excepciones de Redis (CacheException)
    - _Requisitos: 5.4, 4.1, 4.3, 4.4_

  - [ ] 6.6 Implementar IdempotencyRepository en infraestructura
    - Persistir registros de idempotencia (key, hash del request, transactionId, status, timestamps)
    - Implementar expiración (TTL lógico) o limpieza periódica
    - Asegurar unicidad por `idempotencyKey`
    - _Requisitos: 12.4, 12.2, 12.3_

  - [ ] 6.7 Implementar AuditAdapter
    - Persistir eventos de auditoría en PostgreSQL (tabla `audit_events`) o publicar a log estructurado
    - Guardar: actor, action, entityId, correlationId, timestamp, metadata
    - _Requisitos: 15.1, 15.2, 15.3, 16.2_

- [x] 7. Implementar capa de API REST
  - [x] 7.1 Crear DTOs para requests y responses
    - Crear `CreateWalletRequest` con validaciones
    - Crear `WalletResponse`
    - Crear `CreateTransactionRequest` con validaciones
    - Crear `TransactionResponse`
    - Agregar anotaciones de validación (@NotNull, @Positive, etc.)
    - _Requisitos: 10.2, 11.6_
  
  - [x] 7.2 Implementar WalletController
    - Implementar POST /api/wallets para crear wallet
    - Implementar GET /api/wallets/{id} para consultar wallet
    - Agregar validación de entrada
    - Configurar serialización JSON
    - _Requisitos: 11.1, 11.2, 11.6_
  
  - [x] 7.3 Implementar TransactionController
    - Implementar POST /api/transactions para crear transacción
    - Implementar GET /api/transactions/{id} para consultar transacción
    - Implementar GET /api/wallets/{id}/transactions para historial
    - Agregar validación de entrada
    - Exigir header `Idempotency-Key` en POST /api/transactions
    - Generar/propagar `X-Correlation-Id` (si no viene, generarlo) para trazabilidad
    - _Requisitos: 11.3, 11.4, 11.6, 12.1, 16.2_
  
  - [ ]* 7.4 Escribir tests para validación de formato JSON
    - **Property 21: Formato JSON válido**
    - **Valida: Requisitos 11.6**
  
  - [x] 7.5 Implementar GlobalExceptionHandler
    - Mapear InsufficientFundsException a 422 con mensaje descriptivo
    - Mapear WalletNotFoundException a 404
    - Mapear DuplicateWalletException a 409
    - Mapear errores de validación a 400
    - Mapear conflictos de concurrencia a 409 (o 423, definir estándar)
    - Mapear conflictos de idempotencia a 409 con mensaje claro
    - Mapear errores de sistema a 500
    - Asegurar que errores de cliente son 4xx y de servidor 5xx
    - _Requisitos: 10.1, 10.2, 10.3, 10.4, 12.3, 13.4, 17.2_

  - [ ]* 7.6 Escribir property tests para manejo de errores
    - **Property 17: Mensajes de error descriptivos para fondos insuficientes**
    - **Property 18: Validación de entrada inválida**
    - **Property 19: Error 404 para wallets inexistentes**
    - **Property 20: Códigos HTTP apropiados**
    - **Valida: Requisitos 10.1, 10.2, 10.3, 10.4**

  - [x] 7.7 Definir contrato de API (esquemas y ejemplos)
    - Documentar payloads JSON de requests/responses (wallet, transaction, errors)
    - Definir formato estándar de errores (por ejemplo, Problem Details RFC 7807)
    - Definir paginación y filtros del historial (page/size, start/end date, sort)
    - _Requisitos: 11.4, 11.6, 17.1, 17.2, 17.3_

  - [x] 7.8 Implementar seguridad mínima (recomendado para entorno real)
    - Autenticación (por ejemplo, JWT) y autorización: solo el owner puede iniciar débitos desde su wallet
    - Rate limiting en endpoints sensibles (crear transacción)
    - Validaciones anti-abuso básicas (límites por request/usuario)
    - _Requisitos: 18.1, 18.2, 18.3, 10.2_

- [x] 8. Implementar health checks
  - [x] 8.1 Configurar Spring Boot Actuator
    - Agregar dependencia de actuator
    - Exponer endpoints de health y readiness
    - Implementar health indicators para PostgreSQL y Redis
    - _Requisitos: 8.4_

- [x] 9. Checkpoint - Verificar integración completa
  - Asegurar que todos los tests pasen, preguntar al usuario si surgen dudas.

- [ ] 10. Crear tests de integración (T11: alternativa A aprobada)
  - [x] 10.1 Configurar infraestructura de integración
    - Agregar dependencias de Testcontainers para PostgreSQL y Redis
    - Alternativa A: reutilizar PostgreSQL 16 y Redis 7.2 de Docker Compose, sin iniciar Testcontainers.
    - Base exclusiva `micropay_test`, Redis DB 15; limpieza transaccional por caso y guardas de aislamiento.
    - _Requisitos: 7.2_
  
  - [ ]* 10.2 Escribir tests de integración end-to-end
    - Test de creación de wallet y consulta
    - Test de transacción completa entre dos wallets
    - Test de rollback en caso de error
    - Test de comportamiento de caché
    - **Property 11: Aislamiento en concurrencia**
    - **Property 12: Durabilidad después de commit**
    - **Valida: Requisitos 3.2, 3.4, 7.2**

  - Registro de desbloqueo temporal (2026-09-22, autorizado por el usuario):
    - **Pruebas excluidas: ninguna.** Surefire ejecuta las 35 propiedades `*Property.java`; Failsafe mantiene todos los `*IT.java`.
    - Única excepción: perfil Maven opt-in `local-progress`, que omite solo `jacoco:check`. Se mantienen instrumentación, informe y fallo del build ante cualquier prueba fallida. Sin ese perfil siguen vigentes 80% instrucciones y 70% ramas.
    - Comando de avance: `mvn -f backend/pom.xml clean verify -Pintegration,local-progress`.
    - Resultado verificado: `BUILD SUCCESS` en 17,661 s; 35 propiedades + 10 casos de integración, cero fallos/errores/omitidos. `check-boundaries.sh`: cero infracciones; dominio sin imports Spring/JPA.
    - Segunda ejecución con `mvn -f backend/pom.xml verify -Pintegration`: las 45 pruebas pasan nuevamente; falla exclusivamente JaCoCo por cobertura de instrucciones 69,81% y ramas 43,99%. Informe: `backend/target/site/jacoco/index.html`.
    - Preparación inicial, con Compose activo: `docker exec micropay-postgres createdb -U app_user -O app_user micropay_test` (solo si no existe).
    - Extensiones de la base aislada: `docker exec micropay-postgres psql -U app_user -d micropay_test -v ON_ERROR_STOP=1 -c 'CREATE EXTENSION IF NOT EXISTS pgcrypto; CREATE EXTENSION IF NOT EXISTS citext;'`.
    - No se modifica V1 ya publicada, no se ejecuta Flyway clean y no se limpia `micropay` ni Redis DB 0.
    - Core validado por pruebas: registro/login JWT, creación/consulta de wallets, transferencia e historial HTTP, partida doble, conservación de saldos, rechazo de usuario no propietario/saldo insuficiente/wallet congelada, conflicto 409 con tres intentos y liberación de locks.
    - Rollback reforzado: fallo real de PostgreSQL al persistir una transferencia después de guardar wallets; balances y ledger deben permanecer intactos. Las notificaciones de éxito se emiten solo después del commit.
    - [ ] Reprogramar cobertura antes de habilitar CI/CD (T28): añadir casos en las áreas no cubiertas hasta superar 80%/70%, verificar sin `local-progress` y eliminar el perfil temporal. No utilizar esta excepción como aprobación de producción.
    - [ ] Reprogramar Testcontainers al actualizar la combinación Docker Desktop/cliente; mantener la suite Compose como alternativa mientras tanto.
    - [ ] Completar Property 11 con carga concurrente real en T12. La prueba actual de contención Redis no sustituye el ensayo de 1000 concurrentes.
    - [ ] Revisar en T12 propiedad del lock tras vencer TTL y comportamiento del fallback local cuando Redis no está disponible; no están cubiertos por esta validación.
    - T11 permanece con cierre parcial hasta completar aislamiento bajo carga; no se declara cobertura global ni validación de todos los módulos del producto.

- [x] 11. Configurar containerización
  - [x] 11.1 Crear Dockerfile
    - Usar imagen base de Java consistente con el objetivo del proyecto (Java 17 o 21, decidir y unificar)
    - Copiar JAR de la aplicación
    - Exponer puerto 8080
    - Configurar HEALTHCHECK
    - _Requisitos: 8.1, 8.4_
  
  - [x] 11.2 Crear docker-compose.yml
    - Definir servicio app con variables de entorno
    - Definir servicio PostgreSQL con volumen persistente
    - Definir servicio Redis con volumen persistente
    - Configurar dependencias entre servicios
    - _Requisitos: 8.2_

- [ ] 12. Configurar CI/CD
  - [ ] 12.1 Crear GitHub Actions workflow
    - Configurar job de test que ejecuta todos los tests
    - Configurar job de análisis con SonarQube
    - Configurar job de build de Docker image
    - Configurar publicación de imagen en registry
    - Configurar que el pipeline falle si tests fallan
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [ ] 12.2 Alinear versiones de Java en CI/CD y contenedores
    - Asegurar que `setup-java` y la imagen del Dockerfile usan la misma versión (17 o 21)
    - _Requisitos: 9.1_

- [x] 13. Configurar logging
  - [x] 13.1 Configurar logging a stdout
    - Configurar formato de logs para containers
    - Agregar logging de errores con contexto
    - Configurar niveles de log por ambiente
    - _Requisitos: 8.5, 10.5_

  - [ ] 13.2 Configurar observabilidad (métricas y trazabilidad)
    - Agregar Micrometer + endpoint `/actuator/prometheus`
    - Medir: tasa de transacciones, latencia p95/p99, rollbacks, errores, cache hit ratio
    - Propagar `correlationId` en logs y respuestas
    - (Opcional) OpenTelemetry para traces y spans por request/use case
    - _Requisitos: 16.1, 16.2, 16.3, 10.5_

  - [ ] 13.3 Configurar auditoría mínima de transacciones
    - Registrar eventos de auditoría en cada operación crítica (crear wallet, transferir, error)
    - Asegurar que los logs/eventos no incluyan datos sensibles
    - _Requisitos: 15.1, 15.2, 15.3, 16.3_

- [x]* 14. Crear tests de carga con JMeter
  - [x]* 14.1 Plan de test 1000 hilos concurrentes A↔B 1 USD.
    - Archivo: `backend/performance/transfer-p2p-1000-concurrent.jmx`.
    - SetupThreadGroup prepara 2 usuarios JMeter (Alice/Bob), registra, activa email, login JWT, crea wallet USD.
    - ThreadGroup 1000 hilos, ramp-up 60s, Loop 10, direcciones A→B/B→A alternas por hilo (threadNum % 2).
    - Configuración parametrizada: `-Jthreads=1000 -Jrampup=60 -Jloops=10 -JseedAmount=1000000 -JtransferAmount=1 -Jhost=localhost -Jport=8080`.
    - Seeds iniciales Alice/Bob = seedAmount USD (JMeter JDBC Config deshabilitado; usar SQL directo: `UPDATE wallets SET balance = seed WHERE id IN (aId,bId);`).
  - [x]* 14.2 Assertions HTTP + JSON.
    - Éxito (200 OK con body.status == COMPLETED y tx.id/tx.type) OR 409 Conflict con error_code (lock distribuido / optimistic locking; Retry-After 2s en response).
    - Fallo duro (marcado): 422 BRV, 401/403 Auth/Roles, 5xx servidor.
    - JSR223 assertion valida JSON shape no malformed.
  - [x]* 14.3 Métricas p95/p99 + throughput.
    - Listeners: Summary Report + Aggregate Report + BackendListener InfluxDB (optional).
    - `jtl` output: `backend/performance/results/aggregate.jtl`; dashboard HTML: `backend/performance/results/dashboard`.
  - [x]* 14.4 Post-validación ACID (0 double spend, balances finales = iniciales).
    - Script: `backend/performance/sql/post-validate.sql` valida (1) double entry 2 movimientos por tx COMPLETED, (2) neto algebraico 0, (3) ningún balance < 0, (4) wallet.balance == último balanceAfter de ledger, (5) conservación global moneda Alice+Bob suma invariable.
  - **[ ] Reprogramar ejecución real T14 después de levantar backend `docker compose up backend` y poner 2 seed USD a wallets JMeter. Objetivo spec: throughput ≥ 120 tx/s, 0 doble gasto, 0 balances negativos, suma wallets global = inicial.**
  - **[ ] Reprogramar en T14: ejecutar con JMeter CLI non-GUI 3 veces, tomar mediana de throughput y percentiles.**
  - _Requisitos: 7.3_

- [ ] 15. Checkpoint final - Verificar sistema completo
  - Ejecutar todos los tests (unit, property, integration, load)
  - Verificar cobertura de código (objetivo: 80%)
  - Probar deployment con docker-compose
  - Verificar health checks
  - Asegurar que todos los tests pasen, preguntar al usuario si surgen dudas.

- [ ] 16. Autenticación, Autorización y Perfiles de Usuario + KYC base
  - [ ] 16.1 Implementar entidad User + UserProfile + roles (USER, MERCHANT, ADMIN, etc.)
    - Crear tabla `users` con email/phone/password_hash/roles/status y `user_profiles` con kyc_level, referral_code, theme/language
    - Implementar repositorios y conversiones dominio↔JPA
    - _Requisitos: 24.1, 24.2, 24.6_
  - [ ] 16.2 Implementar Spring Security: JWT access token + refresh token HttpOnly
    - Endpoints `/api/v1/auth/login`, `/signup`, `/refresh`, `/logout`, `/mfa/verify`
    - Password hashing Argon2/bcrypt; MFA TOTP (setup + verify endpoints)
    - CSRF double-submit cookie; SameSite=Lax en cookies; CORS por ambiente
    - _Requisitos: 18.1, 24.6, 38.1, 38.2, 38.3_
  - [ ] 16.3 Implementar Signup + verify email/phone + referral tracking
    - `SignupUserUseCase` genera ReferralCode único y aplica referrer si viene link
    - Enviar email de verificación con link de un solo uso
    - _Requisitos: 24.2, 31.1, 31.2_
  - [ ] 16.4 Definir KycProviderPort + implementar adaptador Mock/Sandbox + tier limits
    - Ports: `KycProviderPort`, `TierLimitsPort`
    - Implementar `InMemoryKycAdapter` para dev + hooks para proveedor real
    - Tabla/constantes de límites por KYCLevel+Country y chequeo en operaciones
    - _Requisitos: 24.3, 24.4, 24.5, 19.6, 20.6_
  - [ ] 16.5 Endpoints auth/perfil + authz `@PreAuthorize` wallet ownership
    - GET/PUT `/api/v1/me`, `/api/v1/me/profile`, `/api/v1/me/kyc`
    - Interceptores: `WalletOwnerSecurity` + Role-based filters
    - _Requisitos: 18.1, 26.1, 34.34_
  - [ ]* 16.6 Escribir property tests para perfiles y KYC
    - **Property 28: Aplicación de límites por tier**
    - **Property 34: Autorización wallet → user dueño**
    - _Valida: Requisitos 24.3, 18.1_

- [ ] 17. Recargas (Top-Up) con Payment Gateway
  - [ ] 17.1 Entidades del dominio TopUp + ports
    - `TopUp`, `TopUpId`, `TopUpStatus`; ports `TopUpRepository`, `PaymentGatewayPort`
    - Agregar tipos `TOP_UP` y valores al enum TransactionType / statuses
    - _Requisitos: 19.1, 19.5_
  - [ ] 17.2 Casos de uso CreateTopUpUseCase + HandleTopUpWebhookUseCase
    - Crear checkout session vía port + devolver redirectUrl
    - Webhook handler con HMAC verify + idempotency por provider_ref
    - Transacción atómica: creditar wallet + crear Transaction TOP_UP + invalidar cache
    - _Requisitos: 19.2, 19.3, 19.4, 19.6_
  - [ ] 17.3 Implementar adaptador StripeMock/Sandbox + webhook endpoint
    - `POST /api/v1/webhooks/gateways/stripe` público, firma validada
    - Modo SANDBOX vs LIVE con feature flag
    - _Requisitos: 19.3, 19.4_
  - [ ] 17.4 Endpoints REST Top-Up: POST list + GET status
    - `POST /api/v1/top-ups`, `GET /api/v1/top-ups/{id}`, `GET /api/v1/top-ups` paginado
    - _Requisitos: 19.5_
  - [ ]* 17.5 Property test webhook idempotente
    - **Property 22: Idempotencia webhook top-up**
    - _Valida: Requisitos 19.4_

- [ ] 18. Retiros (Withdrawals) + Payout Destinations + Payout Provider
  - [ ] 18.1 Dominio: `Withdrawal`, `PayoutDestination` + ports
    - Validación KYC ≥ VERIFIED antes de crear retiro
    - `PayoutProviderPort`, `WithdrawalRepository`, `PayoutDestinationRepository`
    - _Requisitos: 20.1, 20.3, 20.5_
  - [ ] 18.2 RequestWithdrawalUseCase con reserva de balance
    - @Transactional: validar límites → crear WITHDRAWAL tx → debitar wallet (reserva)
    - Llamar payout provider initiate; guardar provider_ref
    - _Requisitos: 20.2, 20.6_
  - [ ] 18.3 HandlePayoutUpdateUseCase + reversa en fallo
    - polling o webhook payout: si FAILED → unreserve (acreditar) wallet + marcar WITHDRAWAL FAILED
    - si COMPLETED → marcar COMPLETED; guardar auditoría
    - _Requisitos: 20.3, 20.4_
  - [ ] 18.4 CRUD payout destinations (masked labels + verified_at flag)
    - Endpoints: CRUD `/api/v1/me/payout-destinations`, set default
    - _Requisitos: 20.5_
  - [ ]* 18.5 Property test invariante reserva fallida
    - **Property 24: Reservas en retiros fallidos**
    - _Valida: Requisitos 20.4_

- [ ] 19. Payment Requests (Links/QR Comercio)
  - [ ] 19.1 Dominio PaymentRequest + ShortCode generator único
    - `PaymentRequest`, `PaymentRequestId`, `ShortCode`, `PaymentRequestStatus`
    - Port: `PaymentRequestRepository` (findByShortCode único)
    - _Requisitos: 21.1, 21.2_
  - [ ] 19.2 CreatePaymentRequestUseCase (MERCHANT only)
    - Generar shortCode colisión-safe, crear QR payload data
    - Devolver short URL, QR payload SVG/base64, status OPEN
    - _Requisitos: 21.1, 21.2_
  - [ ] 19.3 PayPaymentRequestUseCase cliente (cualquier USER)
    - @Transactional: validar OPEN, límites, saldo → transferencia P2P atomica → marcar PAID → pagar webhook merchant
    - Prevenir doble pago (idempotencia al nivel request id)
    - _Requisitos: 21.3, 21.4, 21.5, 21.6_
  - [ ] 19.4 Endpoints API merchant + público
    - `POST /api/v1/merchant/payment-requests`, `GET /api/v1/merchant/payment-requests`
    - Público: `GET /api/v1/public/payment-requests/{shortCode}`, `POST /api/v1/public/payment-requests/{id}/pay`
    - _Requisitos: 21.1, 21.3, 21.6_
  - [ ]* 19.5 Property tests PaymentRequest
    - **Property 25: No doble pago**
    - _Valida: Requisitos 21.5_

- [ ] 20. Plans, Subscriptions y Billing Scheduler
  - [ ] 20.1 Dominio: `Plan`, `Subscription` + ports
    - `BillingInterval` enum; `SubscriptionStatus` con TRIALING/ACTIVE/PAST_DUE/CANCELED/PAUSED
    - `PlanRepository`, `SubscriptionRepository` (findDueForBilling)
    - _Requisitos: 22.1, 22.2, 22.6_
  - [ ] 20.2 Casos de uso CreatePlan/Subscribe + manage
    - CreatePlanUseCase (MERCHANT), SubscribeToPlanUseCase (subscriber aprueba mandato)
    - CancelSubscriptionUseCase (no pro-rata por defecto), Pause/Resume
    - _Requisitos: 22.1, 22.2, 22.6_
  - [ ] 20.3 RunSubscriptionBillingJobUseCase + ShedLock + dunning policy
    - Scheduler cada 1h: busca next_billing_date ≤ now; por cada subscription → cobrar wallet o retry dunning
    - dunning policy configurable: reintentos día +1, +3, +7; superado → PAST_DUE/CANCELED
    - si billing ok: crear tx tipo SUBSCRIPTION, avanzar next_billing_date, notificar
    - _Requisitos: 22.3, 22.4, 22.5_
  - [ ]* 20.4 Property tests: nextBillingDate advance + idempotencia
    - **Property 27: Next billing en futuro tras éxito**
    - _Valida: Requisitos 22.4_

- [ ] 21. Refunds + FeeEngine
  - [ ] 21.1 FeeEnginePort + adaptador de reglas config (DB/properties)
    - FeeType: FLAT, PERCENTAGE, FX_SPREAD; FeeLines calculadas por contexto
    - Aplicación atómica: dentro de tx de operación se insertan entradas en `fee_ledger`
    - Reglas default iniciales: top-up %, withdrawal flat, MDR payment request, transfer P2P tier free
    - _Requisitos: 30.1, 30.2, 30.3, 30.4_
  - [ ] 21.2 ApplyFeesAndCommissionsUseCase helper (llamado dentro de tx sensitivas)
    - Integración: ProcessTx, CreateTopUp, RequestWithdrawal, PayPaymentRequest, SubscriptionBilling
    - Auditoría: cada fee ligado a transaction_id + rule_version
    - _Requisitos: 30.2, 30.4, 30.5_
  - [ ] 21.3 Dominio Refund + CreateRefundUseCase
    - `Refund` con sourceTxId, REFUND_TX_ID bidireccional, FULL/PARTIAL, reason
    - Validación: refunding party = recipient original o admin; amount ≤ refundable restante
    - Reverse transfer atomico + marcar source si TOTAL refounded
    - _Requisitos: 23.1, 23.2, 23.3, 23.4, 23.5, 23.6_
  - [ ] 21.4 Endpoints Refund
    - `POST /api/v1/transactions/{id}/refunds` (role merchant o admin)
    - `GET /api/v1/transactions/{id}/refunds` listar historial
    - _Requisitos: 23.1, 23.2, 23.4_
  - [ ]* 21.5 Property tests
    - **Property 26: Refunds acumulados ≤ original amount**
    - **Property 29: Conservación suma + fees**
    - _Valida: Requisitos 23.4, 30.2_

- [ ] 22. Notificaciones + Webhooks a Merchants
  - [ ] 22.1 NotificationPort + Email adapter (Thymeleaf + JavaMail + MailHog dev)
    - `UserNotification` entity + NotificationRepository (unread count)
    - Canal EMAIL plantillas Thymeleaf (topup.success, tx.received, kyc.status, etc.)
    - Canal IN_APP: persist + endpoint unread/list + mark read
    - _Requisitos: 25.1, 25.2, 25.3, 25.6_
  - [ ] 22.2 WebhookDeliveryPort + firma HMAC + retry queue
    - Configs por merchant: endpoint, signing secret, event mask
    - Payload: { id, type, timestamp, entity, data } + header `X-Signature: t=<ts>,v1=<hmac>`
    - Scheduler retries: backoff 1m/5m/15m/1h/6h/24h; persist attempts
    - _Requisitos: 25.4, 21.6_
  - [ ] 22.3 Endpoints merchant: webhook config + delivery history + manual redeliver + simulate test event
    - _Requisitos: 25.4, 25.5_
  - [ ]* 22.4 Property tests: HMAC verify invariant
    - **Property 32: HMAC signature verification**
    - _Valida: Requisitos 25.4_

- [ ] 23. Risk Engine + Tier Limits enforcement + AntiFraude
  - [ ] 23.1 RiskEnginePort + adaptador configurable rules.yaml/DB
    - RiskContext: { userId, tier, country, operation, amount, device, ip, historicalScores }
    - Decision ALLOW / CHALLENGE step-up / BLOCK + reasons[]
    - Default rules: velocity per hour/day, cross-border high risk list, unusual vs history, new device
    - _Requisitos: 27.1, 27.2, 27.5_
  - [ ] 23.2 Integración RunRiskCheckUseCase en operaciones críticas
    - Top-up first, withdrawal large, refund large, P2P > umbral
    - CHALLENGE → endpoint step-up MFA/email + reintento con challenge token
    - BLOCK → 403 genérico + auditoría detallada
    - _Requisitos: 27.3, 27.4, 27.6_
  - [ ] 23.3 Velocity counters Redis (risk:velocity:* + TTL granular)
    - Increment atómico Lua script; expiración por ventana de tiempo
    - _Requisitos: 27.2_

- [ ] 24. Admin Backend API + RBAC avanzado + Adjustments + Freeze wallet
  - [ ] 24.1 Roles y permisos RBAC: SUPPORT, COMPLIANCE, FINANCE, SUPER_ADMIN
    - Tablas rol grants; endpoints `/api/v1/admin/**` con `@PreAuthorize`
    - Backoffice requiere MFA obligatorio
    - _Requisitos: 26.1, 26.6_
  - [ ] 24.2 Admin CRUD APIs
    - Búsquedas: `/admin/users?search=`, `/admin/wallets/{id}`, `/admin/transactions` avanzado
    - Detalle usuario completo con KYC, wallets, listado tx, destinos, ajustes history, audit trail
    - _Requisitos: 26.2_
  - [ ] 24.3 AdminAdjustWalletUseCase + AdminFreezeWalletUseCase
    - ADJUSTMENT transaction con reason mandatorio; audit events obligatorios
    - Freeze wallet: marca wallet.status UNDER_REVIEW/FROZEN, bloquea débitos pero permite créditos entrantes
    - Unfreeze con reason; rollout de transacción bloqueada previa si aplica
    - _Requisitos: 26.3, 26.4_
  - [ ] 24.4 KYC Queue review endpoints (COMPLIANCE role)
    - Listado KYC en UNDER_REVIEW; acción approve/reject + reason obligatoria
    - Preview documentos (URL firmada, no descarga directa sin razón)
    - _Requisitos: 24.4, 24.5, 26.1_
  - [ ]* 24.5 Property tests: no-perdida-audit
    - **Property 31: Todo cambio crítico tiene audit event**
    - _Valida: Requisitos 15.1, 26.4, 26.6_

- [ ] 25. Reports, Exports CSV Asíncronos + Reconciliación
  - [ ] 25.1 ExportReportUseCase jobs asíncronos + storage S3-compat o local
    - Job encola, genera CSV grande en background, sube a storage, envía email con link temporal firmado
    - Endpoints: `POST /admin/reports/exports`, `GET /admin/reports/exports`, `GET /download/{token}`
    - Tipos: tx history, topups, withdrawals, per-user annual tax summary
    - _Requisitos: 28.1, 28.4, 28.5_
  - [ ] 25.2 Dashboard diario cashflow + fees collected endpoints (FINANCE)
    - Total credited by type, debited by type, net, fees segmentadas
    - _Requisitos: 28.2, 30.5_
  - [ ] 25.3 RunReconciliationJobUseCase diario vs providers
    - Match internal transactions vs provider CSV/API; generar tabla mismatches
    - Mismatch statuses: NEW, INVESTIGATING, RESOLVED
    - Endpoint `/admin/reconciliation/runs` + resolver UI
    - _Requisitos: 28.3_

- [ ] 26. Multi-moneda + FxRatePort (feature-flagged, opcional para fase D)
  - [ ]* 26.1 Extender Money Value Object para multi-currency + Currency enum soportado
    - Scale por moneda (JPY 0, COP 2 internal / 0 display, USD/EUR 2, 4 si es necesario)
    - _Requisitos: 29.1, 29.5_
  - [ ]* 26.2 FxRatePort adaptador (proveedor externo + fallback + cache Redis TTL corto)
    - Cross-currency conversion atomica dentro de tx; persist rate used en metadata transaction
    - _Requisitos: 29.2, 29.3, 29.4_
  - [ ]* 26.3 Actualizar endpoints: soporte currency param en topups, wallets show balance per currency
    - Feature flag `FEATURE_MULTICURRENCY_ENABLED`
    - _Requisitos: 29.6_
  - [ ]* 26.4 Property test FX consistencia
    - **Property 33: Cross-currency FX invariant**
    - _Valida: Requisitos 29.3, 29.5_

- [ ] 27. Referrals & Rewards (opcional crecimiento *)
  - [ ]* 27.1 Generación referral code único al signup + referrer attribution via URL
    - Port ReferralRepository; endpoint `/me/referrals` listado estado
    - _Requisitos: 31.1_
  - [ ]* 27.2 PayReferralBonusUseCase idempotente por regla+pareja
    - Trigger: qualifying action (tier-1 completado + first topup ≥ $X)
    - Transacción REFERRAL_BONUS con rule idempotency key
    - _Requisitos: 31.2, 31.3, 31.4_
  - [ ]* 27.3 Property test referral
    - **Property 30: Un solo bono por par referrer+invitee+rule**
    - _Valida: Requisitos 31.3_

- [ ] 28. Checkpoint - Backend robusto completo (antes de Frontend)
  - Correr todos los tests (unit + property + integration)
  - Smoke test Postman/Newman collection contra todos los endpoints nuevos
  - Validar en docker-compose + mailhog webhooks
  - ✅ Validación cumplimiento Reglas Separación: correr `scripts/check-boundaries.sh` y confirmar 0 TS/TSX en `backend/`, 0 JAVA/SQL en `frontend-*/`, package.json/vite.config SOLO en su carpeta
  - Confirmar que QR generation solo devuelve `payload string` en endpoints (ninguna dependencia java qrcode/zxing en pom.xml backend)
  - Confirmar que backend envía valores brutos numéricos + ISO timestamps (ninguna fecha formateada como "14 mar 2026" en JSON — eso es responsabilidad frontend)
  - Preguntar al usuario dudas antes de iniciar frontend

- [ ] 29. Setup Mono-repo + Frontend User SPA base
  - [ ] 29.1 Estructura mono-repo según design.md (`backend/`, `frontend-user/`, `frontend-admin/`, `infra/`)
    - Makefile: targets `dev`, `backend`, `frontend-user`, `frontend-admin`, `seed`, `test`, `e2e`
    - `.env.example` con todas las vars; docker-compose con postgres, redis, mailhog, nginx proxy local
    - _Requisitos: 39.1, 39.2, 39.4_
  - [ ] 29.2 Scaffold `frontend-user` React 18 + TS + Vite + Tailwind + shadcn/ui + React Router v6
    - Lucide icons, react-hook-form + zod, zustand stores, i18next inicial (es-CO default)
    - Build optimizado: code-splitting, alias `@/*`, source maps produccion separados
    - _Requisitos: 32.1, 32.6, 34.1, 34.2, 34.3_
  - [ ] 29.3 Generar typescript client desde OpenAPI spec y build step
    - Paquete `openapi-typescript-codegen` o similar; comando `make generate-api-client`
    - Envoltorio axios: auth interceptor, refresh token on 401, idempotency-key auto-inject mutaciones
    - Marcar carpeta `frontend-*/src/lib/api-client/` como `.generated` o `.gitignore` (según política); NUNCA se edita a mano — check checksum en CI
    - _Requisitos: 37.1, 37.2, 37.3, 37.4, 35.3, design.md "Prohibiciones" fila 12_
  - [ ] 29.4 Seed data base (demo users y mercados, sample tx)
    - Flyway seed o shell script `infra/seed/seed-db.sh` para popular dashboard visual desde primer run
    - NINGÚN archivo .java ni .tsx vive en `infra/seed/`; solo SQL/JSON/ejecutables shell
    - _Requisitos: 39.3, design.md "Reglas Ubicación" punto 3_
  - [ ] 29.5 Aplicar Reglas Estrictas de Separación Back ↔ Front (implementación tooling)
    - Crear `scripts/check-boundaries.sh` con al menos 5 comprobaciones: TS/TSX en `backend/` → fallo, JAVA/SQL en `frontend-*/` → fallo, package.json en raíz con dependencias React → fallo, vite.config/pom.xml fuera de su carpeta → fallo, archivos i18n UI `.json` en `backend/src/main/resources/i18n` → fallo
    - Agregar pre-commit hook (lefthook o husky) que corre `check-boundaries.sh` y bloquea commit si pasa algo
    - Dividir QR funcionalidad: BACKEND solo genera `payload string` del código (ej: short URL /pay/XX) en use case + endpoint; FRONTEND usa `qrcode.react` para SVG/Canvas. NINGUNA dependencia java-qrcode/zxing en backend por esta regla de separación UX-vs-data.
    - _Requisitos: design.md "Reglas Estrictas Separación" §1 QR, §2 Ubicación, §3 Prohibiciones, §4 Scripts_
  - [ ] 29.6 Configuración Linter / Formatter y build EXCLUSIVOS por proyecto (sin global sin ruta)
    - `backend/`: pom.xml (maven) + checkstyle.xml + spotless en `backend/` solo; NO archivos maven fuera de `backend/`
    - `frontend-user/`: `package.json` (React), `.eslintrc.cjs`, `.prettierrc`, `vite.config.ts`, `tsconfig.json` SOLAMENTE en su carpeta
    - `frontend-admin/`: idem, propias configs independientes; si se comparte ESLint config, via `infra/shared-configs/` + resolución paquete local, NO copiando un eslintrc genérico en raíz
    - _Requisitos: design.md "Reglas Estrictas Separación" §1 fila Linter / §3 prohibiciones fila 4 y 10_
  - [ ] 29.7 Variables de entorno con PREFIJOS ESPECÍFICOS por proyecto (sin nombres genéricos)
    - En `.env.example` raíz, nombrar con prefijos: `SPRING_DATASOURCE_URL`, `SPRING_REDIS_HOST`, `VITE_USER_API_URL`, `VITE_ADMIN_API_URL`, `POSTGRES_USER`, `REDIS_PORT`
    - ELIMINAR cualquier variable genérica tipo `API_URL=`, `DB_HOST=`, `JWT_SECRET=` sin prefijo que pueda ser interpretada por ambos stacks
    - Frontend solo consume `VITE_*` (o `PUBLIC_*` si es Astro/otro); backend NO lee variables con prefijo `VITE_` en ninguna circunstancia
    - _Requisitos: design.md "Reglas Estrictas Separación" §1 fila Variables de Entorno / §3 prohibiciones fila 11_
  - [ ] 29.8 Documentar en `docs/architecture/adr/002-back-front-boundaries.md` ADR
    - Registro Arquitectura Decisión: motivación, regla #0 inquebrantable, mapa responsabilidades tabla, lista prohibiciones, scripts validación, pre-commit, CI boundaries-job, diagrama de dependencias
    - Agregar link al ADR en el README.md sección "Arquitectura y Límites Back / Front"
    - _Requisitos: design.md "Reglas Estrictas Separación" sección completa_

- [ ] 30. Frontend User — Auth & Onboarding (login/signup/mfa/verify)
  - [ ] 30.1 Páginas auth: Login, Signup, Verify Email, MFA Setup/Verify, Reset Password flow
    - Validación inline via zod, loading skeletons, errores RFC7807 parseados por axios interceptor → toast
    - Redirect guard: si token válido → /dashboard; si no → /auth/login
    - _Requisitos: 32.2, 34.1, 35.5_
  - [ ] 30.2 Layout Dashboard: Sidebar (collapsible + drawer móvil) + Topbar con notif/bell + theme/lang/user menu
    - Rutas protegidas; breadcrumbs; versión desktop 12-col grid; mobile 360px responsive 1-col
    - _Requisitos: 32.2, 32.5_
  - [ ]* 30.3 Property test tipo — e2e contrato errores
    - **Property 35: RFC7807 consistent error toast**
    - _Valida: Requisitos 17.2, 35.5_

- [ ] 31. Frontend User — Dashboard Home, Wallet & Quick Actions
  - [ ] 31.1 Dashboard home: Balance card grande + hide balance toggle ojo + quick actions (Enviar / Recibir / Recargar / Retirar)
    - Resumen 7d gráfico barras tiny Recharts + KYC upsell card si unverified
    - _Requisitos: 32.2, 32.2, 32.3, Design System "Balance siempre visible"_
  - [ ] 31.2 Página Wallet detalle: Balance total / moneda (o por moneda) + historial resumido
    - _Requisitos: 32.2_
  - [ ] 31.3 Optimistic UI updates + TanStack Query queryClient cache invalidation táctico
    - Actualizar balance local inmediatamente tras POST tx; rollback si 4xx/5xx no idempotente
    - _Requisitos: 35.1, 35.2_

- [ ] 32. Frontend User — Enviar/Recibir P2P + Activity Detallado
  - [ ] 32.1 Flujo Enviar 3 pasos: Destinatario (email/QR/ID + recent list) → Monto → Confirmación c/fee breakdown
    - Modal paso 3 pide confirmación clara con monto, comisión, total + beneficiary name destacado
    - _Requisitos: 32.2, 32.3, UX Flow B_
  - [ ] 32.2 Activity: listado transacciones TanStack Table con filtros, búsqueda, paginación
    - Colores inbound (verde ↗️) vs outbound (rojo ↘️), badges status, icono + texto, click → Drawer receipt
    - Drawer receipt: detalles, copy button IDs, share link interno, timestamp dual (local+UTC)
    - _Requisitos: 32.4, 32.2_
  - [ ] 32.3 Página pública `pay/:shortCode` checkout PaymentRequest (fuera del layout dashboard)
    - Resumen monto/comercio + opción login + pagar con tarjeta Stripe Checkout fallback
    - _Requisitos: 21.3, 32.2, Wireframe 5_

- [ ] 33. Frontend User — Recargas + Retiros
  - [ ] 33.1 Top-Up: grid proveedores (Stripe/Wompi/PlaceToPay/PSE/Bancolombia/Nequi/Daviplata/PayPal logo + desc), input monto con masking COP (separador de miles punto, 0 decimales al display) + límites por tier en COP
    - Redirect al checkout; al volver dashboard polling hasta status SUCCEEDED + toast
    - _Requisitos: 19.2, UX Flow A_
  - [ ] 33.2 Withdraw: upsell KYC si tier<VERIFIED; selector destino verificado; monto + límite; resumen fee flat
    - Historial retiros con filtros; detalle status y referencia provider
    - _Requisitos: 20.1, 20.2, 20.5_

- [ ] 34. Frontend User — Profile, KYC, Security, Settings, Notifications, Referrals
  - [ ] 34.1 Profile personal: name, country, phone editables; referral code section con botón compartir
    - Referrals list: referidos, estado, bono pagado
    - _Requisitos: 32.2, 31.1, 31.4_
  - [ ] 34.2 KYC center: stepper estado (no empezado → email/phone → docs → revisión → aprobado)
    - Upload KYC docs via drag&drop (react-dropzone) + preview + submit
    - _Requisitos: 24.4, 34.2_
  - [ ] 34.3 Security: cambiar password, MFA setup QR + 6-digit, devices list, cerrar sesiones remotas
    - _Requisitos: 32.2_
  - [ ] 34.4 Settings: apariencia tema Light/Dark/System, densidad, idioma ES/EN/PT, notificaciones canal toggles
    - Persist via Zustand + localStorage + sync al backend en user profile (preferred_language, theme)
    - _Requisitos: 34.2, 34.3_
  - [ ] 34.5 Panel notificaciones: drawer bell con lista in-app, marcar leído, ver todas, preferencias
    - _Requisitos: 25.2, 25.3_

- [ ] 35. Frontend Merchant — Dashboard, Payment Requests CRUD, Plans, Subscriptions, Webhooks
  - [ ] 35.1 Merchant Dashboard: cards ventas hoy/semana/mes + gráfico Recharts MRR (si hay subs)
    - _Requisitos: 33.1_
  - [ ] 35.2 Crear Payment Request: formulario izquierda, preview QR + short link derecha en vivo
    - Descargar PNG QR, copiar enlace, compartir WhatsApp/mail
    - _Requisitos: 33.2, Wireframe 2_
  - [ ] 35.3 Plans CRUD + lista subscribers: retry failed billing button, export MRR CSV
    - _Requisitos: 33.3_
  - [ ] 35.4 Webhooks config: endpoint URL, rotar secret, tabla delivery history + retry manual + botón test ping
    - _Requisitos: 33.4_

- [ ] 36. Frontend Admin SPA (Backoffice)
  - [ ] 36.1 Admin login: separado, 2FA obligatorio, IP whitelist configurable (backend check)
    - _Requisitos: 36.1_
  - [ ] 36.2 Admin Dashboard métricas plataforma + Users search + User detail page (Wireframe 4)
    - Ajuste wallet dialog + reason, freeze/unfreeze modal, KYC queue panel approve/reject reason
    - _Requisitos: 26.2, 26.3, 26.4, 36.2, Wireframe 4_
  - [ ] 36.3 Transactions search avanzada + Reports/exports center + Audit log browser
    - _Requisitos: 26.2, 26.5, 28.1, 28.4, 36.3_

- [ ] 37. i18n, Themes, Accesibilidad WCAG, Offline banners + Error boundaries
  - [ ] 37.1 Catalogos i18n 3 idiomas base (es-CO, en-US, pt-BR) con locale es-CO DEFAULT + formatting COP via Intl.NumberFormat('es-CO', style:currency, currency:'COP', maxFractionDigits:0)
    - Dates/numbers/currencies formateados con Intl API o date-fns locale
    - _Requisitos: 34.2_
  - [ ] 37.2 Themes Light/Dark completo: CSS variables, respeto OS preference, toggle settings, persist
    - Paleta colorblind-friendly settings + icons alongside status (no solo color)
    - _Requisitos: 34.3, 34.4_
  - [ ] 37.3 Pass WCAG 2.1 AA checks automatizados (axe-core dev)
    - Focus visibles, contraste 4.5:1 normal, labels todos inputs, ARIA correcto, teclado navegable
    - _Requisitos: 34.1_
  - [ ] 37.4 Error boundaries React + offline banner (navigator online) + retries idempotentes GET
    - Frontend error tracking port (Sentry adapter stub)
    - _Requisitos: 35.4, 35.5_

- [ ] 38. E2E Tests Playwright (Critical Journeys) + Visual Regression
  - [ ]* 38.1 Setup Playwright + docker-compose CI stack ephemeral con seed
    - Config 3 navegadores (chromium, firefox, webkit); traces + screenshots en fallo
    - _Requisitos: 40.1, 40.3_
  - [ ]* 38.2 Specs journeys core (happy + key failures)
    - signup/verify/login; KYC tier-1; top-up mocked gateway; P2P seeded users; payment request flow pay; subscription+fire scheduler; withdrawal + admin payout complete
    - _Requisitos: 40.2_
  - [ ]* 38.3 Visual regression screenshots key pages (home, tx detail, pay qr, merchant plans, admin user detail)
    - _Requisitos: 40.4_

- [ ] 39. CI/CD Completo + Build Docker full stack + generación cliente API
  - [ ] 39.1 GitHub Actions ampliado: checkouts backend + frontend, build backend jar, build frontend-user y admin
    - Jobs orchestration: lint → unit/property backend → unit frontend → integration Testcontainers → build imagen app → e2e Playwright → push images → deploy preview (opcional)
    - _Requisitos: 9.1, 9.2, 9.4, 9.5, 40.3_
  - [ ] 39.2 SonarQube ampliado a frontend coverage (vitest) + linter ESLint/Prettier
    - _Requisitos: 9.3_
  - [ ] 39.3 Generación cliente OpenAPI automatizada en pipeline + commit bot si diff
    - _Requisitos: 37.1, 37.2, 37.3_
  - [ ] 39.4 Job CI OBLIGATORIO `boundaries-lint` que bloquea merge si se detecta mezcla back/front
    - Primer job en el workflow (antes de unit tests): ejecuta `scripts/check-boundaries.sh` contra todo el árbol de archivos del PR
    - Checksum check: hash `openapi.yml` vs archivos en `frontend-*/src/lib/api-client/` (si api-client es commitable) — si difieren sin step generate-api-client → falla PR (evita edición a mano del cliente)
    - Validación variables entorno .env.example: alerta si existe algún nombre sin prefijo `SPRING_` / `VITE_` / `POSTGRES_` / `REDIS_` genérico
    - _Requisitos: design.md "Reglas Estrictas Separación" §4 Scripts_

- [ ] 40. Checkpoint final Full Stack
  - Ejecutar todos los tests (backend unit/property/integration, frontend unit, E2E Playwright)
  - Cobertura backend ≥80% + frontend ≥70%
  - Smoke en docker-compose local completo: login usuario, enviar dinero, ver QR payment, admin aprobar KYC, webhook mock Stripe
  - Lighthouse audit ≥90 en dashboard + checkout (Performance/Accessibility/BP/SEO)
  - ✅ Auditoría FINAL Reglas Separación Back ↔ Front (OBLIGATORIO — se debe documentar PASS en el issue de release):
    - 0 archivos TS/TSX en `backend/` y 0 archivos JAVA/SQL/Flyway en `frontend-*/`
    - NINGÚN `import` cruzado directo back↔front (solo comunicación HTTP + cliente TS autogenerado)
    - `cliente API autogenerado` sin modificaciones manuales (comparar checksum contra spec)
    - Variables entorno `.env.example` todas con prefijo correcto (ninguna genérica `API_URL=`)
    - División QR validada: endpoint devuelve `{ qrPayload: "https://host/pay/AbX99K" }`, front renderiza SVG; búsqueda en pom.xml backend de `zxing`/`qrcode` = 0 resultados
    - NINGÚN mensaje UI estilizado ("pulsa el botón azul") en DTOs/strings backend (solo eventType + metadata)
    - Permisos enforcement: grep de código frontend de "puede retirar" / validation lógica tier en front = sólo UX disable, endpoint backend re-valida
  - Revisión docs ADR de arquitectura (incluido ADR 002 back-front boundaries) + .env.example + README sección "🧪 Límites Back / Front" con link a ADR
  - Asegurar que todo pasa; preguntar al usuario antes de marcar como entregado.

- [ ] 41. Modo Sandbox Global + Prism Mock Server + Docs Tarjetas de Prueba
  - [ ] 41.1 Crear `application-sandbox.yml` Spring Profile maestro
    - Configurar inyección condicional de adaptadores mock/in-memory para TODOS los ports: PaymentGatewayPort → MockPaymentGatewayAdapter, PayoutProviderPort → MockPayoutProviderAdapter, KycProviderPort → InMemoryKycAdapter, NotificationPort → LogOnlyNotificationAdapter, FxRatePort → FixedFxRateAdapter, FeeEnginePort → ZeroFeesAdapter, RiskEnginePort → AllowAllRiskEngineAdapter
    - Feature flags por adapter en `mp.adapters.*` con `@ConditionalOnProperty`
    - _Requisitos: design.md "Testing Tools & Sandbox Mode" §1_
  - [ ] 41.2 Endpoints de testing solo-activos-en-sandbox (`/api/v1/_sandbox/*`) — protegidos por profile y 404 en prod
    - `POST /_sandbox/trigger-topup-webhook` → simula webhook exitoso para cualquier top-up dado `{ topUpId, status: SUCCEEDED|FAILED }`
    - `POST /_sandbox/trigger-payout-update` → marca payout como COMPLETED o FAILED y aplica reversa si corresponde
    - `POST /_sandbox/kyc/approve-user` → aprueba KYC de `{ userId }` a tier VERIFIED y notifica
    - `POST /_sandbox/time/shift` → avanza reloj lógico para tests (Clock mock / Bean override) para subscription billing
    - `GET /_sandbox/notifications/{userId}` → lista últimas notificaciones que no se enviaron realmente
    - _Requisitos: design.md "Testing Tools & Sandbox Mode" §1 endpoints sandbox_
  - [ ] 41.3 Prism Mock Server + servicio docker-compose
    - Crear `infra/mock-server/` + Dockerfile Prism stoplight/prism:5
    - Agregar bloque `prism-mock-api` a docker-compose con puerto 4010 + volumen a `docs/api/openapi.yml`
    - Agregar Vite env var `VITE_MOCK_API_URL=http://localhost:4010` y script npm `pnpm dev:mock` para frontend modo Prism-only (sin backend vivo)
    - _Requisitos: design.md "Testing Tools & Sandbox Mode" §2_
  - [ ] 41.4 CI: Contract Testing con Prism Validate
    - Job GitHub Actions: después de build backend → descarga openapi.yml → corre `prism validate` contra staging/live endpoint o test de integración
    - _Requisitos: design.md "Testing Tools & Sandbox Mode" §2 Extensiones recomendadas_
  - [ ] 41.5 Documentación tarjetas de prueba + plantilla README testing
    - Archivo `docs/TESTING_CARDS.md` con tablas completas de Stripe, Wompi (Bancolombia), PlaceToPay (PSE/AÉROPUERTO), PayPal Sandbox, Conekta legacy, + números de prueba PSE, Cuentas Bancarias Bancolombia/BBVA Colombia, Pruebas Nequi/Daviplata modo sandbox
    - Bloque en README.md raíz: "🧪 Modo Testing / Pruebas" con 3 subsecciones (Sandbox Full, Mock Server Prism, Modo Test Providers) y comandos copy-paste
    - En frontend Top-up grid provider: tooltips con números de prueba para cada provider, solo visibles cuando `NODE_ENV !== 'production'`
    - _Requisitos: design.md "Testing Tools & Sandbox Mode" §3_
  - [ ] 41.6 Integración Clock Bean / reloj lógico para tests de scheduler
    - Extraer `Clock` como Bean configurable en Spring; en profile sandbox usar `MutableClock` para `time/shift` endpoint
    - Usar Clock en todos los Instant.now() del dominio y scheduler billing
    - Escribir prueba property/integration que avanza 31 días en sandbox y valida que subscription cobra EXACTAMENTE 1 vez por ciclo
    - _Requisitos: design.md "Testing Tools & Sandbox Mode" §1 `time/shift` endpoint_
  - [ ] 41.7 Checkpoint Sandbox: smoke flujo completo 100% offline
    - Prueba manual/script: `SPRING_PROFILES_ACTIVE=sandbox make dev` → signup user → approbar KYC vía _sandbox endpoint → top-up via _sandbox trigger-topup-webhook → P2P a merchant → crear payment request → cobrar → subscription billing con time/shift → retiro via trigger-payout-update
    - Validar que NO se hizo ninguna llamada externa (Logs de adaptadores Mock confirman inbound-only)
    - ✅ Validación Reglas Separación en Sandbox: todos los adaptadores Mock viven en `backend/src/main/java/.../infrastructure/adapters/sandbox/` — NINGÚN adaptador mock vive en `frontend-*/` ni importa dependencias de UI
    - Endpoints `/api/v1/_sandbox/*` solo existen si profile=sandbox (valida que con profile prod se recibe HTTP 404); front NO contiene lógica condicional con código Java, solo llama a rutas via cliente TS autogenerado
    - Preguntar al usuario si la configuración es la deseada antes de cerrar esta tarea.

- [ ] 42. Fork País Específico: COLOMBIA (COP) — KYC, Impuestos, Proveedores locales, UX CO
  - [ ] 42.1 Country Policy Port + Feature Flags
    - Implementar interface `CountryPolicyPort` con métodos `getDefaultCurrency()`, `getDefaultLocale()`, `getDefaultTimezone()`, `getKycRequiredDocs(tier)`, `applyTaxRules(tx, feeAmount)`
    - Implementar 1ª policy concreta: `ColombiaCountryPolicy` (config activado con `mp.country.default=CO` en `application.yml` default)
    - Fallback policy `GenericCountryPolicy` (USD/EUR sin impuestos específicos) para multi-país
    - _Requisitos: design.md "Especificidades País Colombia" §1_
  - [ ] 42.2 KYC Colombiano: validaciones y tier limits COP
    - Extender valor documento con validación algoritmo **módulo 11 cédula colombiana** (CC 8-10 dígitos, CE, NIT+DV) en capa Domain
    - 5 tiers KYC según design.md: UNVERIFIED(0), BASIC(1), VERIFIED(2-PN), VERIFIED-PJ(2bis PJ), ACCREDITED(3) — con límites COP por defecto del spec
    - Screening listas UAF/SARLAFT básico stub en `InMemoryKycAdapter` y extensión puerto para provider real
    - Validar requisito: NO se permiten RETIROS en tier 0 y 1 (enforcement server-side, no solo UX)
    - _Requisitos: design.md "Especificidades País Colombia" §2 y §6 Topes_
  - [ ] 42.3 FeeEngine COLOMBIA: IVA 19%, ReteFuente 2.5%, ReteICA x ciudad, ReteIVA (desglose obligatorio en fee_ledger y UI)
    - Extender `FeeEnginePort` con `ColombianFeesAdapter` que calcula 4 impuestos automáticamente si `country=CO`
    - Agregar 4 nuevos tipos a `FeeType` enum: IVA_19, RETE_FUENTE_25, RETE_ICA_CITY, RETE_IVA_15
    - Desglose completo en transacción response: `breakdown.feeSvcAmount`, `breakdown.taxIva`, `breakdown.taxReteFte`, `breakdown.taxReteIca`, `breakdown.taxReteIva`, `breakdown.totalFee`
    - IMPORTANTE: invariant "impuestos NUNCA se calculan sobre el monto enviado/recibido, solo sobre fee" — property test con jqwik 200 iteraciones para validar
    - Feature flags toggleables: `mp.taxes.iva_enabled`, `mp.taxes.rete_fte_enabled`, `mp.taxes.rete_ica_enabled`, `mp.taxes.rete_iva_enabled`, `mp.taxes.gmf_4x1000_enabled` (opcional bancos)
    - _Requisitos: design.md "Especificidades País Colombia" §3_
  - [ ] 42.4 Proveedores pago locales COLOMBIA: Adaptadores Wompi, PlaceToPay, PSE, Nequi, Daviplata
    - `WompiPaymentGatewayAdapter` (PaymentGatewayPort): top-ups tarjeta + Nequi + Daviplata con firma HMAC webhook
    - `PlaceToPayPaymentGatewayAdapter` (PaymentGatewayPort): top-up tarjeta + **PSE (Pagos Seguros en Línea)** con status polling
    - `WompiPayoutProviderAdapter` / `PlaceToPayPayoutProviderAdapter` (PayoutProviderPort): retiros Bancolombia/BBVA ACH y Nequi/Daviplata
    - Idempotency + firma webhook verify en ambos adaptadores (evitar doble cobro/abono por replay)
    - Property test jqwik: "Dado webhook replay con mismo eventId 10 veces → sistema crea solo 1 transacción"
    - _Requisitos: design.md "Especificidades País Colombia" §4_
  - [ ] 42.5 UX Colombia Frontend: locale es-CO, format COP 0 decimales, listados DANE dptos+municipios
    - i18n `es-CO` default con formato fechas `dd/MM/yyyy`, moneda `Intl.NumberFormat('es-CO',{style:'currency',currency:'COP',maximumFractionDigits:0})`
    - Input masking moneda en send/topup/withdraw: `$100.000` (separador de miles `.`; sin decimales al display). Decimal interno `COP 2` separado con coma solo si hace falta por FX, pero el valor COP nativo se muestra como entero
    - Topup grid provider: badge `RECOMENDADO COLOMBIA` destacado 1º Wompi (Tarjeta/Nequi/Daviplata) y 2º PlaceToPay (PSE)
    - Autocompletado departamentos/municipios Colombia (fuente DANE JSON zip en frontend) en KYC dirección
    - Dropdown tipos documento orden específico CO: CC, CE, TI, RC, Pasaporte, NIT(PJ)
    - Banner fijo footer SARLAFT "Cumplimiento UAF Colombia · Sistema Gestión LA/FT" con link política
    - Validación teléfonos `+57` con `libphonenumber-js` region `CO`
    - _Requisitos: design.md "Especificidades País Colombia" §5 + Requirement 34 i18n COP es-CO_
  - [ ] 42.6 Límites y validaciones específicas COLOMBIA (TierLimitsPort Colombia impl)
    - Implementar `ColombiaTierLimitsAdapter` con todos los topes tabla de design.md §6
    - Config properties externas: `mp.limits.tier2.monthly_topup_cop=20000000`, etc. — configurables en runtime (sin deploy) via admin console
    - Endpoint `GET /api/v1/limits/me` que retorna límites actuales usuario + usage (ej: `{ "monthlyTopupUsed": 12500000, "monthlyTopupMax": 20000000 }`) → front muestra barra progreso / tope usado
    - Min monto top-up COP $5.000, min retiro $50.000 COP (validation 422 si pasa)
    - _Requisitos: design.md "Especificidades País Colombia" §6_
  - [ ] 42.7 Test Cards y Pruebas Colombia documentadas + smoke flujo end-to-end COP
    - Popular `docs/TESTING_CARDS.md` con todas las tablas: Wompi, PlaceToPay, PSE, Bancos, Nequi, Daviplata, Stripe Connect Colombia
    - E2E Playwright test **journey COLOMBIA**: Signup → Complete KYC VERIFIED(2) con docs test dummy → Top-Up $150.000 COP via Tarjeta Wompi test 4242 → Enviar $100.000 a Merchant (fee 1% = $1.000 COP, IVA19 $190, Total Fee $1.190) → Merchant retira $95.000 a Bancolombia (éxito)
    - Property test fiscal: "Para toda transacción con fee + IVA 19%, el total debitado emisor = monto + fee + iva; monto acreditado receptor = monto"
    - _Requisitos: design.md Testing + "Especificidades País Colombia" §3 + Tarea 41_
  - [ ] 42.8 🔴 Checkpoint Fork COLOMBIA listo
    - Compilar aplicación con `mp.country.default=CO` y smoke test endpoints 200: `GET /api/v1/me` retorna `preferredLanguage:'es-CO'`, `timezone:'America/Bogota'`, `wallet.currency:'COP'`
    - Dashboard front user: balance se visualiza `$ 2.450.750 COP` formato correcto miles punto, sin decimales
    - KYC flow: validar cédula válida módulo 11 pasa, cédula inválida da error específico "Cédula no cumple dígito verificación"
    - Fees desglose: crear transferencia con fee1% y validar 4 rows fee_ledger (FEE_SVC, IVA_19, RETE_FTE_25, RETE_ICA) + propiedad invariant impuestos no afectan monto enviado
    - Confirmar al usuario antes de cerrar: ¿Todos los comportamientos colombianos se ven correctos?

## Notas

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia requisitos específicos para trazabilidad
- Los checkpoints aseguran validación incremental
- Los property tests validan propiedades de corrección universales
- Los unit tests validan ejemplos específicos y casos borde
- La configuración de jqwik debe usar mínimo 100 iteraciones por property test
- Cada property test debe incluir un tag con formato: `@Tag("Feature: sistema-micro-pagos, Property {N}: {título}")`
