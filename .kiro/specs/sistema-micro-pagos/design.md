# Documento de Diseño: Sistema de Micro-Pagos

## Visión General

El sistema de micro-pagos es una aplicación diseñada con arquitectura hexagonal que gestiona wallets digitales y procesa transacciones entre usuarios. El sistema implementa garantías ACID completas, utiliza caché Redis para optimización de rendimiento, y está preparado para producción con contenedorización y pipelines CI/CD.

### Objetivos Principales

- Gestionar wallets digitales con balances precisos
- Procesar transacciones con garantías ACID
- Optimizar rendimiento mediante caché de balances
- Mantener independencia del dominio respecto a la infraestructura
- Proporcionar APIs REST para interacción con clientes
- Soportar despliegue containerizado y CI/CD

### Tecnologías Clave

- **Lenguaje**: Java con Spring Boot
- **Base de Datos**: PostgreSQL para persistencia ACID
- **Caché**: Redis para optimización de lecturas
- **Arquitectura**: Hexagonal (Ports & Adapters)
- **Testing**: JUnit, Testcontainers, JMeter
- **Containerización**: Docker, Docker Compose
- **CI/CD**: GitHub Actions, SonarQube

## Arquitectura

### Arquitectura Hexagonal

El sistema sigue el patrón de arquitectura hexagonal (Ports & Adapters) para mantener el dominio independiente de la infraestructura:

```
┌─────────────────────────────────────────────────────────┐
│                     API REST Layer                       │
│                  (Controllers/Adapters)                  │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                  Application Layer                       │
│                    (Use Cases)                           │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                   Domain Layer                           │
│              (Entities, Value Objects)                   │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                Infrastructure Layer                      │
│         (PostgreSQL, Redis, Repositories)                │
└──────────────────────────────────────────────────────────┘
```

### Capas del Sistema

**1. Domain Layer (Núcleo)**
- Entidades: `Wallet`, `Transaction`
- Value Objects: `WalletId`, `Money`, `TransactionId`
- Interfaces de puertos: `WalletRepository`, `TransactionRepository`, `CachePort`
- Lógica de negocio pura sin dependencias externas

**2. Application Layer (Casos de Uso)**
- `CreateWalletUseCase`: Crear nuevas wallets
- `GetWalletUseCase`: Consultar información de wallets
- `ProcessTransactionUseCase`: Ejecutar transferencias entre wallets
- `GetTransactionHistoryUseCase`: Consultar historial de transacciones
- Orquesta operaciones del dominio a través de puertos

**3. Infrastructure Layer (Adaptadores)**
- `PostgresWalletRepository`: Implementación de persistencia para wallets
- `PostgresTransactionRepository`: Implementación de persistencia para transacciones
- `RedisCacheAdapter`: Implementación de caché para balances
- Gestión de transacciones de base de datos

**4. API Layer (Controladores REST)**
- `WalletController`: Endpoints para gestión de wallets
- `TransactionController`: Endpoints para procesamiento de transacciones
- Manejo de errores y validación de entrada
- Serialización JSON

## Componentes e Interfaces

### Entidades del Dominio

#### Wallet
```java
public class Wallet {
    private WalletId id;
    private String owner;
    private Money balance;
    
    public Wallet(String owner) {
        this.id = WalletId.generate();
        this.owner = owner;
        this.balance = Money.zero();
    }
    
    public void debit(Money amount) {
        if (balance.isLessThan(amount)) {
            throw new InsufficientFundsException();
        }
        this.balance = balance.subtract(amount);
    }
    
    public void credit(Money amount) {
        this.balance = balance.add(amount);
    }
}
```

#### Transaction
```java
public class Transaction {
    private TransactionId id;
    private WalletId sourceWalletId;
    private WalletId destinationWalletId;
    private Money amount;
    private Instant timestamp;
    private TransactionStatus status;
    
    public Transaction(WalletId source, WalletId destination, Money amount) {
        this.id = TransactionId.generate();
        this.sourceWalletId = source;
        this.destinationWalletId = destination;
        this.amount = amount;
        this.timestamp = Instant.now();
        this.status = TransactionStatus.PENDING;
    }
    
    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
    }
    
    public void markFailed() {
        this.status = TransactionStatus.FAILED;
    }
}
```

### Puertos (Interfaces)

#### WalletRepository
```java
public interface WalletRepository {
    Wallet save(Wallet wallet);
    Optional<Wallet> findById(WalletId id);
    Optional<Wallet> findByOwner(String owner);
    boolean existsByOwner(String owner);
}
```

#### TransactionRepository
```java
public interface TransactionRepository {
    Transaction save(Transaction transaction);
    Optional<Transaction> findById(TransactionId id);
    List<Transaction> findByWalletId(WalletId walletId);
    List<Transaction> findByWalletIdAndDateRange(
        WalletId walletId, 
        Instant start, 
        Instant end
    );
}
```

#### CachePort
```java
public interface CachePort {
    Optional<Money> getBalance(WalletId walletId);
    void putBalance(WalletId walletId, Money balance);
    void invalidateBalance(WalletId walletId);
}
```

### Casos de Uso

#### ProcessTransactionUseCase
```java
@Transactional
public class ProcessTransactionUseCase {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final CachePort cache;
    
    public TransactionResult execute(TransactionCommand command) {
        // 1. Validar que ambas wallets existen
        Wallet source = walletRepository.findById(command.sourceId())
            .orElseThrow(() -> new WalletNotFoundException());
        Wallet destination = walletRepository.findById(command.destinationId())
            .orElseThrow(() -> new WalletNotFoundException());
        
        // 2. Crear transacción
        Transaction transaction = new Transaction(
            source.getId(),
            destination.getId(),
            command.amount()
        );
        
        try {
            // 3. Ejecutar transferencia
            source.debit(command.amount());
            destination.credit(command.amount());
            
            // 4. Persistir cambios
            walletRepository.save(source);
            walletRepository.save(destination);
            
            // 5. Marcar transacción como completada
            transaction.markCompleted();
            transactionRepository.save(transaction);
            
            // 6. Invalidar caché
            cache.invalidateBalance(source.getId());
            cache.invalidateBalance(destination.getId());
            
            return TransactionResult.success(transaction.getId());
            
        } catch (Exception e) {
            transaction.markFailed();
            transactionRepository.save(transaction);
            throw e; // Rollback automático por @Transactional
        }
    }
}
```

## Modelos de Datos

### Esquema PostgreSQL

#### Tabla: wallets
```sql
CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    owner VARCHAR(255) NOT NULL UNIQUE,
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT positive_balance CHECK (balance >= 0)
);

CREATE INDEX idx_wallets_owner ON wallets(owner);
```

#### Tabla: transactions
```sql
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    source_wallet_id UUID NOT NULL,
    destination_wallet_id UUID NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_source_wallet FOREIGN KEY (source_wallet_id) 
        REFERENCES wallets(id),
    CONSTRAINT fk_destination_wallet FOREIGN KEY (destination_wallet_id) 
        REFERENCES wallets(id),
    CONSTRAINT positive_amount CHECK (amount > 0)
);

CREATE INDEX idx_transactions_source ON transactions(source_wallet_id);
CREATE INDEX idx_transactions_destination ON transactions(destination_wallet_id);
CREATE INDEX idx_transactions_timestamp ON transactions(timestamp);
```

### Estructura de Caché Redis

```
Key Pattern: wallet:balance:{walletId}
Value: Decimal string representation of balance
TTL: 300 seconds (5 minutes)

Example:
Key: "wallet:balance:550e8400-e29b-41d4-a716-446655440000"
Value: "1250.50"
TTL: 300
```

## Propiedades de Corrección

*Una propiedad es una característica o comportamiento que debe mantenerse verdadero en todas las ejecuciones válidas de un sistema - esencialmente, una declaración formal sobre lo que el sistema debe hacer. Las propiedades sirven como puente entre las especificaciones legibles por humanos y las garantías de corrección verificables por máquina.*

### Propiedad 1: Balance inicial cero
*Para cualquier* wallet recién creada, el balance inicial debe ser exactamente cero.
**Valida: Requisitos 1.1**

### Propiedad 2: Unicidad de identificadores de wallet
*Para cualquier* conjunto de wallets creadas, todos los identificadores deben ser únicos.
**Valida: Requisitos 1.2**

### Propiedad 3: Round-trip de información de wallet
*Para cualquier* wallet creada, recuperarla debe devolver el mismo owner y balance actual.
**Valida: Requisitos 1.3, 1.4**

### Propiedad 4: Prevención de wallets duplicadas
*Para cualquier* usuario, intentar crear una segunda wallet debe ser rechazado con error.
**Valida: Requisitos 1.5**

### Propiedad 5: Validación de fondos suficientes
*Para cualquier* transacción donde el balance de origen es menor que el monto, la transacción debe ser rechazada.
**Valida: Requisitos 2.1**

### Propiedad 6: Conservación de la suma total de balances
*Para cualquier* transacción válida entre dos wallets, la suma total de ambos balances antes y después debe ser igual.
**Valida: Requisitos 2.2**

### Propiedad 7: Completitud de registro de transacciones
*Para cualquier* transacción procesada, el registro debe contener source, destination, amount y timestamp.
**Valida: Requisitos 2.3**

### Propiedad 8: Atomicidad en fallos
*Para cualquier* transacción que falla en cualquier paso, los balances de ambas wallets deben permanecer sin cambios.
**Valida: Requisitos 2.4, 3.3**

### Propiedad 9: Prevención de balances negativos
*Para cualquier* secuencia de operaciones válidas, ninguna wallet debe terminar con balance negativo.
**Valida: Requisitos 2.5**

### Propiedad 10: Unicidad de identificadores de transacción
*Para cualquier* transacción completada, debe tener un ID único.
**Valida: Requisitos 2.6**

### Propiedad 11: Aislamiento en concurrencia
*Para cualquier* conjunto de transacciones ejecutadas concurrentemente, el resultado final debe ser equivalente a alguna ejecución secuencial de las mismas.
**Valida: Requisitos 3.2**

### Propiedad 12: Durabilidad después de commit
*Para cualquier* transacción confirmada, reiniciar el sistema debe mantener todos los cambios persistidos.
**Valida: Requisitos 3.4**

### Propiedad 13: Consistencia de caché después de transacción
*Para cualquier* transacción que modifica un balance, leer ese balance inmediatamente después debe reflejar el cambio.
**Valida: Requisitos 4.3**

### Propiedad 14: Resiliencia ante fallo de caché
*Para cualquier* operación de lectura, si Redis falla, la operación debe completarse exitosamente consultando PostgreSQL.
**Valida: Requisitos 4.5**

### Propiedad 15: Filtrado correcto de transacciones
*Para cualquier* consulta de transacciones con filtros (wallet, rango de fechas), todos los resultados deben cumplir los criterios especificados.
**Valida: Requisitos 6.4**

### Propiedad 16: Integridad referencial
*Para cualquier* transacción, tanto la wallet de origen como la de destino deben existir en el sistema.
**Valida: Requisitos 6.5**

### Propiedad 17: Mensajes de error descriptivos para fondos insuficientes
*Para cualquier* transacción rechazada por fondos insuficientes, el mensaje de error debe indicar claramente la razón.
**Valida: Requisitos 10.1**

### Propiedad 18: Validación de entrada inválida
*Para cualquier* entrada inválida (montos negativos, IDs malformados), el sistema debe retornar errores de validación específicos.
**Valida: Requisitos 10.2**

### Propiedad 19: Error 404 para wallets inexistentes
*Para cualquier* ID de wallet que no existe, las operaciones deben retornar código HTTP 404.
**Valida: Requisitos 10.3**

### Propiedad 20: Códigos HTTP apropiados
*Para cualquier* operación, los errores de cliente deben retornar códigos 4xx y los errores de servidor códigos 5xx.
**Valida: Requisitos 10.4**

### Propiedad 21: Formato JSON válido
*Para cualquier* request y response, el contenido debe ser JSON válido y bien formado.
**Valida: Requisitos 11.6**

## Manejo de Errores

### Jerarquía de Excepciones

```java
// Excepciones de dominio
public class DomainException extends RuntimeException { }
public class InsufficientFundsException extends DomainException { }
public class WalletNotFoundException extends DomainException { }
public class DuplicateWalletException extends DomainException { }
public class InvalidAmountException extends DomainException { }

// Excepciones de infraestructura
public class InfrastructureException extends RuntimeException { }
public class CacheException extends InfrastructureException { }
public class DatabaseException extends InfrastructureException { }
```

### Estrategias de Manejo

**1. Errores de Validación (4xx)**
- Entrada inválida → 400 Bad Request
- Wallet no encontrada → 404 Not Found
- Wallet duplicada → 409 Conflict
- Fondos insuficientes → 422 Unprocessable Entity

**2. Errores de Sistema (5xx)**
- Fallo de base de datos → 500 Internal Server Error
- Timeout de transacción → 503 Service Unavailable

**3. Fallback de Caché**
```java
public Money getBalance(WalletId walletId) {
    try {
        return cache.getBalance(walletId)
            .orElseGet(() -> {
                Wallet wallet = walletRepository.findById(walletId)
                    .orElseThrow(WalletNotFoundException::new);
                cache.putBalance(walletId, wallet.getBalance());
                return wallet.getBalance();
            });
    } catch (CacheException e) {
        // Fallback directo a base de datos
        logger.warn("Cache failure, falling back to database", e);
        return walletRepository.findById(walletId)
            .map(Wallet::getBalance)
            .orElseThrow(WalletNotFoundException::new);
    }
}
```

**4. Rollback Automático**
- Uso de `@Transactional` en Spring para rollback automático
- Cualquier excepción no capturada causa rollback completo
- Transacciones marcadas como FAILED en el registro

## Estrategia de Testing

### Enfoque Dual de Testing

El sistema utiliza un enfoque complementario que combina:

**Unit Tests**: Verifican ejemplos específicos, casos borde y condiciones de error
- Casos específicos que demuestran comportamiento correcto
- Puntos de integración entre componentes
- Casos borde y condiciones de error

**Property-Based Tests**: Verifican propiedades universales a través de todos los inputs
- Propiedades universales que se mantienen para todos los inputs
- Cobertura comprehensiva de inputs mediante aleatorización

Juntos proporcionan cobertura completa: los unit tests capturan bugs concretos, los property tests verifican corrección general.

### Configuración de Property-Based Testing

**Biblioteca**: jqwik (https://jqwik.net/) para Java
**Configuración**: Mínimo 100 iteraciones por test de propiedad
**Formato de Tag**: `@Tag("Feature: sistema-micro-pagos, Property {número}: {texto}")`

Cada propiedad de corrección debe implementarse como UN SOLO test basado en propiedades.

### Niveles de Testing

**1. Unit Tests (JUnit 5)**
```java
@Test
void shouldCreateWalletWithZeroBalance() {
    Wallet wallet = new Wallet("user123");
    assertEquals(Money.zero(), wallet.getBalance());
}

@Test
void shouldThrowExceptionWhenDebitExceedsBalance() {
    Wallet wallet = new Wallet("user123");
    assertThrows(InsufficientFundsException.class, 
        () -> wallet.debit(Money.of(100)));
}
```

**2. Property-Based Tests (jqwik)**
```java
@Property
@Tag("Feature: sistema-micro-pagos, Property 6: Conservación de suma total")
void transactionPreservesTotalBalance(
    @ForAll @Positive BigDecimal initialBalance,
    @ForAll @Positive @LessThan("initialBalance") BigDecimal amount
) {
    // Arrange
    Wallet source = new Wallet("source");
    source.credit(Money.of(initialBalance));
    Wallet destination = new Wallet("destination");
    
    Money totalBefore = source.getBalance().add(destination.getBalance());
    
    // Act
    source.debit(Money.of(amount));
    destination.credit(Money.of(amount));
    
    // Assert
    Money totalAfter = source.getBalance().add(destination.getBalance());
    assertEquals(totalBefore, totalAfter);
}
```

**3. Integration Tests (Testcontainers)**
```java
@Testcontainers
@SpringBootTest
class TransactionIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);
    
    @Test
    void shouldProcessTransactionEndToEnd() {
        // Test completo con PostgreSQL y Redis reales
    }
}
```

**4. Load Tests (JMeter)**
- Simular 1000 transacciones concurrentes
- Verificar que no hay condiciones de carrera
- Validar que ACID se mantiene bajo carga
- Medir latencia p95 y p99

### Cobertura de Testing

- **Objetivo**: Mínimo 80% de cobertura de código
- **Herramienta**: JaCoCo
- **Enfoque**: Priorizar cobertura de lógica de dominio y casos de uso
- **Property tests**: Cada propiedad de corrección debe tener su test correspondiente

## Deployment y DevOps

### Containerización

**Dockerfile**
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/micropayments-*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**docker-compose.yml**
```yaml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/micropayments
      - SPRING_REDIS_HOST=redis
    depends_on:
      - postgres
      - redis
  
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: micropayments
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: secret
    volumes:
      - postgres_data:/var/lib/postgresql/data
  
  redis:
    image: redis:7-alpine
    volumes:
      - redis_data:/data

volumes:
  postgres_data:
  redis_data:
```

### CI/CD Pipeline (GitHub Actions)

```yaml
name: CI/CD Pipeline

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run tests
        run: ./mvnw clean verify
      - name: Upload coverage
        uses: codecov/codecov-action@v3
  
  sonar:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: SonarQube Scan
        run: ./mvnw sonar:sonar
  
  build:
    needs: [test, sonar]
    runs-on: ubuntu-latest
    steps:
      - name: Build Docker image
        run: docker build -t micropayments:${{ github.sha }} .
      - name: Push to registry
        run: docker push micropayments:${{ github.sha }}
```

### Health Checks

```java
@RestController
@RequestMapping("/actuator/health")
public class HealthController {
    
    @GetMapping
    public ResponseEntity<HealthStatus> health() {
        return ResponseEntity.ok(new HealthStatus("UP"));
    }
    
    @GetMapping("/readiness")
    public ResponseEntity<HealthStatus> readiness() {
        // Verificar conexiones a PostgreSQL y Redis
        boolean dbHealthy = checkDatabase();
        boolean cacheHealthy = checkCache();
        
        if (dbHealthy && cacheHealthy) {
            return ResponseEntity.ok(new HealthStatus("READY"));
        }
        return ResponseEntity.status(503)
            .body(new HealthStatus("NOT_READY"));
    }
}
```

### Configuración por Ambiente

```properties
# application.properties
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USER}
spring.datasource.password=${DATABASE_PASSWORD}
spring.redis.host=${REDIS_HOST}
spring.redis.port=${REDIS_PORT:6379}

# Logging
logging.level.root=INFO
logging.pattern.console=%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n
```

## Consideraciones de Seguridad

- Validación de entrada en todos los endpoints
- Uso de prepared statements para prevenir SQL injection
- Límites de rate limiting en APIs
- Autenticación y autorización (fuera del scope actual, pero recomendado)
- Encriptación de datos sensibles en tránsito (HTTPS)
- Auditoría de todas las transacciones

## Consideraciones de Performance

- Índices en columnas frecuentemente consultadas
- Caché de balances para reducir carga en PostgreSQL
- Connection pooling para base de datos
- Timeouts configurables para operaciones
- Monitoreo de métricas (latencia, throughput, tasa de error)

---

# Extensiones y Ampliaciones del Diseño (v2 — Robusto + Frontend)

## Tecnologías Clave Adicionales (Full Stack)

**Backend (extensiones al stack original):**
- **Autenticación**: Spring Security + JWT (access token) + Refresh Token en HttpOnly Cookie
- **Migraciones**: Flyway para versionado de esquema SQL
- **Scheduler**: Spring Scheduler + ShedLock para jobs distribuidos (billing recurrente, webhook retries, reconciliación)
- **OpenAPI**: springdoc-openapi para autogenerar spec + typescript client
- **Email/Notificaciones**: JavaMailSender / Thymeleaf templates / abstracción NotificationPort

**Frontend (User Dashboard):**
- **Framework**: React 18+ con TypeScript 5+
- **Build**: Vite 5+ (HMR rápido, build optimizado, code-splitting)
- **Routing**: React Router v6 con lazy loading por ruta
- **UI/Componentes**: Tailwind CSS 3.4 + shadcn/ui (accesibles por defecto, personalizables) + Lucide Icons
- **Estado Servidor**: TanStack Query (React Query) v5 + TanStack Table para listados/paginación/filtrado
- **Estado UI**: Zustand (stores pequeños y tipados: auth, theme, uiState, notifications)
- **Formularios**: React Hook Form + Zod para validación tipada (esquemas compartibles con OpenAPI)
- **i18n**: react-i18next (es-CO, en-US, pt-BR)
- **Gráficos**: Recharts (línea/barras para dashboard)
- **QR**: qrcode.react para códigos QR de payment requests
- **Mapa de monto/fecha**: input masking + date-fns (o dayjs) formateo localizado
- **Tests**: Vitest + Testing Library + Playwright (e2e)

**Frontend (Admin Dashboard):**
- Mismo stack base que user dashboard, con componentes adicionales: AG Grid avanzado para tablas de transacciones, react-dropzone para documentos KYC, Recharts avanzados para dashboards financieros
- **Autorización**: CasL o RBAC simple por roles (carga desde API al login)

**DevOps + Full Stack:**
- **Estructura**: Mono-repo (pnpm workspaces o simple layout con Makefile)
- **E2E**: Playwright contra stack dockerizado completo
- **Reverse proxy local**: Nginx o Caddy en docker-compose para simular same-origin en dev (cookies same-site)

---

## Nuevas Entidades del Dominio (ampliación)

### UserProfile y KYC
```java
public class UserProfile {
    private UserId userId;
    private Email email;
    private PhoneNumber phone;
    private FullName fullName;
    private Country country;
    private Instant createdAt;
    private KYCLevel kycLevel;        // UNVERIFIED, BASIC, VERIFIED, ACCREDITED
    private KYCStatus kycStatus;      // NOT_STARTED, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED
    private Set<Role> roles;          // USER, MERCHANT, ADMIN, SUPPORT, COMPLIANCE, FINANCE
    private ReferralCode referralCode;

    public void promoteToTier(KYCLevel newLevel, AuditContext audit) {
        this.kycLevel = newLevel;
        // emite evento KYC_LEVEL_CHANGED
    }
}

public enum KYCLevel {
    UNVERIFIED(0), BASIC(1), VERIFIED(2), ACCREDITED(3);
    private final int tier;
}

public record TierLimits(
    KYCLevel tier,
    Money maxTopUpMonthly,
    Money maxWithdrawMonthly,
    Money maxSingleTransfer,
    Money maxPaymentRequestAmount,
    int maxTxPerHour
) { }
```

### TopUp (Recarga)
```java
public class TopUp {
    private TopUpId id;
    private UserId userId;
    private WalletId walletId;
    private Money amount;
    private ExternalProvider provider; // STRIPE, WOMPI, PLACETOPAY, PSE, NEQUI, DAVIPLATA, PAYPAL, etc.
    private String providerReference;  // checkout_session_id / payment_intent
    private TopUpStatus status;        // PENDING_PAYMENT, SUCCEEDED, FAILED, EXPIRED
    private Instant createdAt;
    private Instant confirmedAt;
}
```

### Withdrawal (Retiro)
```java
public class Withdrawal {
    private WithdrawalId id;
    private UserId userId;
    private WalletId walletId;
    private Money amount;
    private PayoutDestinationId destinationId; // masked bank/card reference
    private ExternalPayoutProvider provider;
    private String providerReference;
    private WithdrawalStatus status; // PENDING, PROCESSING, COMPLETED, FAILED, REVERSED
    private Instant createdAt;
    private Money reservedAmount; // igual que amount; en reversa se libera
}
```

### PaymentRequest (Links/QR Comercio)
```java
public class PaymentRequest {
    private PaymentRequestId id;
    private UserId merchantUserId;
    private Money amount;
    private String description;
    private String orderReference;
    private Instant expiresAt;
    private PaymentRequestStatus status; // DRAFT, OPEN, PAID, EXPIRED, REFUNDED
    private ShortCode shortCode;         // url amigable /pay/:shortCode
    private TransactionId paidWithTxId;
    private String webhookUrl;           // opcional por request o global
}
```

### Plan y Subscription (Recurrencias)
```java
public class Plan {
    private PlanId id;
    private UserId merchantUserId;
    private String name;
    private Money amount;
    private BillingInterval interval;   // DAY, WEEK, MONTH, YEAR
    private int intervalCount;
    private Integer trialDays;
    private PlanStatus status;          // ACTIVE, ARCHIVED
}

public class Subscription {
    private SubscriptionId id;
    private PlanId planId;
    private UserId subscriberUserId;
    private WalletId subscriberWalletId;
    private SubscriptionStatus status;  // ACTIVE, TRIALING, PAST_DUE, CANCELED, PAUSED
    private Instant currentPeriodStart;
    private Instant nextBillingDate;
    private Instant canceledAt;
    private int dunningAttempts;
}
```

### Refund (Reembolso)
```java
public class Refund {
    private RefundId id;
    private TransactionId sourceTransactionId;
    private UserId refundingUserId;     // merchant o admin
    private Money refundedAmount;
    private RefundType type;            // FULL, PARTIAL
    private String reason;
    private TransactionId refundTxId;  // tx de reverse transfer
    private Instant createdAt;
}
```

### Fee / FeeLine (Comisiones)
```java
public record FeeLine(
    BigDecimal amount,
    Currency currency,
    FeeType type,       // FLAT, PERCENTAGE, FX_SPREAD
    String ruleVersion, // referencia a la versión de la regla aplicada
    String description
) { }
```

### Referral / Notification
```java
public class Referral {
    private ReferralCode code;
    private UserId referrerId;
    private UserId inviteeId;
    private ReferralStatus status;    // PENDING, QUALIFIED, BONUS_PAID, EXPIRED
    private Money referrerBonus;
    private Money inviteeBonus;
    private TransactionId bonusTxId;
}

public class UserNotification {
    private NotificationId id;
    private UserId userId;
    private String eventType;
    private Map<String, Object> payload; // non-sensitive subset
    private NotificationChannel channel; // IN_APP, EMAIL, SMS, PUSH
    private NotificationStatus status;   // QUEUED, SENT, FAILED, READ
    private CorrelationId correlationId;
}
```

---

## Nuevos Value Objects (Domain Layer)

- `UserId`, `TopUpId`, `WithdrawalId`, `PaymentRequestId`, `ShortCode`, `PlanId`, `SubscriptionId`, `RefundId`, `NotificationId`, `ReferralCode` (UUID o ULID + factory)
- `Email`, `PhoneNumber`, `FullName`, `Country` (ISO 3166-1 alpha-2)
- `Currency` (ISO 4217, 3 letras, validadas contra lista soportada)
- `FxRatePair` (base, quote, bid, ask, timestamp, source)
- `ExternalProvider` enum: STRIPE, WOMPI, PLACETOPAY, PSE, BANCOLOMBIA_TRANSFER, NEQUI, DAVIPLATA, PAYPAL, BANK_TRANSFER_ACH
- `Role`: USER, MERCHANT, SUPPORT, COMPLIANCE, FINANCE, SUPER_ADMIN
- `KYCVerificationStatus`: NOT_STARTED, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED
- `RiskDecision`: ALLOW, CHALLENGE (step-up), BLOCK

---

## Nuevos Puertos (Interfaces) Expandidos

```java
// ——— Servicios Externos (Ports Salientes Primarios de Infra) ———
public interface PaymentGatewayPort {
    CreateCheckoutResult createCheckout(TopUp topUp, String successUrl, String cancelUrl);
    Optional<WebhookEvent> parseAndVerifyWebhook(String rawBody, Map<String,String> headers); // HMAC verify
}

public interface PayoutProviderPort {
    InitiatePayoutResult initiatePayout(Withdrawal withdrawal, PayoutDestination destination);
    PayoutStatus checkPayoutStatus(String providerReference);
}

public interface KycProviderPort {
    InitiateVerificationResult initiate(UserProfile user);
    DocumentUploadResult uploadDocument(UserId userId, DocumentType type, byte[] content);
    Optional<KycWebhookEvent> parseStatusWebhook(String rawBody, Map<String,String> headers);
}

public interface NotificationPort {
    void sendEmail(EmailMessage msg);
    void enqueueInApp(UserNotification notification);
    // SMS/PUSH opcionales vía adaptador
}

public interface WebhookDeliveryPort {
    void deliver(String url, WebhookPayload payload, String signingSecret);
    void retryFailed();
    List<DeliveryAttempt> historyForEvent(String eventId);
}

// ——— Motores / Lógica de Negocio ———
public interface RiskEnginePort {
    RiskEvaluation evaluate(RiskContext context); // ALLOW / CHALLENGE / BLOCK + reasons
}

public interface FxRatePort {
    FxQuote getRate(Currency base, Currency quote, Instant at);
    Money convert(Money amount, Currency target);
}

public interface FeeEnginePort {
    List<FeeLine> calculateFees(FeeContext context);
}

public interface TierLimitsPort {
    TierLimits getLimitsFor(KYCLevel tier, Country country);
}

// ——— Persistencia (Ports Salientes a DB) ———
public interface UserProfileRepository extends Repository<UserProfile, UserId> {
    Optional<UserProfile> findByEmail(Email email);
    Optional<UserProfile> findByReferralCode(ReferralCode code);
}

public interface TopUpRepository {
    TopUp save(TopUp topUp);
    Optional<TopUp> findByProviderReference(String ref);
}

public interface WithdrawalRepository {
    Withdrawal save(Withdrawal w);
    Page<Withdrawal> findByStatusOrderByCreatedAt(WithdrawalStatus s, Pageable p);
}

public interface PaymentRequestRepository {
    Optional<PaymentRequest> findByShortCode(ShortCode code);
}

public interface PlanRepository { /* CRUD + findByMerchant */ }
public interface SubscriptionRepository { /* findDueForBilling(Instant now) */ }

public interface RefundRepository {
    Money sumRefundedFor(TransactionId sourceTxId);
}

public interface ReferralRepository { /* findByCode + qualify */ }

public interface NotificationRepository extends Repository<UserNotification, NotificationId> {
    Page<UserNotification> findByUserIdOrderByCreatedAtDesc(UserId u, Pageable p);
    long countUnread(UserId u);
}
```

---

## Nuevos Casos de Uso (Application Layer)

| Caso de Uso | Responsabilidad |
|---|---|
| `SignupUserUseCase` | crear `UserProfile` (UNVERIFIED), generar `ReferralCode`, emitir verificación email |
| `VerifyEmailUseCase` / `VerifyPhoneUseCase` | marcar canales verificados, promover a BASIC si ambos cumplen |
| `InitiateKycUseCase` | crear sesión KYC via `KycProviderPort` |
| `HandleKycStatusWebhookUseCase` | actualizar `KYCStatus`/`KYCLevel`, auditoría, notificación |
| `CreateTopUpUseCase` | crear `TopUp` + `PaymentGatewayPort#createCheckout` → devolver URL checkout |
| `HandleTopUpWebhookUseCase` | HMAC verify, idempotency, credit wallet, crear `Transaction` tipo TOP_UP, invalidar cache |
| `RequestWithdrawalUseCase` | validar tier ≥ VERIFIED + límites, `reserve` balance, crear WITHDRAWAL transacción, iniciar payout |
| `HandlePayoutUpdateUseCase` | polling/webhook: COMPLETED → cerrar; FAILED → reverse (unreserve) |
| `CreatePaymentRequestUseCase` (merchant) | crear request, short code único, generar payload QR |
| `PayPaymentRequestUseCase` (customer) | validar request abierta + límite, procesar P2P, marcar PAID atomico, webhook merchant |
| `CreatePlanUseCase` / `EditPlanUseCase` (merchant) | CRUD Plan |
| `SubscribeToPlanUseCase` (subscriber) | aceptar plan, crear Subscription ACTIVE/TRIALING |
| `RunSubscriptionBillingJobUseCase` (scheduler) | buscar subscripciones con nextBillingDate ≤ now; procesar cobro, retry/dunning, avanzar fecha |
| `RetryFailedSubscriptionBillingUseCase` | scheduler dunning |
| `CreateRefundUseCase` | validar elegibilidad, FeeEngine, reverse transfer, link bidireccional |
| `SendNotificationUseCase` | orquesta NotificationPort + guarda log append-only |
| `DeliverWebhookUseCase` (merchant) | firma HMAC, envio, retry queue, persist delivery log |
| `RunRiskCheckUseCase` | wrapper de RiskEngine; CHALLENGE → step-up; BLOCK → audit alert |
| `ExportReportUseCase` (admin/finance) | crea job async CSV, upload a storage, envía email con link |
| `RunReconciliationJobUseCase` (finance) | compara internal tx vs provider report, produce mismatches list |
| `AdminAdjustWalletUseCase` | crédito/débito manual con reason obligatorio → ADJUSTMENT tx + audit log |
| `AdminFreezeWalletUseCase` | compliance: bloquea débitos, auditoría, notificación |
| `ApplyFeesAndCommissionsUseCase` | helper llamado dentro de las tx: FeeEngine + entradas ledger de fees |
| `PayReferralBonusUseCase` | al cumplirse qualifying event → REFERRAL_BONUS tx idempotente |

---

## Modelo de Datos Ampliado (Nuevas Tablas PostgreSQL)

### Tabla: users (autenticación)
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    email_verified_at TIMESTAMP NULL,
    phone VARCHAR(50) NULL,
    phone_verified_at TIMESTAMP NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, DISABLED, FROZEN
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL,
    last_login_country VARCHAR(2) NULL,
    roles TEXT[] NOT NULL DEFAULT ARRAY['USER']::TEXT[],
    mfa_secret VARCHAR(255) NULL,
    mfa_enabled_at TIMESTAMP NULL
);
CREATE INDEX idx_users_email ON users(email);
```

### Tabla: user_profiles
```sql
CREATE TABLE user_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    full_name VARCHAR(255) NULL,
    country VARCHAR(2) NULL,
    kyc_level VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    kyc_provider_ref VARCHAR(255) NULL,
    referral_code VARCHAR(16) NOT NULL UNIQUE,
    referrer_id UUID NULL REFERENCES users(id),
    timezone VARCHAR(50) NULL,
    preferred_language VARCHAR(5) NOT NULL DEFAULT 'es-CO',
    theme VARCHAR(10) NOT NULL DEFAULT 'SYSTEM', -- LIGHT/DARK/SYSTEM
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Tabla: payout_destinations
```sql
CREATE TABLE payout_destinations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL, -- BANK_ACCOUNT, CARD
    masked_label VARCHAR(255) NOT NULL, -- "****-4242", "BBVA ***1234"
    provider_ref VARCHAR(255) NOT NULL, -- ID en el proveedor de payouts
    verified_at TIMESTAMP NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Tabla: top_ups
```sql
CREATE TABLE top_ups (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_ref VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    raw_provider_payload JSONB NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP NULL
);
CREATE INDEX idx_topups_status ON top_ups(status);
```

### Tabla: withdrawals
```sql
CREATE TABLE withdrawals (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    destination_id UUID NOT NULL REFERENCES payout_destinations(id),
    provider VARCHAR(50) NOT NULL,
    provider_ref VARCHAR(255) NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    failed_reason TEXT NULL
);
```

### Tabla: payment_requests
```sql
CREATE TABLE payment_requests (
    id UUID PRIMARY KEY,
    merchant_user_id UUID NOT NULL REFERENCES users(id),
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description TEXT NULL,
    order_ref VARCHAR(255) NULL,
    short_code VARCHAR(16) NOT NULL UNIQUE,
    merchant_wallet_id UUID NOT NULL REFERENCES wallets(id),
    paid_with_tx_id UUID NULL REFERENCES transactions(id),
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    webhook_url VARCHAR(1024) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_payreq_shortcode ON payment_requests(short_code);
CREATE INDEX idx_payreq_merchant ON payment_requests(merchant_user_id);
```

### Tabla: plans y subscriptions
```sql
CREATE TABLE plans (
    id UUID PRIMARY KEY,
    merchant_user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    interval VARCHAR(10) NOT NULL, -- DAY/WEEK/MONTH/YEAR
    interval_count INT NOT NULL DEFAULT 1,
    trial_days INT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES plans(id),
    subscriber_id UUID NOT NULL REFERENCES users(id),
    subscriber_wallet_id UUID NOT NULL REFERENCES wallets(id),
    status VARCHAR(20) NOT NULL,
    current_period_start TIMESTAMP NOT NULL,
    next_billing_date TIMESTAMP NOT NULL,
    canceled_at TIMESTAMP NULL,
    dunning_attempts INT NOT NULL DEFAULT 0,
    last_billing_result TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_subs_next_billing ON subscriptions(next_billing_date, status);
```

### Tabla: refunds
```sql
CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    source_tx_id UUID NOT NULL REFERENCES transactions(id),
    refunding_user_id UUID NOT NULL REFERENCES users(id),
    refunded_amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    type VARCHAR(10) NOT NULL, -- FULL, PARTIAL
    reason TEXT NULL,
    refund_tx_id UUID NOT NULL REFERENCES transactions(id) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_refunds_source ON refunds(source_tx_id);
```

### Tabla: fee_ledger (para conciliación financiera)
```sql
CREATE TABLE fee_ledger (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    fee_type VARCHAR(20) NOT NULL, -- FLAT, PERCENTAGE, FX_SPREAD
    rule_version VARCHAR(50) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_fees_tx ON fee_ledger(transaction_id);
```

### Tabla: notifications y notification_log
```sql
CREATE TABLE user_notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    channel VARCHAR(20) NOT NULL, -- IN_APP, EMAIL, SMS, PUSH
    status VARCHAR(20) NOT NULL,   -- QUEUED, SENT, FAILED, READ
    correlation_id UUID NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP NULL
);
CREATE INDEX idx_notifs_user_time ON user_notifications(user_id, created_at DESC);
```

### Tabla: webhook_subscriptions y deliveries (para merchants)
```sql
CREATE TABLE merchant_webhook_configs (
    id UUID PRIMARY KEY,
    merchant_user_id UUID NOT NULL REFERENCES users(id) UNIQUE,
    endpoint_url VARCHAR(1024) NOT NULL,
    signing_secret VARCHAR(255) NOT NULL,
    subscribed_events TEXT[] NOT NULL DEFAULT ARRAY['*']::TEXT[],
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    merchant_user_id UUID NOT NULL REFERENCES users(id),
    attempt_number INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL, -- QUEUED, SUCCEEDED, FAILED, RETRYABLE_FAIL
    http_status INT NULL,
    request_body JSONB NULL,
    response_body TEXT NULL,
    next_retry_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP NULL
);
CREATE INDEX idx_webhook_next_retry ON webhook_deliveries(next_retry_at, status);
```

### Tabla: referrals
```sql
CREATE TABLE referrals (
    code VARCHAR(16) PRIMARY KEY,
    referrer_id UUID NOT NULL REFERENCES users(id),
    invitee_id UUID NULL REFERENCES users(id) UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    referrer_bonus DECIMAL(19,4) NULL,
    invitee_bonus DECIMAL(19,4) NULL,
    bonus_tx_id UUID NULL REFERENCES transactions(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    qualified_at TIMESTAMP NULL
);
```

### Tabla: audit_events (mejorada)
```sql
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID NULL REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NULL,
    correlation_id UUID NULL,
    metadata JSONB NULL,
    ip_address INET NULL,
    user_agent TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- append-only: no UPDATE ni DELETE permitidos en capa de repositorio
```

---

## Nuevas Estructuras de Caché Redis (ampliación)

```
Patterns adicionales:
- user:profile:{userId}           → JSON (UserProfile cacheado, TTL 10min)
- user:kyclimits:{tier}:{country} → JSON de TierLimits (TTL 1h, cachear versión por país)
- fxrate:{base}:{quote}           → JSON bid/ask + timestamp (TTL 2 min)
- notif:unread:{userId}           → count de unread (TTL corto, write-through al insertar notif)
- idempotency:{key}               → JSON { requestHash, response } TTL configurable (24h)
- risk:velocity:{userId}:{granularity} → contadores velocity con TTL granular (min/hr/day)
- webhook:retry:queue             → ZSET sorted by next_retry_at (para scheduler)
```

---

## Propiedades de Corrección Adicionales (22 - 35)

*Continuación de la lista original de propiedades, ahora incluye los nuevos dominios.*

### Propiedad 22: Idempotencia de creación de transacción (webhook top-up)
*Para cualquier* webhook repetido de provider con mismo `provider_ref`, acreditar la wallet UNA sola vez.
**Valida: Requisitos 19.4, 12.2**

### Propiedad 23: No doble gasto bajo concurrencia (repetido como invariant cross-cutting)
*Bajo N transacciones concurrentes sobre una misma wallet con balance B, la suma total debitada NUNCA excede B.*
**Valida: Requisitos 13.1, 20.2**

### Propiedad 24: Estado invariante de reservas en retiros fallidos
*Para cualquier* withdrawal en estado FAILED o REVERSED, el monto reservado debe haber sido devuelto íntegramente a la wallet.
**Valida: Requisitos 20.4**

### Propiedad 25: No doble pago en PaymentRequest
*Para cualquier* `PaymentRequest` con status PAID, existe EXACTAMENTE una transacción `paidWithTxId` asociada.
**Valida: Requisitos 21.5**

### Propiedad 26: Límite en reembolsos acumulados
*Para cualquier* transacción origen, `sum(refunds.refundedAmount) ≤ originalTx.amount`.
**Valida: Requisitos 23.4**

### Propiedad 27: Fecha de próximo cobro siempre en el futuro luego de billing exitoso
*Después de cobrar exitosamente una subscription, `nextBillingDate` se incrementa por `interval` y queda estrictamente mayor que el instante de cobro.*
**Valida: Requisitos 22.4**

### Propiedad 28: Reglas de límites por tier son aplicadas en operaciones sensibles
*Cualquier* top-up, transfer, withdrawal, payment-request que exceda el límite del user tier debe ser rechazado antes de tocar balances.
**Valida: Requisitos 24.3, 19.6, 20.6**

### Propiedad 29: Invariante de conservación con fees
*Para cualquier* operación, suma (cambios de balance de users + fees) = 0 al cierre de la transacción (fees no "desaparecen").
**Valida: Requisitos 30.2**

### Propiedad 30: Idempotencia en bono de referral
*Para cualquier* referrer+invitee+rule, se emite UNO Y SOLO UN `REFERRAL_BONUS` transaction.
**Valida: Requisitos 31.3**

### Propiedad 31: No pérdida de eventos de auditoría
*Todo* cambio de estado de wallet / transaction / refund / freeze-action tiene al menos un registro en `audit_events` append-only.
**Valida: Requisitos 15.1, 26.4, 26.6**

### Propiedad 32: Firmas HMAC en webhooks salientes
*Todo* webhook entregado a merchant lleva firma y timestamp verificables; el handler de test endpoint debe confirmar que sin firma válida el sistema rechaza (o que delivery endpoint cliente rechaza).
**Valida: Requisitos 25.4**

### Propiedad 33: Consistencia multi-moneda FX
*Dado un* cross-currency transfer de X unidades de A a Y de B, el convertidor usa el rate tal que el amount + fee_spread suma cerrada y la suma de balances en cada moneda es preservada (con fees tratados como salida).
**Valida: Requisitos 29.3, 29.5**

### Propiedad 34: Autorización wallet → user dueño
*Todo* débito de wallet es invocado EXCLUSIVAMENTE por el userId dueño, o bien por un ADMIN/SUPPORT (con razón de ajuste). Ninguna llamada no-autorizada supera la capa de security interceptor.
**Valida: Requisitos 18.1, 26.3, 38.1**

### Propiedad 35: Contrato de errores coherente en frontend
*Toda* respuesta 4xx/5xx del backend cumple RFC 7807, lo cual se refleja en el handler de errores del SPA para renderizar toasts/alertas consistentes.
**Valida: Requisitos 17.2, 35.5**

---

## Estrategias Operativas y de Seguridad Ampliadas

### Scheduler / Jobs Distribuidos
- Spring Scheduler + ShedLock (bloqueo en DB) para evitar que múltiples pods ejecuten el mismo job
- **Cron frecuencias**:
  - Subscription billing job: cada hora (busca next_billing_date ≤ now + 1h)
  - Dunning retries: cada 6h
  - Webhook retries: cada 5 min (exponential backoff: 1m, 5m, 15m, 1h, 6h, 24h)
  - Reconciliation job: diario 02:00 AM
  - Limpieza idempotency keys expiradas: semanal

### Anti-Fraude Default Rules (RiskEngine adaptador reglas-config)
```yaml
rules:
  velocity_per_hour:
    USER: 100 tx/hr
    BASIC: 500 tx/hr
  first_topup_max: 100 USD
  cross_border_high_risk: [CUBA, IRAN, NORTE_COREA, RUSIA] -> BLOCK
  unusual_amount_vs_history: si monto > 5x promedio ultimos 30 dias -> CHALLENGE 2FA
  new_device_login: CHALLENGE email_code
  velocity_topups_per_day:
    UNVERIFIED: 2 topups/day max
```

### Autenticación + Authz Flujo
1. Usuario envía credenciales → backend valida password_hash (bcrypt/argon2)
2. Si 2FA activo, retorna 401 con `requires_mfa=true` + challenge token
3. POST /auth/mfa/verify con TOTP → success emite:
   - `access_token` JWT (15min, en memoria cliente en axios interceptor)
   - `refresh_token` cookie HttpOnly/Secure/SameSite=Lax (30d, rotativa)
4. Cualquier request con access_token expirado: cliente intercepta 401 → POST /auth/refresh (usa cookie) → nuevos tokens; si refresh falla → logout

### Autorización (RBAC + Atributos)
- `@PreAuthorize("@walletSecurity.isOwner(#walletId, authentication)")` en endpoints de débito
- `@PreAuthorize("hasAnyRole('FINANCE','SUPER_ADMIN')")` en reports/exports
- Backend es SIEMPRE la fuente de verdad; frontend solo oculta menú/botones como UX

---

## Arquitectura Frontend — Stack y Diseño Estructural

### Patrón Arquitectónico SPA
```
┌──────────────────────────────────────────────────────────────┐
│  Capa de Presentación (Vistas / Páginas)                     │
│  └── Pages: Login, Signup, DashboardHome, SendMoney, TopUp,  │
│      Withdraw, Activity, PaymentRequests, Plans, Subscribers,│
│      Profile, KYC, Notifications, Settings                   │
└───────────────────────────┬──────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│  Capa de Componentes (UI/UX + Layout)                        │
│  shadcn/ui atoms + molecules (Card, Input, Button, Toast,    │
│  Alert, Modal, Drawer, DataTable, Skeleton, QRDisplay, etc.) │
└───────────────────────────┬──────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│  Hooks / Data Layer (TanStack Query + Cliente OpenAPI)       │
│  useWalletBalance, useTransactions, useCreateTopUp,          │
│  usePayRequest, useNotifications, etc.                       │
└───────────────────────────┬──────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│  Stores (Zustand): AuthStore, ThemeStore, UINotifs, CorrelId │
└──────────────────────────────────────────────────────────────┘
```

### Routing (User SPA)
```
/auth/*               → layout auth (sin barra lateral)
  /login, /signup, /verify-email, /mfa
/                     → dashboard layout (sidebar + topbar + content)
  /dashboard          home con balance, quick actions, resumen 7d
  /wallet             detalle wallet, balance por moneda (si multi)
  /send               P2P transfer
  /request            Payment request customer-side (ver QR others)
  /top-up             recarga (proveedores grid)
  /withdraw           retiro (elegir destino, KYC upsell si no)
  /activity           listado transacciones (filtros avanzados)
  /activity/:id       detalle transacción (receipt)
  /pay/:shortCode     ruta pública QR PaymentRequest checkout
  /merchant/*         (solo role MERCHANT visible)
    /dashboard        ventas hoy/semana/mes + gráficos
    /requests         CRUD PaymentRequest + crear nuevo + QR live
    /plans            Plans CRUD
    /subscriptions    lista subscribers + estados + retry billing
    /webhooks         configuración + delivery history + test ping
  /profile
    /personal         datos personales
    /kyc              flujo KYC estado/documentos
    /destinations     payout destinations (bank/card masked list)
    /security         cambiar pass, 2FA, devices
    /referrals        código + compartir + referidos listado
  /settings
    /appearance       tema (claro/oscuro/sistema) + densidad
    /language         idioma selector
    /notifications    canal preferencias (email/push toggles)
  /admin/*            (condicional si es ADMIN - o separado)
```

### Routing (Admin SPA o sección protegida)
```
/admin/login          login con 2FA required siempre
/admin/dashboard      métricas plataforma (GMV, users, fees)
/admin/users          búsqueda + listado users con filtros
/admin/users/:id      detalle usuario completo (KYC, wallets, tx, ajustes, freeze)
/admin/transactions   búsqueda transacciones global
/admin/wallets/:id    detalle wallet + ajuste manual dialog
/admin/kyc-queue      verificar documentos (aprobar/rechazar con razón)
/admin/reports        generador + cola exports
/admin/audit-log      browser audit_events con filtros
/admin/roles          gestión de roles (SUPER_ADMIN only)
/admin/config         fees/límites/risk-rules (configuración no-hardcode)
```

---

## Diseño Visual — Sistema de Diseño (Design System)

### Paleta de Colores Moderna (FinTech, amigable + premium)
**Brand primario (confianza + financiero):** Indigo profundo 700/800
- `--color-primary-50`:  #EEF2FF   `--color-primary-500`:  #6366F1
- `--color-primary-600`: #4F46E5   `--color-primary-700`:  #4338CA

**Acento positivo (éxito, saldo positivo):** Emerald
- `--color-success-500`: #10B981   `--color-success-600`: #059669

**Acento peligro (error, débito alto, rechazo):** Rojo suave (no muy fuerte, accesible)
- `--color-danger-500`:  #EF4444   `--color-danger-600`:  #DC2626

**Acento atención (pendiente, warning):** Ámbar
- `--color-warning-500`: #F59E0B

**Info (neutros + enlaces):** Azul sky
- `--color-info-500`:    #0EA5E9

**Superficies (modo claro):**
- bg: fondo base `#FAFAFA`, superficie tarjetas `#FFFFFF`, elevación con sombras suaves shadow-sm/shadow-md
- borde: slate-200, texto primary slate-900, secondary slate-600, muted slate-400

**Superficies (modo oscuro):**
- bg: `#0B1020`, superficie tarjetas `#111827` + `#1F2937`
- borde: slate-700/800, texto primary slate-50, secondary slate-400

**Accesibilidad extra:**
- icon+texto en todos los status badges (no solo color)
- gráficos con texturas/hatchmark opcionales en modo daltonismo (toggle settings)

### Tipografía
- Fuente principal: Inter (sans, números tabulares en balance `font-variant-numeric: tabular-nums;`)
- Fuente mono para IDs/transacciones: JetBrains Mono
- Escala:
  - Headings: 36/30/24/20 px → 2xl/xl/lg/md
  - Body: 16/14/12 px → base/sm/xs
  - Balance principal: 48px + peso 700

### Espaciado / Grid
- 8px baseline grid
- Layout Dashboard: `max-w-7xl` en desktop, 12-col grid
  - Sidebar 260px en ≥ lg, oculta en móvil (drawer)
  - Topbar sticky con usuario, notificaciones, tema

### Principios UX Generales
1. **Una acción por vista** en flujos críticos (enviar, recargar, retirar) — no abrumar
2. **Balance siempre visible** en esquina sup-izq; en movil ocultable por privacidad (ojo tap)
3. **Confirmaciones explicitas**: en transferencias > $X pedir confirmación con resumen + nombre beneficiario destacado
4. **Skeletons > Spinners**: durante carga listados
5. **Toast global stack** (ésquinas sup-dcha) — 1 max por evento similar, se apilan
6. **Historial transaccional navegable**: click en cualquier id abre drawer con receipt completo, copy button, link share interno
7. **Upsells claros pero intrusivos cero**: si no tienes KYC → en "Retirar" explicativo "Necesitamos verificar tu cuenta primero → [Ir a KYC]" con lista de beneficios (límites más altos)

---

## Wireframes de Interfaz (Representaciones ASCII Detalladas)

### Wireframe 1: Home del Dashboard (User, Vista escritorio)
```
┌──────────────────────────────────────────────────────────────────────────────┐
│  🔔 (3)   👁️ Ocultar saldo        🌐 ES ▾  ☀︎/🌙  👤 Juan Pérez ▾ [Cerrar]   │ ← Topbar
├──────────┬───────────────────────────────────────────────────────────────────┤
│ Sidebar  │                                                                   │
│          │  ┌─ Saldo Principal ─────────────────────────────────────────┐   │
│ 📊 Home  │  │   Total Disponible                                        │   │
│ 💳 Bille │  │   $ 2.450.750  COP                          [Ver detalle →]│   │
│ 📤 Enviar│  └───────────────────────────────────────────────────────────┘   │
│ 📥 Recib │                                                                   │
│ ➕ Recarg│  Quick Actions (botones grandes, ícono + label)                   │
│ ➖ Retira│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐             │
│ 📋 Histo│  │ 📤 Enviar │ │ 📥 Recibir│ │ ➕ Recargar││ ➖ Retirar │             │
│ 🛒 Pagar │  └──────────┘ └──────────┘ └──────────┘ └──────────┘             │
│ 🎟️ Refer│                                                                   │
│ 👤 Perfi│  ┌─ Últimos Movimientos (6 filas) ───────────── [Ver todos →] ┐   │
│          │  │ ↗️ +$500.00  Recarga    Ayer 15:22      COMPLETED  stripe │   │
│          │  │ ↘️ -$150.00  Taqueria   Hoy 09:14       COMPLETED  order:48│   │
│          │  │ ↗️ +$200.00  María G.   Hoy 08:02       COMPLETED         │   │
│          │  │ ⏳ -$45.50   Netflix    Ayer 22:00      PROCESSING        │   │
│          │  └───────────────────────────────────────────────────────────┘   │
│          │                                                                   │
│          │  ┌─ Resumen 7 días (grafico barras tiny) ┐ ┌─ Acceso rápido KYC ┐ │
│          │  │ ██░░░░ 65%                             │ │ 🆔 KYC Nivel 2      │ │
│          │  │ $1,250 entrada | $780 salida            │ │ ✅ Aprobado         │ │
│          │  └─────────────────────────────────────────┘ └────────────────────┘ │
└──────────┴───────────────────────────────────────────────────────────────────┘
```

### Wireframe 2: Crear Payment Request (Merchant)
```
┌─────────────────────────────────────────────────────────────────────┐
│  ← Volver | Crear enlace de pago                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─ Datos del pago ─────────────────────────┐ ┌─ Previsualización ─┐│
│  │ Monto                                      │ │ ┌───────────────┐ ││
│  │ ┌─────────────────────────┐ [COP ▾]       │ │ │  Panadería El │ ││
│  │ │ 150000.00               │                │ │ │  Progreso     │ ││
│  │ └─────────────────────────┘                │ │ │               │ ││
│  │ Descripción                                │ │ │  $150.000 COP │ ││
│  │ ┌─────────────────────────────────┐        │ │ │               │ ││
│  │ │ Orden #482 - 3 tacos + refresco │        │ │ │   ■■■ QR ■■■  │ ││
│  │ └─────────────────────────────────┘        │ │ │               │ ││
│  │ Referencia pedido (opcional)               │ │ │   /pay/AbX99K │ ││
│  │ ┌─────────────────────────────────┐        │ │ └───────────────┘ ││
│  │ │ ORD-482                         │        │ │                    ││
│  │ └─────────────────────────────────┘        │ │  [Copiar enlace]   ││
│  │ Expiración:                                 │ │  [Descargar PNG]  ││
│  │  ( ) 1 hora  (●) 24 horas   ( ) 7 días     │ │  [Compartir]       ││
│  │                                             │ └────────────────────┘│
│  │ Tipo:                                       │                        │
│  │  (●) Un solo uso    ( ) Reutilizable       │                        │
│  │                                             │                        │
│  │        [ Cancelar ]    [ Crear enlace ]     │                        │
│  └─────────────────────────────────────────────┘                        │
└─────────────────────────────────────────────────────────────────────┘
```

### Wireframe 3: Flujo Enviar Dinero (P2P) — Stepper 3 pasos
```
Paso 1/3 · Destinatario      Paso 2/3 · Monto           Paso 3/3 · Confirmar
┌───────────────────┐    ┌──────────────────┐    ┌────────────────────────────┐
│  Buscar contacto   │    │  Cantidad a      │    │  ✅ Revisa los detalles   │
│ ┌─────────────────┐│    │  enviar          │    │                            │
│ │ correo / QR / ID ││    │ ┌──────────────┐ │    │  Para:  María González    │
│ └─────────────────┘│    │ │ $    200.00   │ │    │  ⋯⋯⋯⋯⋯@mail.com          │
│                    │    │ │        [COP ▾]│ │    │                            │
│ Contactos recientes│    │ └──────────────┘ │    │  Monto: $200.000 COP      │
│ 👤 María G. ↗️      │    │ Nota (opcional): │    │  Comisión: $2.500 COP     │
│ 👤 Taquería El Sol │    │ ┌──────────────┐ │    │  ─────────────────────    │
│ 👤 Carlos Ruiz     │    │ │ Cena del vier │ │    │  Total a debitar: $202.5 ││
│                    │    │ └──────────────┘ │    │                            │
│ [Siguiente →]      │    │  [← Atrás] [Sig] │    │  [← Atrás]  [✅ Confirmar]│
└───────────────────┘    └──────────────────┘    └────────────────────────────┘
```

### Wireframe 4: Admin - Detalle Usuario + Acciones
```
┌───────────────────────────────────────────────────────────────────────┐
│  ← Usuarios  |  Detalle Usuario  ·  Juan Pérez  ·  [UUID copiar]      │
├───────────────────────────────────────────────────────────────────────┤
│  Datos generales        │ Wallet principal          │  KYC            │
│  ┌────────────────┐     │  ┌──────────────────────┐ │  ┌────────────┐ │
│  │ 🆔 Tier: VERIF │     │  │ Balance: $12,485.20   │ │  │ 📄 Docs ✅ │ │
│  │ 📧 juan@mail   │     │  │ Creada:  2026-02-03   │ │  │ ✅ Aprob.   │ │
│  │ 🇲🇽 MX · +52…22 │     │  │ Tx 30d: $4,210 ent / │ │  │ Nivel: 2    │ │
│  │ 👥 Roles: U,M  │     │  │          $1,800 sal   │ │  │ Por: oper… │ │
│  │ 🗓️ Alta 01/26  │     │  │ [Ver movimientos →]   │ │  │ [Docs ↓]   │ │
│  └────────────────┘     │  └──────────────────────┘ │  └────────────┘ │
│                         │                           │                 │
│  Acciones rápidas (con confirmación y razón):                          │
│  [💰 Ajustar wallet + dialog]    [🧊 Congelar wallet]     [🔓 Desbloq]│
│  [📧 Enviar notif push]         [📋 Ver historial ajustes]            │
│                                                                       │
│  ┌─ Transacciones recientes del usuario (filtros + export) ─────────┐ │
│  │ Fecha       Tipo        Monto       Contraparte      Estado       │ │
│  │ 04 Ago      TOP_UP      +$500.00    Stripe           ✅ COMPLETED │ │
│  │ 03 Ago      TRANSFER    -$350.00    Taqueria El Sol  ✅ COMPLETED │ │
│  │ 02 Ago      REFUND      +$45.00     Netflix (admin)  ✅ COMPLETED │ │
│  └───────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────┘
```

### Wireframe 5: Checkout Público PaymentRequest (ruta /pay/:shortCode)
```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  ┌──────── Logo / Nombre comercio (Taquería El Sol) ─────────┐ │
│  │ 📱 Escanea QR desde tu app o paga con tu wallet ↓         │ │
│  └───────────────────────────────────────────────────────────┘ │
│                                                                 │
│  ┌──────────────── Resumen del pago ────────────────────────┐  │
│  │                                                           │  │
│  │  Monto a pagar:                           $150.000 COP  │  │
│  │  Concepto:     Orden #482 - 3 tacos + refresco            │  │
│  │  Comercio:     Taquería El Sol, Suc. Centro               │  │
│  │  Expira en:    23h 15m                                    │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────── Tienes cuenta? Inicia sesión ────────────────┐  │
│  │                                                           │  │
│  │  Correo:        [________________________]              │  │
│  │  Contraseña:    [________________________]              │  │
│  │  [ ] Recordar este equipo                                │  │
│  │                                                           │  │
│  │         [ Crear cuenta ]   [ Iniciar sesión → ]         │  │
│  │                                                           │  │
│  │  O paga sin cuenta (solo tarjeta vía Stripe):            │  │
│  │  [ 💳 Pagar con tarjeta (Stripe Checkout) ]              │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  🔒 Conexión segura · Pagos procesados con encriptación        │
└─────────────────────────────────────────────────────────────────┘
```

---

## UX Flows Principales (Diagramas de Flujo simplificados)

### Flow A — Recargar Wallet (Top-Up)
```
Usuario (Dashboard) → [➕ Recargar]
  → Pantalla elegir monto + proveedor (Stripe / Wompi / PlaceToPay / PSE / Bancolombia / Nequi / Daviplata)
    → Click [Continuar] → POST /api/v1/top-ups (body + idempotency-key)
      → Backend: CreateTopUpUseCase crea TopUp PENDING + CheckoutSession en provider
        ← Respuesta: redirectUrl = checkoutUrl
    → Frontend: window.location = checkoutUrl
      → Usuario paga en proveedor
        → Webhook provider → HandleTopUpWebhookUseCase
          → HMAC verify ✅ + idempotency check ✅
            → Wallet crédito + Transaction TOP_UP + invalidate cache + Notification IN_APP + EMAIL
              → Frontend (al volver a dashboard): poll hasta status SUCCEEDED o recibir SSE/websocket
                → Toast verde: "✅ Recarga por $X confirmada"
```

### Flow B — Enviar P2P
```
[📤 Enviar] → Paso1 dest (email/QR/id) → valida destinatario existe:
  → Paso2 monto + nota → valida límites usuario y balance:
    → Paso3 resumen (con comisiones si hay) + Confirmar:
      → POST /api/v1/transactions (Idempotency-Key, Idempotency-Request-Hash)
        → RiskEngine evalúa → ALLOW/CHALLENGE:
          - CHALLENGE → Modal "Confirma con tu código 2FA" → retry endpoint con mfaToken
            → ALLOW → @Transactional ProcessTxUseCase:
              - lock ordenado wallets, debit/credit, save tx, invalidate cache, emit notif a ambos
          ← 200 OK transactionId + estado COMPLETED
        → Frontend optimistic update: inserta fila PENDING → actualiza a COMPLETED
          → Toast + resumen en Drawer recipt.
```

### Flow C — Crear y Pagar PaymentRequest
```
Merchant → [Crear enlace pago] → monto + descripción + QR live preview:
  → POST /api/v1/merchant/payment-requests → crea id + shortCode único:
    ← 201 shortCode + URL + QR payload
      → Merchant comparte link/QR por WhatsApp/imprime.

Cliente → abre /pay/:shortCode (ruta pública):
  → ve resumen + opciones (login wallet o tarjeta directa):
    → si login y confirma → POST /api/v1/payment-requests/{id}/pay:
      - atomico: valida OPEN + cobra wallet cliente → acredita wallet merchant
        - marca PaymentRequest PAID → guarda paidWithTxId → envía webhook a merchant + in-app notif ambos
      ← éxito: pantalla "✅ Pago completado" + receipt + botón "Volver a mi billetera"
```

---

## Estructura del Mono-Repo (Organización Final)

```
sistema-micro-pagos/
├── backend/                           # Spring Boot - Java 21
│   ├── src/main/java/com/fintech/mp/
│   │   ├── domain/                    # entidades + value objects + ports + exceptions
│   │   ├── application/               # use cases + comandos/queries DTOs
│   │   ├── infrastructure/            # repos Postgres/JPA, Redis adapters, providers (Stripe, etc.)
│   │   ├── api/                       # controllers REST v1, DTOs, GlobalExceptionHandler, OpenAPI config
│   │   ├── config/                    # security, scheduler, Flyway, observability (Micrometer)
│   │   └── jobs/                      # @Scheduled beans: billing, webhook retries, reconciliation
│   ├── src/main/resources/
│   │   ├── db/migration/              # Flyway SQL V1__initial, V2__users_kyc, etc.
│   │   ├── templates/                 # emails Thymeleaf
│   │   └── application.yml
│   └── src/test/                      # unit (jqwik PBT) + integration (Testcontainers) + e2e slice
│
├── frontend-user/                     # React + TS + Vite + Tailwind
│   ├── src/
│   │   ├── pages/
│   │   ├── components/                # shadcn/ui + custom feature components
│   │   ├── hooks/                     # TanStack Query hooks (useCreateTopUp, useTxList)
│   │   ├── stores/                    # Zustand (auth, theme, notifications)
│   │   ├── lib/                       # api-client generado, utils, i18n, zod schemas
│   │   ├── i18n/locales/              # es-CO.json, en-US.json, pt-BR.json
│   │   └── App.tsx + main.tsx + router
│   └── playwrigth/                    # E2E tests user dashboard
│
├── frontend-admin/                    # Admin React SPA (compartiendo design system via symlink o package)
│   └── src/pages/+components/...
│
├── infra/
│   ├── docker-compose.yml             # app + postgres + redis + mailhog + nginx local
│   ├── docker-compose.ci.yml          # e2e tests
│   ├── nginx/dev.conf                 # proxy same-origin
│   ├── seed/                          # SQL/Jsons + script `seed-db.sh` para datos demo
│   └── k8s/                           # optional (helm values future)
│
├── docs/
│   ├── api/openapi.yml                # (autogenerado, commiteado para referencia)
│   └── architecture/adr/              # ADRs (Arquitectura Decision Records)
│
├── Makefile                           # targets: dev, backend, frontend-user, seed, test, e2e, build
├── README.md                          # instrucciones mono-repo setup
├── .env.example                       # todas las variables necesarias para dev
└── .github/workflows/                 # CI: test, e2e, build-push-images, deploy
```

---

## Reglas Estrictas de Separación y Responsabilidad Backend ↔ Frontend

> **Regla Inquebrantable #0**: Ningún archivo, directorio, lógica ni configuración se mezcla entre backend y frontend. Cada cosa pertenece a UNO y SOLO UNO de los proyectos, y debe vivir en su carpeta correspondiente. Ningún script ni importación podrá crear dependencias cruzadas directas (solo se comunican mediante el contrato OpenAPI + generación automática de cliente TypeScript).

### 1. Definición de Responsabilidades por Proyecto

| Capa / Responsabilidad | ¿Pertenece a BACKEND? | ¿Pertenece a FRONTEND? | Nota / Dónde vive |
|---|---|---|---|
| Lógica de negocio (Use Cases, invariantes, ACID, cálculo de fees) | ✅ EXCLUSIVO backend | ❌ NUNCA | `backend/src/main/java/**/application` + `domain` |
| Entidades y Value Objects del dominio | ✅ EXCLUSIVO backend | ❌ NUNCA | `backend/domain` — frontend solo conoce los DTOs via OpenAPI |
| Persistencia DB (JPA, SQL, Flyway, Redis queries) | ✅ EXCLUSIVO backend | ❌ NUNCA | `backend/infrastructure` + `src/main/resources/db/migration` |
| Auth server-side (password hash, JWT signing, RBAC, 2FA validation) | ✅ EXCLUSIVO backend | ❌ NUNCA | `backend/config/security` — frontend solo muestra UI, NUNCA valida permisos como enforcement (solo como conveniencia UX) |
| Envío emails/SMS/webhooks PUSH a terceros | ✅ EXCLUSIVO backend | ❌ NUNCA | `backend/infrastructure` NotificationPort adaptadores |
| Controladores REST + DTOs de request/response + OpenAPI spec | ✅ EXCLUSIVO backend | ❌ NUNCA | `backend/api` — fuente ÚNICA de verdad del contrato |
| **Cliente TS generado desde OpenAPI** | ❌ no vive en back | ✅ EXCLUSIVO frontend | `frontend-{user,admin}/src/lib/api-client/` — GENERADO AUTOMÁTICAMENTE, NUNCA editado a mano |
| UI (componentes React, páginas, layout, estilos, Tailwind) | ❌ nunca | ✅ EXCLUSIVO frontend | `frontend-*/src/{pages,components}` |
| Estado cliente (stores Zustand, TanStack Query cache, forms state) | ❌ nunca | ✅ EXCLUSIVO frontend | `frontend-*/src/{stores,hooks}` |
| i18n strings de UI (etiquetas botones, textos) | ❌ nunca | ✅ EXCLUSIVO frontend | `frontend-*/src/i18n/locales/*.json` — backend solo envía payloads con ids/claves, nunca mensajes UI hardcodeados si son de presentación |
| i18n strings de emails (plantillas Thymeleaf) | ✅ EXCLUSIVO backend | ❌ no | `backend/src/main/resources/templates/**` (i18n Thymeleaf) |
| Formateo números, fechas, monedas **para visualizar** en pantalla | ❌ no | ✅ EXCLUSIVO frontend | lib utils Intl / date-fns — backend SIEMPRE envía valores numéricos brutos + ISO timestamps |
| Validación ZOD de formularios (client-side) | ❌ no | ✅ EXCLUSIVO frontend | `frontend-*/src/lib/schemas` — SIEMPRE es una primera capa; backend re-valida TODO |
| Validación server-side (JSR-380 Bean Validation, invariantes dominio) | ✅ EXCLUSIVO backend | ❌ no debe confiarse en frontend | `backend/api` DTOs + domain — nada llega al use case sin validar en servidor |
| Seed data de demo + migrations SQL | ✅ EXCLUSIVO backend | ❌ nunca | `backend/src/main/resources/db/migration` y `infra/seed/` (compartido solo en infra como ejecutable) |
| Test de dominio / property-based / integración DB | ✅ EXCLUSIVO backend | ❌ no | `backend/src/test` |
| Test componentes / unitarios React / Vitest | ❌ no | ✅ EXCLUSIVO frontend | `frontend-*/src/**/*.test.tsx` |
| E2E Playwright (UI + stack completo) | ❌ no | ✅ Pertenece al mono-repo pero orquesta ambos | `frontend-user/playwright/` y/or `e2e/` en raíz como carpeta ORQUESTADORA compartida (única excepción permitida, porque usa tanto backend running como frontend) |
| Config build-time del lenguaje (pom.xml / Vite config) | ✅ EXCLUSIVO backend pom.xml en `backend/` | ✅ EXCLUSIVO frontend `frontend-*/vite.config.ts` | NUNCA un pom.xml en la raíz ni vite.config fuera de su carpeta |
| Variables de entorno | Ambas, PERO CADA UNO las suyas | | Backend lee `backend/.env` o raíz `.env` con prefijo `SPRING_`, Frontend usa variables `VITE_*` propias y exclusivas. NO se reutiliza un mismo nombre de variable para ambos (evitar colisiones). |
| Dockerfile (imagen app) | ✅ EXCLUSIVO backend su Dockerfile en `backend/src/main/docker` o `backend/Dockerfile` | ✅ EXCLUSIVO frontend su `frontend-*/Dockerfile` (nginx builder pattern) | NUNCA un Dockerfile único que compile ambos proyectos |
| Linter / formatter | Backend: Checkstyle / Spotless en `backend/` | Frontend: ESLint / Prettier en `frontend-*/` | Configs por proyecto, NO eslintrc global sin ruta explícita |
| Logs de aplicación | Backend: estructura en logs JSON via Logback (Micrometer tracing) | Frontend: NO envía logs de usuario sin anonymizar; error tracking via Sentry adapter | Cada uno su formato, cada uno su sink |
| Iconos, imágenes estáticas, fuentes | ❌ no | ✅ EXCLUSIVO frontend | `frontend-*/public/` o `src/assets` — backend NO embebe assets UI, solo provee URLs de descarga cuando hace falta |
| QR generation (códigos de payment request) | Solo lógica: backend **GENERA EL PAYLOAD STRING del QR** (ej: `https://host/pay/AbX99K`) | Renderiza la imagen SVG/Canvas a partir del string | Esta es una división funcional CLAVE: backend dueño de los datos, frontend dueño del render visual del código |

### 2. Reglas Ubicación de Archivos Nuevos (Pre-commit Checklist)

> Cualquier persona del equipo, antes de agregar un archivo nuevo, debe responder estas 3 preguntas y validar la ruta:

1. **¿Este archivo contiene código Java / SQL / Flyway / configuración Spring / lógica de negocio?**
   → ✅ **SÍ → va dentro de `backend/`**, exactamente:
   - Entidades y reglas de negocio → `backend/src/main/java/com/fintech/mp/domain`
   - Casos de uso → `application`
   - Controller REST / DTOs → `api`
   - Adapters Postgres/Redis/Stripe → `infrastructure`
   - Config (security, scheduler, properties) → `config`
   - Jobs recurrentes billing/webhooks → `jobs`
   - Migraciones SQL → `src/main/resources/db/migration/V{N}__description.sql`
   - Plantillas emails → `src/main/resources/templates/{es,en,pt}/`

2. **¿Este archivo contiene código TS/TSX, estilos, componentes UI, páginas, i18n strings, tests Vitest/Playwright de UI?**
   → ✅ **SÍ → va dentro de `frontend-user/` O `frontend-admin/`**, NUNCA en la raíz ni en backend:
   - Pantallas completas → `src/pages/{user,merchant,admin,auth,settings}/`
   - Cualquier cosa reutilizable (botones, data table, drawer receipt) → `src/components/`
   - TanStack Query wrappers por feature → `src/hooks/{wallet,topup,tx,subscription}.ts`
   - Zustand stores → `src/stores/{auth,theme,notifications,ui}.ts`
   - Locales i18n → `src/i18n/locales/{es-CO,en-US,pt-BR}.json`
   - Cliente API AUTOGENERADO → `src/lib/api-client/` (marcado .gitignore si es 100% generado o commit si es versionado)
   - Assets públicos → `public/`
   - Playwright E2E user dashboard → `frontend-user/playwright/tests/`
   - Playwright E2E admin → `frontend-admin/playwright/tests/`

3. **¿Es compartido cross-stack y NO entra en 1 ni 2? (docker-compose, README global, Makefile, OpenAPI generado, ADRs, CI workflows, seed script que corre contra contenedor, E2E super-shared)**
   → ✅ **SÍ → vive ÚNICAMENTE en la raíz o en carpetas compartidas designadas a continuación, y NUNCA contiene lógica de negocio ni componentes UI:**
   - Orquestación local → `infra/docker-compose.yml`, `infra/nginx/dev.conf`
   - Datos demo → `infra/seed/` (ejecutables contra DB, no JSX ni Java aquí)
   - Documentación → `docs/architecture/adr/*.md`, `docs/api/openapi.yml`, `docs/TESTING_CARDS.md`
   - Automatizaciones todos proyectos → `Makefile` raíz, `.github/workflows/`
   - Variables de entorno plantilla → `.env.example` raíz (con prefijos separados: `SPRING_*`, `VITE_USER_*`, `VITE_ADMIN_*`, `POSTGRES_*`, `REDIS_*`)
   - **Excepción única E2E Playwright stack completo**: si se requiere, `e2e/` en raíz para specs extremadamente cross que no encajen en un solo frontend — pero esta carpeta NUNCA contiene código de UI ni código de use cases; solo orquesta `docker compose up` + navegador + assertions.

### 3. Prohibiciones Explicitas (si se detecta en review, PR no se aprueba)

| ❌ PROHIBIDO HACER | ¿Por qué? | Cómo debe hacerse en su lugar |
|---|---|---|
| Crear archivos `.java` en `frontend-*/` | Mezcla de responsabilidad | Va en `backend/src/main/java/...` |
| Crear archivos `.tsx/.ts` de UI en `backend/src/main/resources/static/` | El backend no debe renderizar React del lado del servidor en este proyecto (no SSR planificado) | Va en `frontend-*/src` |
| Hacer `import` directo de un archivo `.ts` del frontend desde el backend o viceversa | Rompe compilación y acopla proyectos | La comunicación **ÚNICA** es HTTP + OpenAPI → `openapi-typescript-codegen` genera cliente TS, backend consume sus propias clases Java |
| Poner un `package.json` general en la raíz que mezcle dependencias React + Java | Herramientas y ciclos build distintos | Cada frontend su `package.json` en su carpeta; backend usa Maven/Gradle |
| Migración SQL Flyway `.sql` en `frontend-*/` ni en `docs/` | Es infraestructura de persistencia backend | Únicamente `backend/src/main/resources/db/migration/` con nomenclatura V{num}__{nombre}.sql |
| Archivo `.json` de i18n UI en `backend/src/main/resources/i18n/` | Strings botones son presentacionales front | Únicamente `frontend-*/src/i18n/locales/*.json` |
| Hardcodear en backend un mensaje estilo "Por favor confirma la transacción pulsando el botón azul" | Frontend decide color y copy | Backend envía `eventType: "TRANSFER_PENDING_CONFIRMATION"` con metadata; frontend renderiza el copy y el color del botón |
| Frontend calcula lógica de fees para "mostrar el total final" sin que backend también lo calcule | Usuario podría manipular JS | Frontend PUEDE mostrar una estimación en base a tarifas publicadas, pero el total real DEBE venir en el endpoint de confirmación, y se muestra resumen final con lo que devuelve servidor |
| Frontend valida "este usuario no puede retirar < $X porque es nivel KYC 0" y lo toma como verdad único | Cliente se puede tamperar con DevTools | Frontend MUESTRA el upsell y deshabilita botón como UX; endpoint backend `POST /api/v1/withdrawals` revalida KYC tier y lanza 422 si no cumple (SIEMPRE enforcement en servidor) |
| Nombre variable entorno genérica `API_URL=http://localhost:8080` | Confunde qué proyecto la usa | Prefijo explícito: `VITE_USER_API_URL`, `VITE_ADMIN_API_URL`, backend no consume `VITE_*` para nada |
| `frontend-user/src/lib/api-client/` con archivos editados a mano | Pierdes sincronía con backend | Regenerar via `make generate-api-client` sobre spec OpenAPI generado. Si necesitas un wrapper, hazlo en `frontend-user/src/lib/api.ts` que importa el cliente generado, nunca edites el generado |

### 4. Scripts Validación Automática (Pre-commit / CI)

Para forzar estas reglas sin depender de review humano, se implementarán hooks y checks:

1. **Check ruta no mezclada (pre-commit hook via lefthook o husky + script):**
   ```bash
   # scripts/check-boundaries.sh — falla si encuentra archivos fuera de lugar
   test -z "$(git diff --cached --name-only | grep -E '^backend/.*\.tsx?$' || true)" || { echo "❌ TS/TSX en carpeta backend"; exit 1; }
   test -z "$(git diff --cached --name-only | grep -E '^frontend-.*/.*\.java$' || true)" || { echo "❌ JAVA en carpeta frontend"; exit 1; }
   test -z "$(git diff --cached --name-only | grep -E '^frontend-.*/.*\.sql$' || true)" || { echo "❌ SQL migration en frontend"; exit 1; }
   ```
2. **Job de CI "boundaries-lint"**: Ejecuta el script contra todos los archivos del PR, bloqueando merge si pasa algo.
3. **Check cliente API no editado a mano**: Hash de `openapi.yml` + checksum de archivos en `api-client/`; si difieren sin pasar por el generador, falla CI.

### 5. Diagrama Dependencias Máxima (qué puede importar de qué)

```
              ┌─────────────────────────────────────────┐
              │           Contrato Único: OpenAPI      │
              │         (fuente de verdad → backend/   │
              │          src/main/java/ → springdoc →  │
              │          docs/api/openapi.yml)         │
              └──────────────┬────────────┬─────────────┘
          generate:typescript  │            │ HTTP + Auth (JWT/refresh)
              ┌────────────────▼──┐     ┌──▼───────────────────┐
              │  frontend-user/   │     │  frontend-admin/     │
              │  React 18 + TS    │     │  React 18 + TS       │
              │  (SIN lógica de   │     │  (SIN enforcement de │
              │   negocio pura)   │     │   permisos/reglas)    │
              └───────────────────┘     └──────────────────────┘
                         │  HTTP ONLY               │
                         ▼                          ▼
              ┌─────────────────────────────────────────────────┐
              │                   backend/                      │
              │      Domain ← Application ← API (REST v1)       │
              │              ↑                                   │
              │      Infrastructure (JPA, Redis, Stripe...)     │
              │  (NO conoce nada de React ni TSX ni rutas UI)   │
              └─────────────────────────────────────────────────┘
```

---

## Roadmap de Priorización (por fases)

> Para que la implementación no sea abrumadora, se mantienen las tareas opcionales originales y se propone esta secuencia incremental.

### Phase A — MVP Base (lo que ya estaba + auth + frontend user básico)
1. Tareas 1-15 originales (core P2P + ACID + caché + tests)
2. Signup/login JWT + roles USER/MERCHANT
3. Frontend User: auth + dashboard + send + activity + top-up mock
4. Moneda única COP (Pesos Colombianos), sin fees reales, single-currency default

### Phase B — MVP Productivo (cash-in/out + merchant)
5. Top-Up real via Stripe (adaptador PaymentGatewayPort)
6. Withdraw + KYC básico (tier 0,1,2) + tier limits enforcement
7. PaymentRequest completo con shortCode + QR + webhook merchants
8. Frontend Merchant view (dashboard + crear request)
9. Notificaciones in-app + email

### Phase C — Crecimiento (recurring + refunds + fees + risk)
10. Plans & Subscriptions + billing scheduler + dunning
11. Refunds total/parcial + FeeEngine + fee_ledger
12. RiskEngine básico + velocity checks + step-up 2FA
13. Admin Dashboard (compliance + finance + adjustments + freeze)
14. Reports CSV + exports asíncronos

### Phase D — Escalabilidad e Internacionalización
15. Multi-currency + FxRatePort + cross-border transfers
16. Referrals + rewards
17. i18n + theme light/dark + accesibilidad WCAG tweaks
18. Reconciliación automática vs providers
19. E2E tests críticos + visual regression
20. Hardening security: CSP, rotación secrets, audit alerts

---

## Testing Tools & Sandbox Mode (Capacidades Adicionales de Testeabilidad)

> Esta sección documenta mejoras opcionales pero altamente recomendadas para que el sistema sea aún más testeable. Todas son **no destructivas** y se activan por profile/feature flag.

### 1. Modo SANDBOX Global con Feature Flag Maestro

Un único `application-sandbox.yml` de Spring que activa, en una sola línea, **todos los adaptadores externos en modo mock/in-memory**. Útil para levantar el sistema completo sin tocar ninguna API externa.

#### Profile `sandbox` (Spring Profiles)
```yaml
# application-sandbox.yml — se activa con SPRING_PROFILES_ACTIVE=sandbox
mp:
  sandbox:
    enabled: true
  adapters:
    payment-gateway:  MockPaymentGatewayAdapter  # devuelve sessions fake + always SUCCEEDED webhook al poll
    payout-provider:  MockPayoutProviderAdapter  # payout siempre COMPLETED tras 10s
    kyc-provider:     InMemoryKycAdapter         # cualquier documento siempre APRUEBA
    notification:     LogOnlyNotificationAdapter # guarda emails en tabla fake y log, no envía SMTP
    fx-rate:          FixedFxRateAdapter         # USD/COP fijo 4.150, EUR/COP 4.450, COP default native 1.0
    fees:             ZeroFeesAdapter            # comisiones 0% + $0.00 para facilitar asserts en tests
    risk:             AllowAllRiskEngineAdapter  # todo ALLOW (no CHALLENGE ni BLOCK en tests)
  webhooks:
    auto-delivery: false                         # no dispara webhooks reales, guarda en cola memory
  features:
    multi-currency: true
    referrals: true
```

#### Cómo se usa:
```bash
# Dev local normal (adaptadores live/mock según config por defecto)
make dev

# Full sandbox 100% offline, sin terceros:
SPRING_PROFILES_ACTIVE=sandbox make backend
```

El profile `sandbox` también habilita endpoints de testing solo en ese profile (404 en prod):
- `POST /api/v1/_sandbox/trigger-topup-webhook` — simula webhook exitoso para cualquier TopUp creado
- `POST /api/v1/_sandbox/trigger-payout-update` — marca payout COMPLETED/FAILED
- `POST /api/v1/_sandbox/kyc/approve-user` — aprueba KYC de un userId instantáneamente
- `POST /api/v1/_sandbox/time/shift` — avanza reloj lógico (para tests subscription billing)
- `GET  /api/v1/_sandbox/notifications/:userId` — lista últimas notificaciones sin enviar

---

### 2. Mock Server OpenAPI con Prism (Workstream Frontend ↔ Backend Paralelo)

**Prism** es un servidor mock que lee `openapi.yml` generado por springdoc y devuelve ejemplos válidos. Permite que el **equipo frontend desarrolle y pruebe la UI ANTES de que exista el backend**.

#### Integración en el mono-repo:
```
infra/
└── mock-server/
    ├── Dockerfile          # usa imagen stoplight/prism:5
    └── README.md           # cómo levantar: prism mock -p 4010 docs/api/openapi.yml
```

#### docker-compose servicio adicional:
```yaml
# docker-compose.yml (bloque nuevo)
  prism-mock-api:
    image: stoplight/prism:5
    command: mock -h 0.0.0.0 -p 4010 /tmp/openapi.yml
    volumes:
      - ./docs/api/openapi.yml:/tmp/openapi.yml:ro
    ports:
      - "4010:4010"
```

#### Frontend endpoint dual:
```ts
// vite.config.ts o env vars
VITE_API_BASE_URL =
  NODE_ENV === 'mock-server'
    ? 'http://localhost:4010'   // Prism (sin backend vivo)
    : 'http://localhost:8080'; // backend Spring real
```

Con Prism + ejemplos bien definidos en el OpenAPI (`@Schema(example = "...")` en los DTOs Java), cualquier pantalla del frontend se puede probar con datos coherentes sin escribir ni un endpoint todavía.

**Extensiones recomendadas:**
- Prism también permite `prism validate` — valida que la respuesta del backend real cumpla el spec (contrato bidireccional).
- Agregar al CI un job de contract testing que corre Prism Validate tras build backend.

---

### 3. Tarjetas de Prueba Documentadas + Flujos de Testing en README

Lista oficial de tarjetas y flujos para modo Test de los proveedores. **Nunca son tarjetas reales**, solo funcionan en ambientes sandbox/test de cada plataforma. Se documentan en `docs/TESTING_CARDS.md` y el README principal.

#### Stripe Test Cards (Global) — https://stripe.com/docs/testing

| Número | Brand | Descripción / Flujo | Cualquier fecha futuro | CVC cualquier |
|---|---|---|---|---|
| `4242 4242 4242 4242` | Visa | ✅ Pago exitoso directo | `12/34` | `123` |
| `5555 5555 5555 4444` | Mastercard | ✅ Pago exitoso Mastercard | `12/34` | `123` |
| `4000 0025 0000 3155` | Visa | ⚠️ Requiere autenticación 3DS SCA (modal prueba) | `12/34` | `123` |
| `4000 0000 0000 0002` | Visa | ❌ Declinada genérica (probar error handling) | `12/34` | `123` |
| `4000 0000 0000 0069` | Visa | ❌ Expirada (probar validación) | `12/34` | `123` |
| `4000 0000 0000 0119` | Visa | ❌ Processing error (retry idempotency) | `12/34` | `123` |
| `4000 0082 6000 3178` | Visa | ✅ Exitosa + disputes/disputas simulables | `12/34` | `123` |

**Stripe Payouts (Cuenta Connect Prueba):**
- Cuenta bancaria exitosa USA routing `000000000` + account `000123456789`
- Payout fallido: routing `110000000` (devuelve ACH return)

---

#### Wompi (Colombia — Bancolombia Fintech) — docs.wompi.co/en/testing

| Número | Brand | Resultado |
|---|---|---|
| `4242 4242 4242 4242` | Visa | ✅ Pago aprobado automático |
| `4575 6237 0837 7932` | Mastercard | ✅ Pago aprobado Mastercard |
| `4111 1111 1111 1111` | Visa | ⚠️ Pago PENDING — para probar estados intermedios |
| `4000 0000 0000 0002` | Visa | ❌ Declinada (rechazada por emisor) |
| `4000 0000 0000 0069` | Visa | ❌ Declinada tarjeta expirada |
| `4000 0000 0000 0119` | Visa | ❌ Error procesamiento (retry idempotency) |

**Wompi Nequi / Daviplata Testing (PSE-like billetera móvil):**
- Para éxito: ingresar **cualquier número de celular** formato `+57 3XX XXX XXXX` → en sandbox se genera OTP `000000` fijo
- Para fallo: usar número `+57 300 000 0001` → devuelve `FONDOS_INSUFICIENTES`
- Para pending: usar número `+57 300 000 0002`

---

#### PlaceToPay (Evertec / Colombia — PSE + Tarjetas) — docs.placetopay.com/docs/checkout/tarjetas-de-prueba

Tarjetas PlaceToPay:
| Número | Brand | CVC | Fecha exp | Resultado |
|---|---|---|---|---|
| `4575 6210 8000 0275` | Visa | `123` | `12/30` | ✅ Aprobada |
| `5420 7201 2835 0003` | Mastercard | `123` | `12/30` | ✅ Aprobada |
| `4111 1111 1111 2222` | Visa | `123` | `12/30` | ❌ Declinada |
| `4111 1111 1111 3333` | Visa | `123` | `12/30` | ⚠️ Requiere 3DS Secure (modal) |

**PSE (Pagos Seguros en Línea ACH Colombia) testing PlaceToPay/Wompi:**
Para simular transferencia PSE exitosa/fallida usar estas credenciales de banco ficticio o elegir **Banco de Prueba (TEST)** si aparece en el listado:
- ✅ **PSE Exitosa**: seleccionar tipo persona NATURAL, cedula `1020304050`, banco `Bancolombia TEST`, monto cualquiera → código OTP/seguridad `123456`
- ❌ **PSE Fallida fondos**: tipo persona NATURAL, cedula `1020304051`, banco `Banco Pruebas` → estado `SALDO_INSUFICIENTE`
- ⏳ **PSE Pendiente**: cedula `1020304052`, monto exacto `$150.000 COP` → estado `PENDING` resuelve a los 30s

---

#### Transferencias Bancarias ACH Colombia / Bancolombia Prueba

Bancos principales y cuentas de testing (sandbox):
| Tipo | Banco | No. Cuenta / CBU virtual | Estado |
|---|---|---|---|
| ✅ Éxito | Bancolombia Ahorros | `001-001-0000012345` | Confirmada en < 10s sandbox |
| ✅ Éxito | BBVA Colombia | `002-001-9876543210` | Confirmada < 10s |
| ✅ Éxito | Nequi (asociada Bancolombia) | Celular `+57 310 000 0001` | OTP `000000` siempre exitosa |
| ✅ Éxito | Daviplata (Grupo Éxito / Bancamía) | Celular `+57 310 000 0002` | OTP `000000` |
| ❌ Fallo | Bancolombia Fallo | `001-001-0000000009` | Estado RECHAZADA (número inválido simulado) |
| ❌ Fallo Fondos | Davivienda | `003-001-9999999999` | SALDO_INSUFICIENTE |

---

#### PayPal Sandbox (Global) — developer.paypal.com/tools/sandbox

- No hay "tarjetas fijas"; se crean **cuentas sandbox ficticias** (comprador/vendedor) desde el Dashboard PayPal Developer.
- Configurar país de la cuenta sandbox = **COLOMBIA (CO)** + moneda = COP para probar retiros/payouts a Colombia
- Credenciales típicas test: `comprador-colombia@example.com` / `PasswordDev123!` (crear en dashboard)
- **Flujo negativo pago denegado:** crear cuenta sandbox con balance $0 COP

---

#### Stripe Connect Payouts Colombia (Retiros a cuentas bancarias)

Stripe Connect Custom/Express permite payouts en COP a cuentas ACH Colombia (transferencia bancaria ordinaria). Para modo testing:
- **Cuenta exitosa Colombia (ACH Stripe):** usar `country=CO`, `currency=COP`, `account_number=0001234567890` (cualquier 13 dígitos) + `routing_number=000000000`
- **Payout fallido:** routing number `111111111` → Stripe devuelve `FAILED` con failure_code `account_closed` o `could_not_process`
- Stripe NO soporta retiros a Nequi/Daviplata directamente vía Connect (debe usarse un proveedor local como Wompi/PlaceToPay/Addi para wallets móviles); por eso el proyecto incluye ports PayoutProvider multi-adaptador.

---

#### Documentación en README del Mono-repo (pseudocódigo plantilla COLOMBIA):

```markdown
## 🧪 Modo Testing / Pruebas (COLOMBIA / COP default)

### 1. Sandbox Full (sin servicios reales)
```bash
SPRING_PROFILES_ACTIVE=sandbox make dev
# Endpoints testing: /api/v1/_sandbox/*
# Moneda default: COP | Idioma default: es-CO
```

### 2. Mock Server Frontend-only (no necesitas levantar backend)
```bash
docker compose up prism-mock-api
# UI apunta a http://localhost:4010 — 100% datos de ejemplo OpenAPI
```

### 3. Modo Test Providers LIVE SANDBOX (dinero ficticio)
Tarjetas y cuentas de prueba oficiales:
[Stripe](docs/TESTING_CARDS.md#stripe) · [Wompi](docs/TESTING_CARDS.md#wompi) · [PlaceToPay + PSE](docs/TESTING_CARDS.md#placetopay) · [Transferencias Bancarias Bancolombia/BBVA/Nequi/Daviplata](docs/TESTING_CARDS.md#bancos-col) · [PayPal Sandbox Colombia](docs/TESTING_CARDS.md#paypal-co)

Ejemplo top-up exitoso PESOS COLOMBIANOS:
```bash
POST /api/v1/top-ups
{ "amount": 150000, "currency": "COP", "provider": "WOMPI" }
→ en checkout Wompi usa: 4242 4242 4242 4242 · exp 12/30 · CVC 123 · cualquier nombre
```
```

---

# Especificidades País: COLOMBIA (KYC, Impuestos, Proveedores, UX)

> Esta sección codifica todas las reglas específicas para despliegue en Colombia, desde KYC hasta tratamiento fiscal. Implementación: se configura en `application.yml` con `mp.country.default=CO` + feature flags, y los use cases aplican la regla correspondiente via `CountryPolicyPort`.

## 1. País y Moneda Por Defecto

- **País (Country ISO):** `CO` (Colombia)
- **Moneda (ISO 4217):** `COP` Peso Colombiano
- **Idioma / Locale por defecto:** `es-CO` (formato fechas `dd/MM/yyyy`, millares con `.`, decimal con `,` en formato de números generales)
- **Zona Horaria default usuarios nuevos:** `America/Bogota` (UTC-5)
- **Campo teléfono formato:** `+57 3XX XXX XXXX` para móviles (Nequi/Daviplata); validación con lib `libphonenumber-js` región CO

## 2. KYC Específico Colombia (Requisitos DIAN/UAF)

| KYC Tier | Documentos requeridos | Limites por defecto COP |
|---|---|---|
| **UNVERIFIED (0)** — al hacer signup solo email+teléfono SIN verificar | Nada | Top-Up $500.000 COP total, NO retiros |
| **BASIC (1)** — email verificado + teléfono verificado (SMS OTP) + datos básicos (nombre, apellidos, fecha nac, dirección ciudad) | Correo + Teléfono + Perfil básico | Top-Up: $3.000.000 COP / mes, Transfer P2P $1.000.000 / día, NO Retiros |
| **VERIFIED (2)** — Persona Natural Colombiana (general para 90% usuarios): Cédula de Ciudadanía + Selfie con documento + Dirección detallada (dirección, barrio, ciudad/dpto/municipio) + RUT si aplica | **Cédula CC** (8-10 dígitos sin puntos ni guiones) · foto frente y reverso · selfie de rostro sosteniendo documento visible (comparación liveness automático vía KycProvider). Para extranjeros residentes: Cédula Extranjería | Top-Up $20.000.000 / mes, Retiros $15.000.000 / mes, Transferencias $5.000.000 / día, Máx retiro individual $8.000.000 (límite regulatorio para algunos proveedores) |
| **VERIFIED-PJ (2BIS)** — Persona Jurídica / Comercios: además del representante legal VERIFIED(2): Cámara de Comercio < 90 días, RUT certificado DIAN, Estatutos, Carta representante legal, Lista accionistas >25% | Cámara + RUT + Estatutos + identificación RL | Top-Up $100.000.000 / mes, Retiros $80.000.000 / mes, PaymentRequest máximo $50.000.000 |
| **ACCREDITED (3)** — Validación financiera adicional (retiros mayores): 2 últimos extractos bancarios, declaración de renta, listas PEPs/sanciones screening avanzado | Extractos + Declaración de renta + PEP screening | Limites definidos caso por caso FINANCE; retiros $100M+ |

**Validaciones adicionales KYC COLOMBIA (dentro de KycProviderPort):**
- Cédula de ciudadanía: validación algoritmo de módulo 11 colombiano (no solo dígitos). Existe librería Java de cédula colombiana; implementar como policy en Domain layer.
- Autocreación RUT persona natural: para tier VERIFIED(2) natural, el sistema puede opcionalmente generar numero RUT válido (mismo NIT CC+DV) y mostrar al usuario para validación DIAN (no reemplaza trámite oficial, solo evita errores digitación)
- Screening: listas OFAC + Lista Clinton + Listas UAF + Listas SIJIN (Obligación SARLAFT SOG)

## 3. Impuestos y Tratamiento Fiscal en Fees (COP)

El `FeeEnginePort` en modo `country=CO` agrega automáticamente estas líneas además del fee de servicio base (son OBLIGATORIOS por ley colombiana y deben desglosarse en la UI y en el comprobante/factura electrónica):

| Impuesto | Aplicación | Tasa por defecto | Base |
|---|---|---|---|
| **IVA (Impuesto Valor Agregado)** | Sobre la COMISIÓN de servicio (no sobre el monto de transferencia) | 19% | `feeAmount` (plataforma revenue, sin incluir impuestos anteriores) |
| **Rete-Fuente (Retención en la Fuente IR)** | Sobre comisión de servicio cuando el comisionista (la plataforma) factura a un merchant persona jurídica (también aplica a retiros si el payout provider declara) | 2.5% si es régimen común (por defecto) o 0% si merchant se valida como autónomo régimen simple declarado | `feeAmount` base + IVA de fee |
| **Rete-ICA (Retención Industria y Comercio)** | Sobre fee de servicio según tarifa ciudad/departamento | Tarifa default 0.3% (Bogotá 0.3%, Medellín 0.2%, Barranquilla 0.4%, Cali 0.5% — configurable por ciudad del merchant) | `feeAmount` sin IVA |
| **Rete-IVA** | Sobre IVA de fee, para transacciones B2B con clientes regimen común | 15% (sobre IVA) | `feeIva` (19% de feeAmount) |

Regla fiscal importante: **Los impuestos NUNCA se calculan sobre el monto enviado/recibido del usuario**, SÓLO sobre el fee que cobra la plataforma. Ejemplo práctico:
> Envío $100.000 COP P2P. Comisión 1% = $1.000 COP. IVA 19% sobre fee = $190 COP. Total Fee con impuestos = $1.190 COP. Total debitado usuario emisor = $101.190 COP. Acreditado usuario receptor = $100.000 COP.
> En desglose fee_ledger: 3 rows: FEE_SVC $1000 · IVA_19 $190 (no es revenue, es passthrough DIAN) · RETE_FTE_25 $25 si aplica

**Impuestos OPCIONALES (por caso de uso):**
- **GMF (Gravamen Movimiento Financiero 4x1000):** Si integración con bancos colombianos reales (SPEI/PSE en banco) aplica; configurar como tax "passthrough" (`mp.taxes.gmf_enabled=true/false`). Para plataforma "billetera virtual" NO se aplica GMF si los fondos NO pasan por cuenta captación bancaria (solo pasarelas).
- **Bolsillo solidario 1x1000:** aplica a retiros en efectivo en corresponsales bancarios (no a retiros ACH), deshabilitado por defecto.

**Comprobante fiscal:** Todo fee y top-up/retiro debe tener opcionalmente Factura Electrónica (FE) Colombiana DIAN vía adaptador `FacturacionElectronicaPort` (Addi/Zapier/Felera/DIAN directa). MVP: solo exportar PDF con datos fiscales básicos; producción: FE obligatoria para mayores UVT de facturación anual.

## 4. Proveedores de Pago Recomendados Colombia (Prioridad)

Recomendación de adaptadores por prioridad para despliegue real en Colombia:

| Flujo | Prioridad 1 (primero) | Prioridad 2 (alternativa) | Prioridad 3 (global fallback) |
|---|---|---|---|
| **Top-Up Tarjeta** | Wompi (Bancolombia, tasas bajas CO) | PlaceToPay (Evertec, cobertura nacional) | Stripe Colombia |
| **Top-Up PSE (transferencia desde app bancaria usuario)** | PlaceToPay (mejor soporte PSE) | Wompi PSE | Stripe BankTransfer ACH CO (beta) |
| **Top-Up Nequi / Daviplata (billetera móvil)** | Wompi (integración oficial Nequi Bancolombia) | PlaceToPay + Nequi API | Addi (compra ahora paga después NO) |
| **Retiros Bancolombia / Davivienda (ACH CO)** | Wompi Payouts (Bancolombia) | PlaceToPay Transferencia Bancaria | Stripe Connect COP |
| **Retiros Nequi Daviplata** | Wompi (directo) | **Port separado**: Addi / Bold / Bancamía | - |
| **Payout Provider Port masivos (comercios)** | PlaceToPay (Evertec) PayOuts | Wompi Pro (Masivos) | Stripe Connect Express |
| **Factura Electrónica DIAN** | Zapier Fe | Felera | Addi Facturación |

## 5. UX Colombiana (Frontend Mejoras Específicas)

Elementos UI que deben mostrarse por defecto `country=CO`:
- **Selector Pago:** En grid proveedores top-up, resaltar como **"recomendado COLOMBIA"** Wompi (tarjeta + Nequi) + PlaceToPay (PSE) antes que Stripe/PayPal globales.
- **Formateo moneda en inputs:** el usuario al escribir $100000 se muestra como `$100.000` (0 decimales, separador miles punto). Input Mask `mask: ['.', /\d/]` con `numeric: true`, max 12 dígitos (999.999.999.999 COP).
- **Autocompletado Rápido:** en input destinatario, mostrar "Escribe # de celular Nequi/Daviplata o correo" además de email.
- **Campos formulario KYC:** autocompletar departamentos + municipios Colombia según DANE (lista ~1102 municipios + 32 dptos, zip en frontend).
- **Mostrar UVT (Unidad Valor Tributario) actual:** en screens fee breakdown y límites, tooltip "$X.XXX.XXX COP = 1 UVT 2025" como referencia. UVT 2025 = $50.814 COP; cargar desde config Spring `mp.taxes.uvt_current_year_2025_cop: 50814` actualizable anualmente.
- **Tipos documento dropdown KYC default orden CO:**
  1. Cédula Ciudadanía (CC)
  2. Cédula Extranjería (CE)
  3. Tarjeta Identidad (TI, menores 18 - con tutor)
  4. Registro Civil Nacimiento
  5. Pasaporte
  6. NIT (Persona Jurídica)
- **Banner informativo transparencia SOG/SARLAFT:** footer obligatorio por resolución UAF: "Este sistema de pagos cumple con el Sistema de Gestión del Riesgo Integral LA/FT (SARLAFT) de la UAF." + link a política anti-blanqueo.
- **Reintentos PSE:** Si usuario cierra pestaña PSE antes de terminar, mostrar banner "Continúa tu recarga PSE de $X.XXX COP →" con botón "Ver estado" polling hasta 15min (tiempo vida típica sesión PSE).

## 6. Topes Regulatorios y Límites Estándar Industria COLOMBIA

En `TierLimitsPort` por defecto para `country=CO`:

| Operación | UNVERIFIED 0 | BASIC 1 | VERIFIED 2 (CC Natural) | VERIFIED-PJ Jurídica |
|---|---|---|---|---|
| Top-Up acumulado mes | $500k | $3M | $20M | $100M |
| Retiros mes | ❌ | ❌ | $15M | $80M |
| Retiro individual max | ❌ | ❌ | $8M | $50M |
| Transferencia P2P / día | $100k | $1M | $5M | $20M |
| PaymentRequest monto max | $300k | $1.5M | $10M | $50M |
| Número max transacciones día | 5 | 25 | 100 | 500 |
| Monto min operación top-up | $10.000 | $10.000 | $5.000 | $5.000 |
| Monto min retiro | — | — | $50.000 | $200.000 |

Estos límites son parámetro, se cambian vía Spring Properties sin deploy: `mp.limits.tier2.monthly_topup_cop=20000000`.
