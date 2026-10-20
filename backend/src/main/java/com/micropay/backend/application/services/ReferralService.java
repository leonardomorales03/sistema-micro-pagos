package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.AuditEvent;
import com.micropay.backend.domain.entities.Referral;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.exceptions.UserNotFoundException;
import com.micropay.backend.domain.ports.in.ReferralUseCases;
import com.micropay.backend.domain.ports.out.AuditEventRepository;
import com.micropay.backend.domain.ports.out.NotificationAdapter;
import com.micropay.backend.domain.ports.out.ReferralRepository;
import com.micropay.backend.domain.ports.out.TransactionRepository;
import com.micropay.backend.domain.ports.out.UserProfileRepository;
import com.micropay.backend.domain.ports.out.UserRepository;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.KycLevel;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.TransactionStatus;
import com.micropay.backend.domain.valueobjects.TransactionType;
import com.micropay.backend.domain.valueobjects.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso Referrals & Rewards (T27 / Requisito 31).
 * <p>
 * Reconciliación con la config EXISTENTE ({@code micropay.referrals.*}): la "qualifying action"
 * configurada es un RETIRO del invitado ≥ {@code withdrawal-threshold-amount} en su moneda; al
 * completarse, el referrer recibe un bono = {@code reward-percent × monto cualificante}.
 * <p>
 * Puntos clave:
 * <ul>
 *   <li><b>Atribución</b> ({@link #linkOnSignup}): idempotente, se invoca desde el signup.</li>
 *   <li><b>Pago del bono</b> ({@link #payQualifyingBonus}): idempotente (lock pesimista + guarda
 *       de estado PENDING + índice único del par); crédito atómico con 1 AccountMove +
 *       Transaction {@code REFERRAL_REWARD} (el design lo llama REFERRAL_BONUS) + auditoría + notificación.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ReferralService implements ReferralUseCases {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);

    private final ReferralRepository referrals;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final WalletRepository wallets;
    private final TransactionRepository transactions;
    private final AuditEventRepository audit;
    private final NotificationAdapter notifications;

    @Value("${micropay.referrals.enabled:true}")
    private boolean enabled;

    @Value("${micropay.referrals.reward-percent:0.05}")
    private BigDecimal rewardPercent;

    @Value("${micropay.referrals.withdrawal-threshold-amount:10.00}")
    private BigDecimal thresholdAmount;

    @Value("${micropay.referrals.withdrawal-threshold-currency:USD}")
    private String thresholdCurrency;

    @Value("${micropay.referrals.min-kyc-level:LEVEL_1}")
    private String minKycLevel;

    @Value("${micropay.referrals.invite-base-url:https://app.micropay.example/register}")
    private String inviteBaseUrl;

    public ReferralService(ReferralRepository referrals,
                           UserRepository users,
                           UserProfileRepository profiles,
                           WalletRepository wallets,
                           TransactionRepository transactions,
                           AuditEventRepository audit,
                           NotificationAdapter notifications) {
        this.referrals = referrals;
        this.users = users;
        this.profiles = profiles;
        this.wallets = wallets;
        this.transactions = transactions;
        this.audit = audit;
        this.notifications = notifications;
    }

    // ────────────────── 1. Atribución al signup ──────────────────

    /**
     * Crea la fila {@code referrals} para el par (referrer, invitee) con status PENDING.
     * IDEMPOTENTE y TOLERANTE A FALLOS: no lanza si ya existe o si algo falla (devuelve null).
     */
    @Override
    @Transactional
    public Referral linkOnSignup(UUID referrerId, UUID inviteeId) {
        if (referrerId == null || inviteeId == null) return null;
        if (referrerId.equals(inviteeId)) return null;
        try {
            Optional<Referral> existing = referrals.findByInviteeId(inviteeId);
            if (existing.isPresent()) {
                return existing.get();
            }
            if (referrals.existsByReferrerAndInvitee(referrerId, inviteeId)) {
                return referrals.findByInviteeId(inviteeId).orElse(null);
            }
            User referrer = users.findById(UserId.from(referrerId)).orElse(null);
            if (referrer == null) {
                log.warn("[REFERRAL] Referrer {} no encontrado al atribuir invitee={}", referrerId, inviteeId);
                return null;
            }
            Referral created = referrals.save(Referral.pending(referrer.referralCode(), referrerId, inviteeId));
            log.info("[REFERRAL] Atribución creada referral={} referrer={} invitee={}",
                    created.id(), referrerId, inviteeId);
            return created;
        } catch (Exception e) {
            // Nunca romper el signup por el tracking de referidos.
            log.warn("[REFERRAL] No se pudo atribuir referrer={} invitee={}: {}",
                    referrerId, inviteeId, e.getMessage());
            return null;
        }
    }

    // ────────────────── 2. Pago del bono (acción cualificante) ──────────────────

    /**
     * Paga el bono al referrer cuando el invitee cumple la acción cualificante.
     * REQUIRES_NEW: puede invocarse desde un hook {@code afterCommit} del retiro.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Optional<Referral> payQualifyingBonus(UUID inviteeUserId, Money qualifyingAmount) {
        if (!enabled) {
            log.debug("[REFERRAL] Programa deshabilitado (micropay.referrals.enabled=false)");
            return Optional.empty();
        }
        if (inviteeUserId == null || qualifyingAmount == null || !qualifyingAmount.isPositive()) {
            return Optional.empty();
        }

        Currency cur = qualifyingAmount.currency();
        Currency thresholdCur;
        try {
            thresholdCur = Currency.valueOf(thresholdCurrency.trim().toUpperCase());
        } catch (Exception e) {
            thresholdCur = Currency.USD;
        }
        if (cur != thresholdCur) {
            // El umbral está definido en una moneda concreta; la acción cualificante debe estar en esa moneda.
            return Optional.empty();
        }
        Money threshold = Money.of(thresholdAmount, cur);
        if (!qualifyingAmount.isGreaterThanOrEqual(threshold)) {
            return Optional.empty();
        }

        // Gate KYC del invitado
        User invitee = users.findById(UserId.from(inviteeUserId)).orElse(null);
        if (invitee == null) {
            log.warn("[REFERRAL] Invitee {} no encontrado para bono", inviteeUserId);
            return Optional.empty();
        }
        KycLevel required;
        try {
            required = KycLevel.valueOf(minKycLevel.trim().toUpperCase());
        } catch (Exception e) {
            required = KycLevel.LEVEL_1;
        }
        if (!invitee.kycLevel().atLeast(required)) {
            log.debug("[REFERRAL] Invitee {} KYC {} < requerido {} — sin bono",
                    inviteeUserId, invitee.kycLevel(), required);
            return Optional.empty();
        }

        // Lock pesimista del referral → serializa intentos concurrentes (idempotencia)
        Referral referral = referrals.lockByInviteeId(inviteeUserId).orElse(null);
        if (referral == null) {
            return Optional.empty();
        }
        if (referral.status() != Referral.ReferralStatus.PENDING) {
            log.info("[REFERRAL] Idempotente: referral {} ya está {} (invitee={})",
                    referral.id(), referral.status(), inviteeUserId);
            return Optional.empty();
        }

        Wallet referrerWallet = wallets.findByUserAndCurrency(UserId.from(referral.referrerId()), cur).orElse(null);
        if (referrerWallet == null) {
            log.warn("[REFERRAL] Referrer {} no tiene wallet {} — bono no pagado (invitee={})",
                    referral.referrerId(), cur.isoCode(), inviteeUserId);
            return Optional.empty();
        }

        Money bonus = qualifyingAmount.multiply(rewardPercent);
        if (!bonus.isPositive()) {
            log.warn("[REFERRAL] Bono calculado no positivo ({}) para invitee={}", bonus, inviteeUserId);
            return Optional.empty();
        }

        // 1. Crédito atómico a la wallet del referrer
        Money balanceAfter = referrerWallet.credit(bonus, "referral bonus invitee=" + inviteeUserId);
        wallets.save(referrerWallet);

        // 2. Ledger: Transaction REFERRAL_REWARD + 1 AccountMove CRÉDITO (from=null, to=referrer)
        Transaction.Builder txB = Transaction.builder()
                .type(TransactionType.REFERRAL_REWARD)
                .walletFromId(null)
                .walletToId(referrerWallet.id())
                .grossAmount(bonus)
                .netAmount(bonus)
                .feeAmount(Money.zero(cur))
                .status(TransactionStatus.COMPLETED)
                .createdBy(null) // sistema
                .note("Referral bonus por referido " + inviteeUserId);
        Transaction tx = txB.build();
        List<AccountMove> moves = new ArrayList<>(1);
        moves.add(new AccountMove(UUID.randomUUID(), referrerWallet.id(), tx.id(), bonus, balanceAfter, Instant.now()));
        txB.accountMoves(moves);
        Transaction savedTx = transactions.save(txB.build());

        // 3. Marcar referral QUALIFIED
        Referral qualified = referral.qualified(bonus.amount(), savedTx.id().uuid(), Instant.now());
        referrals.save(qualified);

        // 4. Marcar el perfil del referrer
        try {
            profiles.findByUserId(UserId.from(referral.referrerId())).ifPresent(p -> {
                p.claimReferralBonusIfEligible();
                profiles.save(p);
            });
        } catch (Exception e) {
            log.warn("[REFERRAL] No se pudo marcar perfil referrer {}: {}", referral.referrerId(), e.getMessage());
        }

        // 5. Auditoría
        try {
            audit.record(AuditEvent.of(null, null, "REFERRAL_BONUS_PAID", "REFERRAL", qualified.id(), null,
                    Map.of("inviteeId", inviteeUserId.toString(),
                            "amount", bonus.amount().toPlainString(),
                            "currency", cur.isoCode(),
                            "txId", savedTx.id().uuid().toString()),
                    null, null, null));
        } catch (Exception e) {
            log.warn("[REFERRAL] No se pudo auditar REFERRAL_BONUS_PAID: {}", e.getMessage());
        }

        // 6. Notificación al referrer (after-commit, tolerante a fallos)
        registerNotification(UserId.from(referral.referrerId()), "REFERRAL_BONUS_PAID",
                Map.of("referral_id", qualified.id().toString(),
                        "invitee_id", inviteeUserId.toString(),
                        "bonus", bonus.toString(),
                        "tx_id", savedTx.id().uuid().toString()));

        log.info("[REFERRAL] ✅ Bono pagado referral={} referrer={} invitee={} bonus={} tx={}",
                qualified.id(), referral.referrerId(), inviteeUserId, bonus, savedTx.id());
        return Optional.of(qualified);
    }

    // ────────────────── 3. Consultas (dashboard) ──────────────────

    @Override
    public List<Referral> listMine(UUID userId) {
        if (userId == null) return List.of();
        return referrals.listByReferrer(userId, 0, 100);
    }

    @Override
    public Optional<ReferralSummary> referralSummary(UUID userId) {
        if (userId == null) return Optional.empty();
        User user = users.findById(UserId.from(userId))
                .orElseThrow(() -> new UserNotFoundException(userId));
        List<Referral> mine = referrals.listByReferrer(userId, 0, 100);
        long total = mine.size();
        long qualified = mine.stream().filter(r -> r.status() == Referral.ReferralStatus.QUALIFIED).count();
        long pending = mine.stream().filter(r -> r.status() == Referral.ReferralStatus.PENDING).count();
        BigDecimal bonusSum = mine.stream()
                .filter(r -> r.referrerBonus() != null)
                .map(Referral::referrerBonus)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String link = inviteBaseUrl + "?ref=" + user.referralCode();
        return Optional.of(new ReferralSummary(user.referralCode(), link, total, qualified, pending, bonusSum));
    }

    // ────────────────── Helpers ──────────────────

    private void registerNotification(UserId userId, String template, Map<String, Object> payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { trySend(userId, template, payload); }
            });
        } else {
            trySend(userId, template, payload);
        }
    }

    private void trySend(UserId userId, String template, Map<String, Object> payload) {
        try {
            notifications.send(new NotificationAdapter.NotificationRequest(
                    userId, NotificationAdapter.Channel.PUSH, template, payload));
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación {} a user {}: {}", template, userId, e.getMessage());
        }
    }
}
