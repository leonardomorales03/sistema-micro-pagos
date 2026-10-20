package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.PaymentRequest;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.exceptions.UserNotFoundException;
import com.micropay.backend.domain.exceptions.WalletNotFoundException;
import com.micropay.backend.domain.ports.out.NotificationAdapter;
import com.micropay.backend.domain.ports.out.PaymentRequestRepository;
import com.micropay.backend.domain.ports.out.TierLimitsPort;
import com.micropay.backend.domain.ports.out.TransactionRepository;
import com.micropay.backend.domain.ports.out.UserRepository;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.FeeOperation;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.PaymentRequestId;
import com.micropay.backend.domain.valueobjects.PaymentRequestStatus;
import com.micropay.backend.domain.valueobjects.ShortCode;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import com.micropay.backend.domain.valueobjects.WebhookEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Casos de uso Payment Requests (links/QR de comercio) — T19.
 *
 * Flujo:
 *  1. createRequest(merchant, wallet, amount, ...) → valida wallet del comercio (owner/ACTIVE/moneda),
 *     genera shortCode colisión-safe (6 chars), persiste PENDING y devuelve link corto + payload QR.
 *  2. pay(customer, requestId) → valida PENDING + no expirado + wallet del cliente (moneda) + límites,
 *     ejecuta la transferencia P2P atómica (TransactionService: locks + retry + doble entrada),
 *     marca PAID con el tx id, notifica a cliente y comercio y dispara webhook (best-effort).
 *     Idempotente a nivel request: si ya está PAID devuelve el recibo existente sin mover fondos.
 *
 * NOTA: el proyecto sólo define ROLE_USER/ROLE_ADMIN; no existe un rol MERCHANT todavía, por lo que
 * "comercio" se interpreta como cualquier usuario autenticado que acredita a su propia wallet.
 */
@Service
@Transactional(readOnly = true)
public class PaymentRequestService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestService.class);
    private static final int SHORT_CODE_MAX_ATTEMPTS = 10;
    private static final int MAX_DESCRIPTION_LEN = 255;
    private static final String DEFAULT_COUNTRY = "CO";

    private final PaymentRequestRepository paymentRequests;
    private final WalletRepository wallets;
    private final UserRepository users;
    private final TransactionRepository transactions;
    private final TransactionService transactionService;
    private final TierLimitsPort tierLimits;
    private final NotificationAdapter notifications;
    private final WebhookService webhooks;

    @Value("${app.payment-request.base-url:http://localhost:5173/pay/}")
    private String baseUrl;

    public PaymentRequestService(PaymentRequestRepository paymentRequests,
                                 WalletRepository wallets,
                                 UserRepository users,
                                 TransactionRepository transactions,
                                 TransactionService transactionService,
                                 TierLimitsPort tierLimits,
                                 NotificationAdapter notifications,
                                 WebhookService webhooks) {
        this.paymentRequests = paymentRequests;
        this.wallets = wallets;
        this.users = users;
        this.transactions = transactions;
        this.transactionService = transactionService;
        this.tierLimits = tierLimits;
        this.notifications = notifications;
        this.webhooks = webhooks;
    }

    // ────────────────── 1. Crear PaymentRequest (Requisitos 21.1, 21.2) ──────────────────

    @Transactional(rollbackFor = Exception.class)
    public PaymentRequest createRequest(UserId merchantId,
                                        WalletId merchantWalletId,
                                        Money amount,
                                        String description,
                                        String orderReference,
                                        Instant expiresAt,
                                        String webhookUrl) {
        if (amount == null || !amount.isPositive()) {
            throw new BusinessRuleViolationException("PAYMENT_REQUEST_AMOUNT_INVALID",
                    "PaymentRequest requiere monto > 0");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LEN) {
            throw new BusinessRuleViolationException("PAYMENT_REQUEST_DESCRIPTION_TOO_LONG",
                    "description excede %d caracteres".formatted(MAX_DESCRIPTION_LEN));
        }

        User merchant = users.findById(merchantId)
                .orElseThrow(() -> new UserNotFoundException(merchantId.uuid()));
        merchant.assertCanOperate("crear payment request");

        Wallet wallet = wallets.findById(merchantWalletId)
                .orElseThrow(() -> new WalletNotFoundException(merchantWalletId.uuid()));
        if (!wallet.userId().equals(merchantId)) {
            throw new BusinessRuleViolationException("WALLET_NOT_OWNED_BY_USER",
                    "Wallet %s no pertenece al comercio %s".formatted(merchantWalletId, merchantId));
        }
        if (wallet.status() != Wallet.Status.ACTIVE) {
            throw new BusinessRuleViolationException("WALLET_NOT_ACTIVE",
                    "Wallet %s estado %s no permite crear requests".formatted(merchantWalletId, wallet.status()));
        }
        if (!wallet.currency().equals(amount.currency())) {
            throw new BusinessRuleViolationException("CURRENCY_MISMATCH",
                    "Wallet currency=%s vs amount currency=%s".formatted(wallet.currency(), amount.currency()));
        }

        ShortCode shortCode = generateUniqueShortCode();

        PaymentRequest pr = PaymentRequest.builder()
                .merchantUserId(merchantId)
                .merchantWalletId(merchantWalletId)
                .amount(amount)
                .description(description)
                .orderReference(orderReference)
                .shortCode(shortCode)
                .webhookUrl(webhookUrl)
                .expiresAt(expiresAt)
                .status(PaymentRequestStatus.PENDING)
                .build();
        pr = paymentRequests.save(pr);

        log.info("[PAYMENT_REQUEST] Creado id={} merchant={} wallet={} amount={} shortCode={}",
                pr.id(), merchantId, merchantWalletId, amount, shortCode.value());

        PaymentRequest saved = pr;
        registerAfterCommit(() -> dispatchWebhook(saved, WebhookEventType.PAYMENT_REQUEST_CREATED));
        return saved;
    }

    private ShortCode generateUniqueShortCode() {
        for (int i = 0; i < SHORT_CODE_MAX_ATTEMPTS; i++) {
            ShortCode candidate = ShortCode.random();
            if (!paymentRequests.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new BusinessRuleViolationException("PAYMENT_REQUEST_SHORT_CODE_GENERATION_FAILED",
                "No fue posible generar un shortCode único tras %d intentos".formatted(SHORT_CODE_MAX_ATTEMPTS));
    }

    // ────────────────── 2. Pagar PaymentRequest (Requisitos 21.3–21.6) ──────────────────

    /** Resultado del pago: request + transacción P2P + si fue un replay idempotente. */
    public record PayResult(PaymentRequest request, Transaction transaction, boolean idempotentReplay) {}

    @Transactional(rollbackFor = Exception.class)
    public PayResult pay(UserId customerId, PaymentRequestId requestId) {
        PaymentRequest pr = paymentRequests.findById(requestId)
                .orElseThrow(() -> new BusinessRuleViolationException("PAYMENT_REQUEST_NOT_FOUND",
                        "PaymentRequest " + requestId + " no encontrado"));

        // Idempotencia a nivel request (Property 25): un segundo intento no vuelve a transferir.
        if (pr.status() == PaymentRequestStatus.PAID) {
            Transaction existing = pr.paidWithTxId() == null ? null
                    : transactions.findById(pr.paidWithTxId()).orElse(null);
            log.info("[PAYMENT_REQUEST] Idempotente: ya PAID id={} tx={}", pr.id(), pr.paidWithTxId());
            return new PayResult(pr, existing, true);
        }
        if (pr.status() != PaymentRequestStatus.PENDING) {
            throw new BusinessRuleViolationException("PAYMENT_REQUEST_NOT_PAYABLE",
                    "PaymentRequest %s no es pagable en estado %s".formatted(pr.id(), pr.status()));
        }
        if (pr.isExpiredAt(Instant.now())) {
            pr.markExpired();
            paymentRequests.save(pr);
            registerAfterCommit(() -> dispatchWebhook(pr, WebhookEventType.PAYMENT_REQUEST_EXPIRED));
            throw new BusinessRuleViolationException("PAYMENT_REQUEST_EXPIRED",
                    "PaymentRequest %s expiró el %s".formatted(pr.id(), pr.expiresAt()));
        }

        User customer = users.findById(customerId)
                .orElseThrow(() -> new UserNotFoundException(customerId.uuid()));
        customer.assertCanOperate("pago de payment request");

        if (pr.merchantUserId().equals(customerId)) {
            throw new BusinessRuleViolationException("PAYMENT_REQUEST_SELF_PAYMENT",
                    "El comercio no puede pagar su propio PaymentRequest " + pr.id());
        }

        Currency cur = pr.amount().currency();
        Wallet customerWallet = wallets.findByUserAndCurrency(customerId, cur)
                .orElseThrow(() -> new BusinessRuleViolationException("PAYMENT_REQUEST_NO_WALLET",
                        "El usuario %s no tiene wallet en %s para pagar".formatted(customerId, cur)));
        if (customerWallet.status() != Wallet.Status.ACTIVE) {
            throw new BusinessRuleViolationException("WALLET_NOT_ACTIVE",
                    "Wallet %s estado %s no permite pagos".formatted(customerWallet.id(), customerWallet.status()));
        }

        // Límites por tier (KYC + país + moneda)
        tierLimits.assertSingleTxAllowed(customer.kycLevel(), DEFAULT_COUNTRY, pr.amount());

        // Transferencia P2P atómica con MDR del comercio (FeeOperation.PAYMENT_REQUEST):
        // valida saldo (debit aplica BR), monedas, riesgo y ledger 2 movimientos.
        Transaction tx = transactionService.transferP2P(
                customerWallet.id(),
                pr.merchantWalletId(),
                pr.amount(),
                customerId,
                "PaymentRequest " + pr.shortCode().value(),
                FeeOperation.PAYMENT_REQUEST);

        pr.markPaid(tx.id());
        PaymentRequest saved = paymentRequests.save(pr);

        registerAfterCommit(() -> {
            notify(customerId, "PAYMENT_REQUEST_PAID", Map.of(
                    "payment_request_id", saved.id().toString(),
                    "short_code", saved.shortCode().value(),
                    "tx_id", tx.id().toString(),
                    "amount", saved.amount().toString()));
            notify(saved.merchantUserId(), "PAYMENT_REQUEST_RECEIVED", Map.of(
                    "payment_request_id", saved.id().toString(),
                    "short_code", saved.shortCode().value(),
                    "tx_id", tx.id().toString(),
                    "amount", saved.amount().toString(),
                    "payer_user_id", customerId.toString()));
            fireWebhookPaid(saved, tx);
        });

        log.info("[PAYMENT_REQUEST] ✅ PAID id={} tx={} customer={} merchant={} amount={}",
                saved.id(), tx.id(), customerId, saved.merchantUserId(), saved.amount());
        return new PayResult(saved, tx, false);
    }

    // ────────────────── 3. Consultas ──────────────────

    public Optional<PaymentRequest> findById(PaymentRequestId id) { return paymentRequests.findById(id); }

    public Optional<PaymentRequest> findByShortCode(ShortCode shortCode) { return paymentRequests.findByShortCode(shortCode); }

    public List<PaymentRequest> listByMerchant(UserId merchantId, int page, int pageSize) {
        return paymentRequests.listByMerchant(merchantId, page, pageSize);
    }

    /** Link corto público para compartir/pintar el QR. */
    public String shortUrl(PaymentRequest pr) {
        String base = baseUrl == null ? "" : baseUrl;
        if (!base.endsWith("/")) base = base + "/";
        return base + pr.shortCode().value();
    }

    /** Payload QR: URI de pago que los clientes escanean (frontend puede renderizar el QR). */
    public String qrPayload(PaymentRequest pr) {
        return shortUrl(pr);
    }

    // ────────────────── Helpers: notificación + webhook merchant ──────────────────

    private void notify(UserId userId, String template, Map<String,Object> payload) {
        try {
            notifications.send(new NotificationAdapter.NotificationRequest(
                    userId, NotificationAdapter.Channel.PUSH, template, payload));
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación {} a user {}: {}", template, userId, e.getMessage());
        }
    }

    /**
     * Webhook saliente al comercio (Requisitos 21.6, 25.4). Delega en {@link WebhookService}, que
     * resuelve el endpoint (config del comercio o URL por-request de T19), firma con HMAC-SHA256 y
     * registra el intento para reintentos. Nunca debe romper la transacción de negocio.
     */
    private void dispatchWebhook(PaymentRequest pr, WebhookEventType type) {
        dispatchWebhook(pr, type, null);
    }

    private void fireWebhookPaid(PaymentRequest pr, Transaction tx) {
        dispatchWebhook(pr, WebhookEventType.PAYMENT_REQUEST_PAID, tx);
    }

    private void dispatchWebhook(PaymentRequest pr, WebhookEventType type, Transaction tx) {
        try {
            Map<String,Object> data = new LinkedHashMap<>();
            data.put("payment_request_id", pr.id().toString());
            data.put("short_code", pr.shortCode().value());
            data.put("status", pr.status().name());
            data.put("amount", pr.amount().amount().toPlainString());
            data.put("currency", pr.amount().currency().isoCode());
            if (pr.orderReference() != null) data.put("order_reference", pr.orderReference());
            if (tx != null) data.put("transaction_id", tx.id().toString());
            data.put("occurred_at", Instant.now().toString());

            webhooks.dispatchTo(pr.merchantUserId(), type, pr.id().uuid(), "PAYMENT_REQUEST",
                    data, pr.webhookUrl());
        } catch (Exception e) {
            log.warn("[PAYMENT_REQUEST] Webhook {} de {} falló: {}", type, pr.id(), e.getMessage());
        }
    }

    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }
}
