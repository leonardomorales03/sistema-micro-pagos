# Requirements Document

## Introduction

This document specifies the requirements for a micro-payments system built with hexagonal architecture. The system manages digital wallets, processes transactions between users, implements caching for performance optimization, and includes comprehensive testing and DevOps practices. The system is designed to handle high-volume micro-transactions with ACID guarantees, balance caching, and production-ready deployment capabilities.

## Glossary

- **Wallet**: A digital account that holds a balance for a user
- **Transaction**: A transfer of funds from one wallet to another
- **Balance**: The current amount of funds in a wallet
- **Domain**: The core business logic layer independent of infrastructure
- **Port**: An interface that defines how the domain interacts with external systems
- **Adapter**: An implementation of a port that connects to specific infrastructure
- **Use_Case**: An application service that orchestrates domain operations
- **Repository**: A port for persisting and retrieving domain entities
- **Cache**: A temporary storage layer (Redis) for frequently accessed data
- **ACID**: Atomicity, Consistency, Isolation, Durability properties for transactions
- **System**: The micro-payments application

## Requirements

### Requirement 1: Wallet Management

**User Story:** As a user, I want to manage digital wallets, so that I can store and track my balance for micro-payments.

#### Acceptance Criteria

1. THE System SHALL create a new wallet with an initial balance of zero
2. WHEN a wallet is created, THE System SHALL assign a unique identifier to the wallet
3. THE System SHALL retrieve wallet information including current balance
4. WHEN querying a wallet, THE System SHALL return the wallet owner and current balance
5. THE System SHALL prevent creation of duplicate wallets for the same user

### Requirement 2: Transaction Processing

**User Story:** As a user, I want to transfer funds between wallets, so that I can make micro-payments to other users.

#### Acceptance Criteria

1. WHEN a transaction is initiated, THE System SHALL validate that the source wallet has sufficient balance
2. WHEN a transaction is valid, THE System SHALL deduct the amount from the source wallet and add it to the destination wallet
3. THE System SHALL record all transaction details including source, destination, amount, and timestamp
4. IF a transaction fails, THEN THE System SHALL rollback all changes and maintain wallet consistency
5. THE System SHALL prevent negative balances in any wallet
6. WHEN a transaction is completed, THE System SHALL return a transaction confirmation with a unique transaction ID

### Requirement 3: ACID Transaction Guarantees

**User Story:** As a system administrator, I want ACID guarantees for all transactions, so that data integrity is maintained even under concurrent operations.

#### Acceptance Criteria

1. THE System SHALL execute all wallet operations within database transactions
2. WHEN multiple transactions occur concurrently, THE System SHALL ensure isolation between transactions
3. IF a transaction fails at any step, THEN THE System SHALL rollback all changes atomically
4. THE System SHALL ensure durability by persisting all committed transactions to PostgreSQL
5. THE System SHALL maintain consistency by enforcing all wallet and transaction constraints

### Requirement 4: Balance Caching

**User Story:** As a system administrator, I want to cache wallet balances, so that read operations are fast and database load is reduced.

#### Acceptance Criteria

1. WHEN a wallet balance is queried, THE System SHALL check Redis cache first
2. IF the balance is not in cache, THEN THE System SHALL retrieve it from PostgreSQL and store it in Redis
3. WHEN a transaction modifies a wallet balance, THE System SHALL invalidate the cached balance for that wallet
4. THE System SHALL set an expiration time for cached balances
5. THE System SHALL handle cache failures gracefully by falling back to database queries

### Requirement 5: Hexagonal Architecture

**User Story:** As a developer, I want the system to follow hexagonal architecture, so that the domain logic is independent of infrastructure concerns.

#### Acceptance Criteria

1. THE System SHALL define domain entities (Wallet, Transaction) independent of persistence mechanisms
2. THE System SHALL define ports as interfaces for external interactions
3. THE System SHALL implement adapters for PostgreSQL persistence
4. THE System SHALL implement adapters for Redis caching
5. THE System SHALL implement use cases that orchestrate domain operations through ports
6. WHEN infrastructure changes, THEN THE Domain SHALL remain unaffected

### Requirement 6: Data Persistence

**User Story:** As a system administrator, I want reliable data persistence, so that all wallet and transaction data is safely stored.

#### Acceptance Criteria

1. THE System SHALL persist wallet entities to PostgreSQL with proper schema
2. THE System SHALL persist transaction entities to PostgreSQL with proper schema
3. THE System SHALL use repository pattern to abstract persistence operations
4. THE System SHALL support querying transactions by wallet, date range, and transaction ID
5. THE System SHALL maintain referential integrity between wallets and transactions

### Requirement 7: Testing Coverage

**User Story:** As a developer, I want comprehensive testing, so that the system is reliable and maintainable.

#### Acceptance Criteria

1. THE System SHALL include unit tests for all domain entities and use cases
2. THE System SHALL include integration tests using Testcontainers for PostgreSQL and Redis
3. THE System SHALL include load tests to validate performance under high transaction volume
4. WHEN tests are executed, THE System SHALL verify ACID properties and cache behavior
5. THE System SHALL achieve minimum 80% code coverage

### Requirement 8: Containerization and Deployment

**User Story:** As a DevOps engineer, I want the application containerized, so that it can be deployed consistently across environments.

#### Acceptance Criteria

1. THE System SHALL provide a Dockerfile for building the application image
2. THE System SHALL provide a docker-compose.yml for local development with PostgreSQL and Redis
3. THE System SHALL configure environment-specific properties through environment variables
4. THE System SHALL expose health check endpoints for container orchestration
5. THE System SHALL log to stdout for container-friendly logging

### Requirement 9: Continuous Integration and Deployment

**User Story:** As a DevOps engineer, I want automated CI/CD pipelines, so that code changes are tested and deployed automatically.

#### Acceptance Criteria

1. THE System SHALL include a GitHub Actions workflow for building and testing
2. WHEN code is pushed, THE System SHALL run all unit and integration tests
3. THE System SHALL perform static code analysis using SonarQube
4. THE System SHALL build and publish Docker images on successful builds
5. THE System SHALL fail the pipeline if tests fail or code quality thresholds are not met

### Requirement 10: Error Handling and Validation

**User Story:** As a user, I want clear error messages, so that I understand why operations fail.

#### Acceptance Criteria

1. WHEN a transaction has insufficient funds, THE System SHALL return a descriptive error message
2. WHEN invalid input is provided, THE System SHALL validate and return specific validation errors
3. WHEN a wallet is not found, THE System SHALL return a not found error
4. THE System SHALL distinguish between client errors (4xx) and server errors (5xx)
5. THE System SHALL log all errors with sufficient context for debugging

### Requirement 11: API Endpoints

**User Story:** As a client application, I want REST API endpoints, so that I can interact with the micro-payments system.

#### Acceptance Criteria

1. THE System SHALL provide a POST endpoint to create wallets
2. THE System SHALL provide a GET endpoint to retrieve wallet information
3. THE System SHALL provide a POST endpoint to create transactions
4. THE System SHALL provide a GET endpoint to retrieve transaction history
5. THE System SHALL return appropriate HTTP status codes for all operations
6. THE System SHALL accept and return JSON payloads

### Requirement 12: Idempotent Transaction Creation

**User Story:** As a client application, I want transaction creation to be idempotent, so that retries do not create duplicate transfers.

#### Acceptance Criteria

1. THE System SHALL accept an idempotency key (e.g., `Idempotency-Key` header) for transaction creation requests
2. WHEN a request is repeated with the same idempotency key and equivalent payload, THE System SHALL return the same transaction confirmation without applying the transfer twice
3. IF a request is repeated with the same idempotency key but a different payload, THEN THE System SHALL reject it with a conflict response
4. THE System SHALL persist idempotency records for a configurable retention period

### Requirement 13: Concurrency Control and Double-Spend Prevention

**User Story:** As a system administrator, I want the system to be safe under concurrent transactions, so that balances remain correct and double-spend is prevented.

#### Acceptance Criteria

1. THE System SHALL prevent negative balances and lost updates under concurrent transaction processing
2. THE System SHALL implement a defined concurrency control strategy (optimistic locking with versioning or pessimistic locking)
3. IF pessimistic locking is used, THEN THE System SHALL apply deterministic lock ordering to minimize deadlocks
4. WHEN a concurrency conflict occurs, THE System SHALL return a specific error and MAY retry internally with bounded retries

### Requirement 14: Monetary Precision and Currency Handling

**User Story:** As a product owner, I want monetary amounts to be handled precisely and consistently, so that balances and transfers are accurate.

#### Acceptance Criteria

1. THE System SHALL represent monetary amounts using fixed-scale decimals with explicit rounding rules
2. THE System SHALL validate monetary bounds (minimum and maximum) and reject invalid amounts
3. THE System SHALL define currency handling (single currency or multi-currency) and enforce consistent currency rules for each transaction

### Requirement 15: Audit Trail and Traceability

**User Story:** As an auditor, I want an audit trail for wallet and transaction operations, so that actions are traceable.

#### Acceptance Criteria

1. THE System SHALL record audit events for wallet creation and the full transaction lifecycle (initiated, completed, failed)
2. THE System SHALL capture a correlation identifier and the acting user (when available) for audit events
3. THE System SHALL store audit events as append-only records

### Requirement 16: Observability (Metrics and Correlation)

**User Story:** As an operator, I want observability (metrics and traceability), so that I can monitor reliability and performance.

#### Acceptance Criteria

1. THE System SHALL expose metrics for transaction throughput/latency, error rates, and cache behavior
2. THE System SHALL propagate a correlation identifier across logs and API responses
3. THE System SHALL log operational events with sufficient context while avoiding sensitive data exposure

### Requirement 17: API Contract and Error Format

**User Story:** As a client developer, I want a stable API contract, so that client integrations are predictable and resilient.

#### Acceptance Criteria

1. THE System SHALL define stable JSON schemas for wallet and transaction requests/responses
2. THE System SHALL return errors in a consistent format (for example, Problem Details / RFC 7807)
3. THE System SHALL support pagination and filtering for transaction history queries

### Requirement 18: Authentication and Abuse Protection

**User Story:** As a security engineer, I want authentication and abuse protections, so that only authorized users can initiate transfers and the system resists abuse.

#### Acceptance Criteria

1. WHEN authentication is enabled, THE System SHALL authenticate requests and authorize that only the wallet owner can initiate debits
2. THE System SHALL apply rate limiting to transaction creation endpoints
3. THE System SHALL enforce per-request and per-user limits (for example, maximum amount and maximum request rate)

### Requirement 19: Wallet Top-Up (Recarga) via Payment Gateway

**User Story:** As a user, I want to add funds to my wallet using a payment method (card, transfer), so that I can then make micro-payments with the available balance.

#### Acceptance Criteria

1. THE System SHALL create a top-up intent with a unique ID and a requested amount
2. WHEN a top-up is created, THE System SHALL delegate collection to a Payment Gateway port (Stripe-like or bank-transfer style) and return a checkout handle/URL to the client
3. WHEN the gateway confirms payment via signed webhook, THE System SHALL credit the wallet and record a `TOP_UP` transaction with a reference to the external provider
4. IF a top-up webhook is replayed or tampered with, THE System SHALL reject it and preserve idempotency (no double credit)
5. THE System SHALL track top-up lifecycle (PENDING_PAYMENT, SUCCEEDED, FAILED, EXPIRED) and expose it via API
6. THE System SHALL enforce minimum and maximum top-up amounts per user verification level

### Requirement 20: Wallet Withdrawal (Retiro) to External Accounts

**User Story:** As a verified user, I want to withdraw wallet funds to a bank account or card, so that I can access the money outside the system.

#### Acceptance Criteria

1. THE System SHALL require the user to have completed KYC level-2 (or higher) before allowing any withdrawal
2. WHEN a withdrawal is requested, THE System SHALL validate sufficient balance, create a `WITHDRAWAL` transaction in PENDING state, and reserve the funds (deduct balance atomically)
3. THE System SHALL delegate payout execution to a Payout Provider port and track status (PROCESSING, COMPLETED, FAILED, REVERSED)
4. IF a payout fails at the provider, THE System SHALL refund (un-reserve) the reserved amount back to the wallet and mark the transaction as FAILED
5. THE System SHALL support storing external payout destinations (bank account, card) masked, with at least one destination verified per user before the first withdrawal
6. THE System SHALL enforce per-request, daily, and monthly withdrawal limits configurable per user tier

### Requirement 21: Merchant Payment Requests (Links/QR/Checkout)

**User Story:** As a merchant, I want to create payment requests (links or QR codes) for specific amounts, so that customers can pay me easily from their wallets.

#### Acceptance Criteria

1. THE System SHALL allow a merchant-role user to create a `PaymentRequest` with amount, currency, optional description/order reference, and expiry
2. WHEN a PaymentRequest is created, THE System SHALL return a short URL and a scannable QR payload encoding that URL or a protocol URI
3. WHEN a customer opens a PaymentRequest that is still open, THE System SHALL display the payment details and allow confirming the transfer from the customer wallet to the merchant wallet in one click
4. ONCE paid, THE System SHALL mark the PaymentRequest as PAID atomically with the P2P transfer, and return a confirmation receipt with transaction ID
5. THE System SHALL prevent double-payment of the same PaymentRequest (idempotent at request level)
6. THE System SHALL support optional webhook notifications to the merchant backend on payment state changes (CREATED, PAID, EXPIRED, REFUNDED)

### Requirement 22: Subscriptions and Recurring Billing

**User Story:** As a merchant, I want to charge a subscriber on a recurring schedule (weekly/monthly/yearly), so that I can offer membership, SaaS, or subscription products.

#### Acceptance Criteria

1. THE System SHALL allow a merchant to create a `Plan` with amount, currency, billing interval (day/week/month/year), and trial days (optional)
2. WHEN a subscriber approves a plan, THE System SHALL create a `Subscription` storing the mandate, next billing date, and the subscriber payment method (wallet debit by default)
3. A background scheduler SHALL run at least once per day and attempt billing for all subscriptions whose `nextBillingDate` has arrived
4. IF a billing attempt succeeds, THE System SHALL create a `SUBSCRIPTION` transaction, advance `nextBillingDate` to the next period, and notify both parties
5. IF a billing attempt fails (insufficient funds, canceled), THE System SHALL apply a configurable dunning/retries policy (e.g., retry on days +1, +3, +7) before marking the subscription as `PAST_DUE` or `CANCELED`
6. Subscribers SHALL be able to cancel or pause a subscription at any time with effect on the next billing cycle (no pro-rata refund unless explicitly configured)

### Requirement 23: Refunds (Reembolsos)

**User Story:** As a merchant or support operator, I want to refund a previous payment partially or totally, so that I can correct mistakes, returns, or disputes.

#### Acceptance Criteria

1. THE System SHALL allow initiating a `REFUND` referencing a completed source transaction (type P2P, TOP_UP, PAYMENT_REQUEST, or SUBSCRIPTION)
2. WHEN a refund is created, THE System SHALL validate that the refunding party is the original recipient (or an admin), and that the remaining refundable amount of the source transaction covers the requested amount
3. THE System SHALL execute the refund as an atomic reverse transfer (debit merchant, credit customer) and link it bidirectionally to the source transaction
4. THE System SHALL track total refunded amount per source transaction and prevent cumulative refunds exceeding the original amount
5. THE System SHALL distinguish between FULL and PARTIAL refunds in the transaction status/reason fields
6. IF the refunding party has insufficient balance at refund time, THE System SHALL reject the request without side effects and return a descriptive 422 error

### Requirement 24: User Identity, Profiles and KYC Tiers

**User Story:** As a compliance officer, I want users to have verified identities and tiered limits, so that the platform meets KYC/AML obligations and reduces fraud exposure.

#### Acceptance Criteria

1. THE System SHALL define a `UserProfile` entity (separate from Wallet owner-string) with userId, email, fullName, phone, country, createdAt, plus KYC status and tier
2. THE System SHALL implement at least three KYC tiers: `UNVERIFIED` (tier-0), `BASIC` (tier-1: email+phone), `VERIFIED` (tier-2: ID document + selfie + address), `ACCREDITED` (tier-3)
3. Each tier SHALL define a set of limits: max top-up per month, max withdrawal per month, max single transfer, max payment request amount; the system SHALL enforce these limits on every relevant operation
4. THE System SHALL expose a KYC port (KycProviderPort) with methods to initiate verification, upload documents, and receive status-change webhooks
5. WHEN KYC status changes, THE System SHALL update user tier, persist an audit event, and optionally notify the user
6. All wallet operations SHALL be attributed to an authenticated `userId` (not just the owner string) with proper authorization checks

### Requirement 25: Notifications and Webhooks

**User Story:** As a user or merchant, I want to be notified of important events on my wallet and receive webhooks to my backend, so that I can react in real time.

#### Acceptance Criteria

1. THE System SHALL define a `NotificationPort` supporting at least email and in-app channels, plus a `WebhookDeliveryPort` for merchant HTTP callbacks
2. The following events SHALL trigger notifications by default (with opt-out for non-critical ones): wallet credited, wallet debited, top-up succeeded/failed, withdrawal completed/failed, payment request paid, subscription charged/failed, KYC status changed, login from new device
3. THE System SHALL persist an append-only notification log per user and expose an API to list them with read/unread status
4. Merchant webhooks SHALL be signed (HMAC-SHA256 of body + timestamp) and include the event type, entity id, and an idempotency key; failed deliveries SHALL be retried with exponential backoff up to a configurable limit (e.g., ~24h)
5. THE System SHALL expose a test/simulate endpoint in sandbox mode for merchants to replay and verify their webhook handler
6. All outbound communications SHALL avoid leaking sensitive data (no full card numbers, no unmasked IDs in email bodies)

### Requirement 26: Admin Dashboard and Backoffice Capabilities

**User Story:** As a platform operator/support, I want a backoffice dashboard, so that I can review transactions, handle support tickets, adjust limits manually, and apply compliance actions.

#### Acceptance Criteria

1. THE System SHALL define an `ADMIN` role and a backoffice API surface protected by the same auth system with additional RBAC (roles: SUPPORT, COMPLIANCE, FINANCE, SUPER_ADMIN)
2. Backoffice users SHALL be able to search wallets and users by id, email, phone, or owner; view full wallet transaction history with filters and date ranges
3. THE System SHALL allow support (with audit trail) to manually credit or debit a wallet for goodwill/charge-resolution, creating `ADJUSTMENT` transactions with a mandatory `reason` field
4. COMPLIANCE role SHALL be able to flag/freezer a wallet (temporarily block all debits) and mark it as UNDER_REVIEW, plus release it after review; all actions SHALL be audited with actor, reason, and timestamp
5. FINANCE role SHALL be able to trigger reports: daily cashflow reconciliation, top-ups vs. withdrawals net balance, volume by country/tier, failed payouts queue
6. All backoffice endpoints SHALL require 2FA for login, shall be rate-limited, and SHALL emit audit events to append-only storage

### Requirement 27: Anti-Fraud, Velocity Checks, and Risk Rules

**User Story:** As a risk officer, I want automatic risk checks on sensitive operations, so that fraud and abuse are detected early and blocked when necessary.

#### Acceptance Criteria

1. THE System SHALL define a `RiskEnginePort` with an evaluate(context) method returning a decision: ALLOW, CHALLENGE, BLOCK with reasons
2. Default risk rules SHALL include at least: velocity limits (N transfers per minute/hour/day), amount thresholds by tier+country, new-device login risk, cross-border activity, unusual pattern vs. user historical behavior (simple scoring)
3. WHEN a transaction is CHALLENGED, THE System SHALL require step-up: 2FA code, email confirmation, or in-app biometric confirmation before executing
4. BLOCK decisions SHALL result in a 403/422 with a generic message to the user, a detailed reason in internal audit, and optionally an alert to compliance
5. Risk evaluation SHALL be extensible: rules SHALL be configurable via properties/DB without redeploy (e.g., per-country thresholds, known-wallet blacklist)
6. High-risk actions (first top-up on a new card, first withdrawal, large refund) SHALL be routed through the risk engine at minimum; normal P2P within-trusted-user behavior MAY be evaluated asynchronously after the fact for scoring

### Requirement 28: Reports, Exports and Reconciliation

**User Story:** As a finance user, I want exportable reports and reconciliation tools, so that I can close books monthly and match ledger entries to external providers.

#### Acceptance Criteria

1. THE System SHALL expose endpoints to export transaction history, top-up list, and withdrawal list in CSV format, with filters by date range, user/tier, and status; exports SHALL be generated asynchronously for large datasets and delivered via download link + email notification
2. THE System SHALL provide a daily cashflow report (total credited by type, total debited by type, net movement, fees collected) and a monthly ledger view
3. THE System SHALL support reconciliation against payment gateways and payout providers: a `ReconciliationJob` SHALL compare internal transactions to provider CSVs/APIs and flag mismatches (MISSING_AT_PROVIDER, AMOUNT_MISMATCH, UNEXPECTED_PROVIDER_ENTRY) for a FINANCE user to resolve
4. All report and export access SHALL be restricted by role, audited, and include a watermark/metadata in the generated file (exportedBy, exportedAt, filters applied)
5. For tax reporting, THE System SHALL allow exporting per-user annual summaries by country of residence with totals by transaction category

### Requirement 29: Multi-Currency Support and Foreign Exchange (FX)

**User Story:** As an international user, I want to hold balances and transact in multiple currencies with automatic conversion, so that cross-border payments work seamlessly.

#### Acceptance Criteria

1. THE System SHALL support multiple fiat currencies (USD, EUR, COP, ARS, BRL as baseline, with COP as default/native currency for Colombia deployment) and each user MAY have one wallet per currency or a single wallet with per-currency sub-balances (implementation choice documented and aligned with Money value object). For COP: Money internal precision `DECIMAL(19,2)` always, display formatting `0` decimals (whole pesos only) via Intl.NumberFormat `es-CO` style with thousand separator period (e.g. `$2.450.000` not `2,450,000.00`).
2. THE System SHALL define an `FxRatePort` that provides buy/sell rates for a currency pair with a timestamp; rates SHALL be cached with a short TTL and MUST be applied atomically inside the same transaction when a cross-currency operation occurs
3. WHEN a transfer crosses currencies (e.g., sender USD → recipient EUR), THE System SHALL debit the source amount, apply the applicable FX spread/fee, credit the converted amount in destination currency, and record the rate used on the transaction metadata
4. Cross-currency top-ups and withdrawals SHALL also go through FxRatePort and persist the effective rate for auditability
5. THE System SHALL enforce a consistent rounding policy (half-even, with explicit scale) for all FX conversions to prevent drift in cumulative balances
6. For MVP simplicity, single-currency mode SHALL remain the default; multi-currency SHALL be feature-flagged and opt-in per tenant/user

### Requirement 30: Commissions, Fees and Pricing Rules

**User Story:** As a platform operator, I want to collect configurable fees on transactions, so that the business generates revenue.

#### Acceptance Criteria

1. THE System SHALL define a `FeeEnginePort` that returns a list of `FeeLine` (amount, currency, type: FLAT, PERCENTAGE, FX_SPREAD) for a given operation context (operation type, participants, amount, tier, country)
2. Fees SHALL be calculated and deducted atomically within the same database transaction as the operation; each fee SHALL be recorded as a separate ledger entry linked to the originating transaction
3. Default fee configuration SHALL cover: P2P transfer (free or %+flat by tier), top-up (% of provider cost + markup), withdrawal (flat fee + provider fee), payment request paid (merchant discount rate MDR), cross-currency FX spread, refunds (fee non-refundable by default, configurable)
4. Fee rules SHALL be configurable in DB/properties and versioned, so that historical transactions always reference the rule version that applied at execution time
5. Admin backoffice SHALL include a dashboard of fees collected per day/week/month, segmented by fee type

### Requirement 31: Referrals and Rewards (Opt-in Growth Feature)

**User Story:** As a user, I want to invite friends and earn rewards, so that the platform grows virally and loyal users benefit.

#### Acceptance Criteria

1. Each user SHALL be assigned a unique referral code and referral URL upon signup; sharing the code is tracked via `Referral` entity (referrerId, inviteeId, status, createdAt)
2. WHEN a referred invitee completes a qualifying action (e.g., tier-1 KYC + first top-up ≥ $X), THE System SHALL credit a referral bonus to the referrer wallet and optionally to the invitee wallet as `REFERRAL_BONUS` transactions
3. THE System SHALL allow configurable reward campaigns: fixed amount, percentage of first top-up, limited time window, per-country caps; duplicates SHALL be prevented by idempotency key per referral rule + pair
4. Referral history SHALL be visible in the user dashboard with status and bonus received, and SHALL be exportable for tax/reporting

### Requirement 32: Frontend Web SPA — User Dashboard Experience

**User Story:** As an end user, I want a modern, fast, and intuitive web dashboard, so that I can manage my wallet, send money, view history, and handle notifications with delight.

#### Acceptance Criteria

1. THE System SHALL ship a production-ready Single-Page Application (SPA) for the user dashboard using a modern framework (React with TypeScript as baseline)
2. The SPA SHALL include at minimum: landing/login page, signup flow, email/phone verification, main dashboard with balance quick view, quick-actions (Send, Request, Top-up, Withdraw), activity/transactions list with filters+search+pagination, wallet detail, profile & KYC center, notifications panel, settings (security, language, theme)
3. All forms SHALL validate inline with clear error messages, SHALL show loading/skeleton states, and SHALL never leave the user without feedback on long operations (e.g., top-up polling until gateway confirms)
4. Transaction list items SHALL distinguish visually between inbound vs. outbound, show status (pending/completed/failed), reference, counterparty, amount with sign, currency, timestamp, and allow clicking to see a detail drawer/receipt
5. Dashboard SHALL be responsive and usable on mobile (360px width baseline) and desktop; touch interactions SHALL be first-class on mobile
6. Build output SHALL be optimized: code-splitting by route, tree-shaking, image lazy-loading, and target Lighthouse scores ≥ 90 in Performance, Accessibility, Best Practices, SEO for the main routes

### Requirement 33: Frontend — Merchant Experience

**User Story:** As a merchant, I want a dedicated merchant dashboard view, so that I can track my sales, create payment requests, manage plans/subscriptions, and configure webhooks.

#### Acceptance Criteria

1. A user with merchant role SHALL see a merchant dashboard section with a sales summary (today/week/month), top payment methods stats, and quick actions: create payment request, view paid requests, manage plans, subscriptions dashboard, webhooks config
2. "Create payment request" flow SHALL include live QR preview, copyable short link, optional metadata (order id, customer email), and the ability to toggle one-time vs. reusable open-ticket request
3. Plans and subscriptions views SHALL allow creating/editing plans, seeing active/past-due/canceled subs, retrying a failed billing, and exporting per-plan MRR (monthly recurring revenue) report
4. Webhooks configuration view SHALL let the merchant set an endpoint URL, rotate signing secrets, see delivery history per event with retry logs, and redeliver any failed event manually

### Requirement 34: Frontend — Accessibility, Internationalization, and Themes

**User Story:** As a diverse user, I want the app to be accessible, support my language, and respect my theme preference, so that I can use it comfortably regardless of ability, location, or taste.

#### Acceptance Criteria

1. The frontend SHALL comply with WCAG 2.1 AA accessibility requirements: semantic HTML, all interactive elements keyboard-accessible, sufficient color contrast (minimum 4.5:1 normal text, 3:1 large), labels for all inputs, ARIA where native semantics fall short, visible focus indicators
2. The app SHALL be fully internationalized (i18n) via a message catalog with at least: Spanish (es-CO, Colombian variant, DEFAULT locale), English (en-US), Portuguese (pt-BR) as baseline; adding a new language SHALL only require translating a JSON file without code changes; dates, numbers, and currencies SHALL be formatted per locale. For es-CO: date format dd/MM/yyyy, number decimal separator comma, thousand separator period, currency symbol `$` before amount without space.
3. The app SHALL support at least Light and Dark themes out-of-the-box, with automatic detection from OS settings and a manual toggle in user settings; theme SHALL persist across sessions via localStorage and server profile
4. A colorblind-friendly palette SHALL be used for status indicators (success/warning/error/info) not relying on color alone (icons + patterns + text labels)

### Requirement 35: Frontend — State Management, Optimistic UI, and Resilience

**User Story:** As a user, I want the UI to feel fast and robust even on flaky networks, so that I trust that my actions are handled correctly.

#### Acceptance Criteria

1. The SPA SHALL use a typed state-management approach (e.g., TanStack Query for server state + React Context/Zustand for UI state) keeping local state minimal and server state cached with smart invalidation
2. On user actions such as send-money or top-up confirm, the UI SHALL apply optimistic updates (balance changes, insert a PENDING transaction row) and reconcile once the API responds; on error, rollback and toast a clear message with a retry option
3. Network layer SHALL implement automatic retries for idempotent GET requests (with exponential backoff and jitter) and SHALL NOT retry non-idempotent POSTs unless the client has a client-generated idempotency key mapped to the endpoint
4. Offline support: while offline, the user SHALL browse cached dashboard data read-only; the UI SHALL show an obvious offline banner; queued user actions MAY be persisted locally and presented for confirmation once reconnected (optional MVP: mark disabled + explain)
5. Error boundaries SHALL prevent a single component crash from breaking the entire app; each boundary SHALL show a friendly message, a "try again" action, and log the error details to a frontend error-tracking port (Sentry-style abstraction)

### Requirement 36: Admin Frontend — Backoffice SPA

**User Story:** As an operator, I want a dedicated admin web app, so that I can perform support, compliance, and finance operations safely without touching the user-facing dashboard.

#### Acceptance Criteria

1. The System SHALL ship a separate admin SPA (or a route-restricted section of the main SPA guarded by admin roles) including: login with 2FA enforcement, user/wallet lookups with full detail view, transaction search with advanced filters, manual adjustment & reason capture, wallet freeze/unfreeze actions with dialogs, KYC queue review (approve/reject/documents preview), reports/exports center, audit log browser, role management
2. All destructive actions (freeze, adjustment, role change) SHALL require a confirmation dialog re-stating the consequences and a mandatory reason input text field
3. The admin UI SHALL use audit-trail-friendly navigation: deep links to user/wallet/transaction IDs, breadcrumbs, and copy buttons for IDs; timestamps SHALL show both local time and UTC
4. Admin role matrix SHALL be enforced on both frontend (menu visibility, button disabling) and backend (API-level RBAC), with frontend only as convenience, never as enforcement

### Requirement 37: Frontend-Backend Contract and Shared Types

**User Story:** As a frontend developer, I want an always-up-to-date typed contract with the backend, so that integrations are safe and predictable.

#### Acceptance Criteria

1. THE System SHALL expose an OpenAPI 3.x spec generated from the backend controllers (SpringDoc / springdoc-openapi) including all schemas, security schemes, idempotency header, pagination, and error responses
2. From the OpenAPI spec, a build step SHALL generate TypeScript types + a typed fetch/axios client; the generated client SHALL be used by the SPA with zero manual-copy-paste of request/response interfaces
3. The generated client SHALL enforce idempotency-key injection for mutation endpoints that require it, and SHALL expose helper types for filter params, paginated responses, and error formats
4. API versioning SHALL be URL-based (`/api/v1/...`); breaking schema changes SHALL bump the version, and old version SHALL be maintained at least one minor cycle with deprecation warnings in responses

### Requirement 38: Security — Session, CSRF, CORS, Secrets

**User Story:** As a security engineer, I want the frontend-backend communication hardened and secrets properly managed, so that the app resists common web attacks.

#### Acceptance Criteria

1. User authentication SHALL use short-lived access JWTs plus rotating refresh tokens stored in HttpOnly, Secure, SameSite=Lax cookies (or equivalent secure pattern) — tokens SHALL NOT be stored in localStorage when cookies are available
2. All state-changing endpoints SHALL be protected against CSRF via double-submit-cookie or SameSite enforcement (documented and tested)
3. CORS configuration SHALL be environment-specific, with explicit allow-lists of origins — no wildcard `*` in production when credentials are in use
4. Client-side code SHALL NOT embed production secrets (API keys, signing secrets); backend SHALL proxy any third-party calls that require secrets
5. Sensitive pages (dashboard, admin) SHALL use security headers in production: CSP (with strict nonce or hash-based if inline scripts are needed), X-Frame-Options DENY, Referrer-Policy strict-origin-when-cross-origin, Permissions-Policy limiting unnecessary APIs

### Requirement 39: Developer Experience (DX) — Mono-Repo Tooling and Local Dev Scripts

**User Story:** As a developer, I want a single repo setup that runs backend + frontend + infra with one command, so that I can be productive quickly and stay close to production behavior.

#### Acceptance Criteria

1. THE System SHALL be organized as a mono-repo with clear top-level folders: `backend/` (Spring Boot), `frontend-user/` (SPA user dashboard), `frontend-admin/` (SPA admin), `infra/` (docker-compose, migrations, seed scripts), `docs/`
2. A single root command (e.g., `make dev` or `pnpm dev`/`npm run dev`) SHALL start PostgreSQL + Redis in docker, run backend dev server with live reload, and frontend dev servers with HMR; inter-service URLs SHALL be configured via `.env.example` + local `.env` files that are gitignored
3. Backend migrations (Flyway/Liquibase) SHALL run automatically on local start; a seed command SHALL populate dev data: demo users (UNVERIFIED, BASIC, VERIFIED tiers), demo merchant, some sample transactions, so the UI is visually populated on first run
4. Shared scripts SHALL cover: lint (backend + frontend), test (unit, integration, e2e via Playwright/Cypress baseline), build all docker images, generate typescript client from OpenAPI, format code

### Requirement 40: End-to-End Tests Covering Critical User Journeys

**User Story:** As a QA/engineer, I want automated e2e tests covering the most critical flows, so that regressions in the frontend-backend integration are caught before release.

#### Acceptance Criteria

1. THE System SHALL include an e2e test suite (Playwright as baseline, or Cypress) running against a docker-compose full stack with seeded data
2. Critical journeys SHALL be covered at minimum with happy-path + key error cases: signup/login, verify email → complete tier-1 KYC, top-up flow with mocked gateway, P2P transfer between two seeded users, create payment request as merchant → pay as customer → verify paid state, subscribe to a plan → scheduler fires once → verify charged state, user requests withdrawal → backoffice marks payout completed → verify user sees COMPLETED state
3. E2E tests SHALL run in CI on pull requests against an ephemeral full environment; failures SHALL attach screenshots, traces, and console logs to the CI run for debugging
4. Visual regression testing (optional but recommended): screenshots of key pages (dashboard home, transaction detail, payment request QR view, merchant plans, admin user detail) SHALL be compared on PRs to catch unintended UI changes
