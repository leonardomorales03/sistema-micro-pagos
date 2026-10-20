package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.AuditEvent;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.entities.UserProfile;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.exceptions.WalletNotFoundException;
import com.micropay.backend.domain.exceptions.UserNotFoundException;
import com.micropay.backend.domain.ports.in.AdminUseCases;
import com.micropay.backend.domain.ports.out.AuditEventRepository;
import com.micropay.backend.domain.ports.out.PayoutDestinationRepository;
import com.micropay.backend.domain.ports.out.TransactionRepository;
import com.micropay.backend.domain.ports.out.UserProfileRepository;
import com.micropay.backend.domain.ports.out.UserRepository;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.domain.valueobjects.TransactionStatus;
import com.micropay.backend.domain.valueobjects.TransactionType;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.KycDocumentJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.KycDocumentJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AdminService (T24) — orquesta el Backoffice: RBAC, ajustes manuales, freeze/unfreeze,
 * cola KYC y consultas de usuarios/wallets/transacciones.
 * <p>
 * TODA mutación registra un {@link AuditEvent} (Property 31 — append-only).
 */
@Service
@Transactional(readOnly = true)
public class AdminService implements AdminUseCases {

    /** Roles administrables vía {@code grantRoles} (se persisten con prefijo ROLE_). */
    private static final Set<String> ALLOWED_ROLES =
            Set.of("SUPPORT", "COMPLIANCE", "FINANCE", "SUPER_ADMIN", "ADMIN", "USER");

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final WalletRepository wallets;
    private final TransactionRepository transactions;
    private final PayoutDestinationRepository payoutDestinations;
    private final AuditEventRepository audit;
    private final KycDocumentJpaRepository kycDocs;

    public AdminService(UserRepository users,
                        UserProfileRepository profiles,
                        WalletRepository wallets,
                        TransactionRepository transactions,
                        PayoutDestinationRepository payoutDestinations,
                        AuditEventRepository audit,
                        KycDocumentJpaRepository kycDocs) {
        this.users = users;
        this.profiles = profiles;
        this.wallets = wallets;
        this.transactions = transactions;
        this.payoutDestinations = payoutDestinations;
        this.audit = audit;
        this.kycDocs = kycDocs;
    }

    // ───────────────────────────── Consultas ─────────────────────────────

    @Override
    public List<User> searchUsers(String q, int page, int pageSize) {
        return users.search(q, Math.max(0, page), Math.min(100, Math.max(1, pageSize)));
    }

    @Override
    public UserDetail getUserDetail(UUID userId) {
        User u = users.findById(UserId.from(userId))
                .orElseThrow(() -> new UserNotFoundException(userId));
        UserProfile profile = profiles.findByUserId(u.id()).orElse(null);
        List<Wallet> userWallets = wallets.listByUser(u.id());

        List<Transaction> recent = new ArrayList<>();
        List<Transaction> adjustments = new ArrayList<>();
        for (Wallet w : userWallets) {
            List<Transaction> txs = transactions.listByWallet(w.id(), 0, 20);
            recent.addAll(txs);
            for (Transaction t : txs) {
                if (t.type() == TransactionType.ADJUSTMENT) adjustments.add(t);
            }
        }
        recent.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        adjustments.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));

        List<AuditEvent> trail = audit.list("USER", userId, null, 0, 50);
        return new UserDetail(
                u,
                profile,
                userWallets,
                recent,
                payoutDestinations.listByUser(u.id()),
                adjustments,
                trail
        );
    }

    @Override
    public WalletDetail getWalletDetail(UUID walletId) {
        Wallet w = wallets.findById(WalletId.from(walletId))
                .orElseThrow(() -> new WalletNotFoundException(walletId));
        return new WalletDetail(w, transactions.listByWallet(w.id(), 0, 50));
    }

    @Override
    public List<Transaction> listTransactions(TransactionFilter filter) {
        TransactionFilter f = filter == null
                ? new TransactionFilter(null, null, null, null, null, null, 0, 20)
                : filter;
        return transactions.search(f.walletId(), f.type(), f.status(), f.currency(),
                f.from(), f.to(), f.page(), f.pageSize());
    }

    @Override
    public List<KycQueueItem> listKycQueue(int page, int pageSize) {
        int p = Math.max(0, page);
        int size = Math.min(100, Math.max(1, pageSize));
        return kycDocs.findByStatusOrderBySubmittedAtAsc(KycDocumentJpa.KycStatus.PENDING, PageRequest.of(p, size))
                .stream()
                .map(this::toQueueItem)
                .toList();
    }

    // ───────────────────────────── Mutaciones ─────────────────────────────

    @Override
    @Transactional
    public Wallet adjustWallet(UUID walletId, Money amount, boolean isCredit, String reason, AdminContext ctx) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleViolationException("ADJUSTMENT_REASON_REQUIRED",
                    "El ajuste manual requiere un motivo (reason) obligatorio");
        }
        if (amount == null) {
            throw new BusinessRuleViolationException("ADJUSTMENT_AMOUNT_REQUIRED",
                    "El ajuste manual requiere un monto");
        }
        Wallet wallet = wallets.findById(WalletId.from(walletId))
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        // Aplica el movimiento al saldo (credit acepta ACTIVE/FROZEN/UNDER_REVIEW; debit exige ACTIVE).
        if (isCredit) {
            wallet.credit(amount, "admin.adjust.credit");
        } else {
            wallet.debit(amount, "admin.adjust.debit");
        }
        Wallet saved = wallets.save(wallet);

        // Transacción ADJUSTMENT + su AccountMove (una sola línea: +/- amount).
        TransactionId txId = TransactionId.generate();
        Money signedMove = isCredit ? amount : amount.multiply(BigDecimal.valueOf(-1));
        Transaction tx = Transaction.builder()
                .id(txId)
                .type(TransactionType.ADJUSTMENT)
                .grossAmount(amount)
                .netAmount(amount)
                .feeAmount(Money.zero(amount.currency()))
                .status(TransactionStatus.COMPLETED)
                .walletFromId(isCredit ? null : saved.id())
                .walletToId(isCredit ? saved.id() : null)
                .createdBy(ctx != null ? ctx.adminUserId() : null)
                .note(reason)
                .accountMoves(List.of(new AccountMove(
                        UUID.randomUUID(),
                        saved.id(),
                        txId,
                        signedMove,
                        saved.balance(),
                        Instant.now())))
                .build();
        transactions.save(tx);

        Map<String, Object> metadata = Map.of(
                "amount", amount.amount(),
                "currency", amount.currency().isoCode(),
                "direction", isCredit ? "CREDIT" : "DEBIT",
                "reason", reason,
                "txId", tx.id().uuid().toString()
        );
        record(ctx, "WALLET_ADJUSTMENT", "WALLET", walletId, metadata);
        return saved;
    }

    @Override
    @Transactional
    public Wallet freezeWallet(UUID walletId, String reason, AdminContext ctx) {
        Wallet wallet = wallets.findById(WalletId.from(walletId))
                .orElseThrow(() -> new WalletNotFoundException(walletId));
        wallet.markUnderReview();
        Wallet saved = wallets.save(wallet);
        record(ctx, "WALLET_FREEZE", "WALLET", walletId,
                Map.of("reason", reason != null ? reason : "", "status", saved.status().name()));
        return saved;
    }

    @Override
    @Transactional
    public Wallet unfreezeWallet(UUID walletId, String reason, AdminContext ctx) {
        Wallet wallet = wallets.findById(WalletId.from(walletId))
                .orElseThrow(() -> new WalletNotFoundException(walletId));
        wallet.unfreeze();
        Wallet saved = wallets.save(wallet);
        record(ctx, "WALLET_UNFREEZE", "WALLET", walletId,
                Map.of("reason", reason != null ? reason : "", "status", saved.status().name()));
        return saved;
    }

    @Override
    @Transactional
    public KycQueueItem reviewKyc(UUID documentId, boolean approve, String reason, AdminContext ctx) {
        KycDocumentJpa doc = kycDocs.findById(documentId)
                .orElseThrow(() -> new BusinessRuleViolationException("KYC_DOCUMENT_NOT_FOUND",
                        "Documento KYC no encontrado: " + documentId));

        if (approve) {
            doc.setStatus(KycDocumentJpa.KycStatus.APPROVED);
            doc.setRejectionReason(null);
        } else {
            if (reason == null || reason.isBlank()) {
                throw new BusinessRuleViolationException("KYC_REJECTION_REASON_REQUIRED",
                        "El rechazo KYC requiere un motivo (reason) obligatorio");
            }
            doc.setStatus(KycDocumentJpa.KycStatus.REJECTED);
            doc.setRejectionReason(reason);
        }
        doc.setReviewedAt(Instant.now());
        doc.setReviewedBy(ctx != null ? ctx.adminUserId() : null);
        KycDocumentJpa saved = kycDocs.save(doc);

        record(ctx, approve ? "KYC_APPROVE" : "KYC_REJECT", "KYC_DOCUMENT", documentId,
                Map.of("userId", saved.getUserId().toString(),
                        "status", saved.getStatus().name(),
                        "reason", reason != null ? reason : ""));
        return toQueueItem(saved);
    }

    @Override
    @Transactional
    public User grantRoles(UUID userId, List<String> roles, AdminContext ctx) {
        User user = users.findById(UserId.from(userId))
                .orElseThrow(() -> new UserNotFoundException(userId));

        List<String> normalized = new ArrayList<>();
        if (roles != null) {
            for (String r : roles) {
                if (r == null || r.isBlank()) continue;
                String up = r.trim().toUpperCase().replace("ROLE_", "");
                if (!ALLOWED_ROLES.contains(up)) {
                    throw new BusinessRuleViolationException("ROLE_NOT_ALLOWED",
                            "Rol no permitido: " + r + ". Permitidos: " + ALLOWED_ROLES);
                }
                normalized.add(up);
            }
        }
        user.replaceAdminRoles(normalized);
        User saved = users.save(user);

        record(ctx, "ROLE_GRANT", "USER", userId,
                Map.of("roles", saved.roles()));
        return saved;
    }

    // ───────────────────────────── Helpers ─────────────────────────────

    private void record(AdminContext ctx, String action, String entityType, UUID entityId,
                        Map<String, Object> metadata) {
        audit.record(AuditEvent.of(
                null,
                ctx != null ? ctx.adminUserId() : null,
                action,
                entityType,
                entityId,
                null,
                metadata,
                ctx != null ? ctx.ip() : null,
                ctx != null ? ctx.userAgent() : null,
                null
        ));
    }

    private KycQueueItem toQueueItem(KycDocumentJpa d) {
        if (d == null) return null;
        return new KycQueueItem(
                d.getId(),
                d.getUserId(),
                d.getType(),
                d.getNumber(),
                d.getCountry(),
                d.getStatus() != null ? d.getStatus().name() : null,
                d.getRejectionReason(),
                d.getSubmittedAt(),
                d.getReviewedAt(),
                d.getReviewedBy()
        );
    }
}
