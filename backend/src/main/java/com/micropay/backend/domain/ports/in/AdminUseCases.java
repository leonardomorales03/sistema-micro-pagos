package com.micropay.backend.domain.ports.in;

import com.micropay.backend.domain.entities.AuditEvent;
import com.micropay.backend.domain.entities.PayoutDestination;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.entities.UserProfile;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.valueobjects.Money;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * UseCases IN del Backoffice Admin (T24 / Requisitos 24.x, 26.x).
 * Implementado en Application Layer → AdminService.
 * <p>
 * Todas las mutaciones registran un {@code AuditEvent} (Property 31).
 */
public interface AdminUseCases {

    /** Contexto de auditoría propagado desde el controller (actor + ip + user-agent). */
    record AdminContext(UUID adminUserId, String ip, String userAgent) {}

    /** Filtros de búsqueda avanzada de transacciones (todos opcionales). */
    record TransactionFilter(UUID walletId,
                             String type,
                             String status,
                             String currency,
                             Instant from,
                             Instant to,
                             int page,
                             int pageSize) {}

    /** Ítem del queue KYC pendiente de revisión. */
    record KycQueueItem(UUID documentId,
                        UUID userId,
                        String type,
                        String number,
                        String country,
                        String status,
                        String rejectionReason,
                        Instant submittedAt,
                        Instant reviewedAt,
                        UUID reviewedBy) {}

    /** Vista completa de un usuario para el backoffice. */
    record UserDetail(User user,
                      UserProfile profile,
                      List<Wallet> wallets,
                      List<Transaction> recentTransactions,
                      List<PayoutDestination> payoutDestinations,
                      List<Transaction> adjustments,
                      List<AuditEvent> auditTrail) {}

    /** Vista de wallet + su historial de transacciones. */
    record WalletDetail(Wallet wallet, List<Transaction> transactions) {}

    // ─────────────── Consultas ───────────────

    List<User> searchUsers(String q, int page, int pageSize);

    UserDetail getUserDetail(UUID userId);

    WalletDetail getWalletDetail(UUID walletId);

    List<Transaction> listTransactions(TransactionFilter filter);

    List<KycQueueItem> listKycQueue(int page, int pageSize);

    // ─────────────── Mutaciones (auditadas) ───────────────

    Wallet adjustWallet(UUID walletId, Money amount, boolean isCredit, String reason, AdminContext ctx);

    Wallet freezeWallet(UUID walletId, String reason, AdminContext ctx);

    Wallet unfreezeWallet(UUID walletId, String reason, AdminContext ctx);

    KycQueueItem reviewKyc(UUID documentId, boolean approve, String reason, AdminContext ctx);

    User grantRoles(UUID userId, List<String> roles, AdminContext ctx);
}
