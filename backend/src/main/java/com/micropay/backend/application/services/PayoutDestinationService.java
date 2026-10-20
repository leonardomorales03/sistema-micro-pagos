package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.PayoutDestination;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.ports.out.PayoutDestinationRepository;
import com.micropay.backend.domain.valueobjects.PayoutDestinationId;
import com.micropay.backend.domain.valueobjects.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Casos de uso CRUD de PayoutDestinations (cuentas externas destino de retiros).
 *
 * SEGURIDAD: el número de cuenta completo NUNCA se persiste; sólo se guarda el
 * enmascarado ("**** **** **** 1234") + los últimos 4 dígitos.
 *
 * REGLAS:
 *   - Máximo 10 destinos por usuario.
 *   - Sólo un destino por usuario puede ser default.
 *   - Sandbox: el destino se auto-verifica al crearse (verified=true).
 */
@Service
@Transactional(readOnly = true)
public class PayoutDestinationService {

    private static final Logger log = LoggerFactory.getLogger(PayoutDestinationService.class);
    private static final int MAX_DESTINATIONS_PER_USER = 10;

    private final PayoutDestinationRepository destinations;

    public PayoutDestinationService(PayoutDestinationRepository destinations) {
        this.destinations = destinations;
    }

    @Transactional(rollbackFor = Exception.class)
    public PayoutDestination create(UserId userId,
                                    String rawAccountNumber,
                                    String bankName,
                                    String accountType,
                                    String holderName,
                                    String country,
                                    String currency,
                                    boolean makeDefault) {
        if (rawAccountNumber == null || rawAccountNumber.isBlank()) {
            throw new BusinessRuleViolationException("PAYOUT_ACCOUNT_REQUIRED",
                    "Número de cuenta es requerido");
        }
        long existing = destinations.countByUser(userId);
        if (existing >= MAX_DESTINATIONS_PER_USER) {
            throw new BusinessRuleViolationException("PAYOUT_DESTINATION_LIMIT_REACHED",
                    "Máximo %d destinos por usuario".formatted(MAX_DESTINATIONS_PER_USER));
        }

        String last4 = PayoutDestination.last4Of(rawAccountNumber);
        String masked = PayoutDestination.mask(rawAccountNumber, last4);

        boolean isDuplicate = destinations.listByUser(userId).stream()
                .anyMatch(d -> d.accountNumberMasked().equals(masked));
        if (isDuplicate) {
            throw new BusinessRuleViolationException("PAYOUT_DESTINATION_DUPLICATE",
                    "Ya existe un destino con la misma cuenta enmascarada " + masked);
        }

        String label = buildLabel(bankName, last4, accountType);
        boolean firstForUser = existing == 0;

        PayoutDestination dest = PayoutDestination.builder()
                .userId(userId)
                .type(PayoutDestination.Type.BANK_ACCOUNT)
                .maskedLabel(label)
                .bankName(bankName)
                .accountType(accountType)
                .accountNumberMasked(masked)
                .last4(last4)
                .holderName(holderName)
                .country(country)
                .currency(currency)
                .isDefault(makeDefault || firstForUser)
                .verified(true) // sandbox: auto-verificado
                .build();

        if (dest.isDefault()) {
            clearOtherDefaults(userId, dest.id());
        }
        PayoutDestination saved = destinations.save(dest);
        log.info("[PAYOUT DEST] Creado id={} user={} label={} default={}",
                saved.id(), userId, saved.maskedLabel(), saved.isDefault());
        return saved;
    }

    public List<PayoutDestination> listByUser(UserId userId) {
        return destinations.listByUser(userId);
    }

    public Optional<PayoutDestination> findById(PayoutDestinationId id) { return destinations.findById(id); }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UserId userId, PayoutDestinationId id) {
        PayoutDestination dest = requireOwned(userId, id);
        if (dest.isDefault()) {
            throw new BusinessRuleViolationException("PAYOUT_DESTINATION_DEFAULT_DELETE",
                    "No se puede eliminar el destino por defecto; designe otro primero");
        }
        destinations.deleteById(id);
        log.info("[PAYOUT DEST] Eliminado id={} user={}", id, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PayoutDestination setDefault(UserId userId, PayoutDestinationId id) {
        PayoutDestination dest = requireOwned(userId, id);
        if (dest.isDefault()) {
            return dest;
        }
        clearOtherDefaults(userId, id);
        dest.setDefault(true);
        PayoutDestination saved = destinations.save(dest);
        log.info("[PAYOUT DEST] Default actualizado id={} user={}", id, userId);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public PayoutDestination markVerified(UserId userId, PayoutDestinationId id) {
        PayoutDestination dest = requireOwned(userId, id);
        dest.markVerified();
        return destinations.save(dest);
    }

    // ────────────────── helpers ──────────────────

    private PayoutDestination requireOwned(UserId userId, PayoutDestinationId id) {
        PayoutDestination dest = destinations.findById(id)
                .orElseThrow(() -> new BusinessRuleViolationException("PAYOUT_DESTINATION_NOT_FOUND",
                        "PayoutDestination " + id + " no encontrado"));
        if (!dest.userId().equals(userId)) {
            throw new BusinessRuleViolationException("PAYOUT_DESTINATION_NOT_OWNED",
                    "PayoutDestination %s no pertenece al usuario %s".formatted(id, userId));
        }
        return dest;
    }

    private void clearOtherDefaults(UserId userId, PayoutDestinationId keepId) {
        destinations.findDefaultByUser(userId).ifPresent(current -> {
            if (!current.id().equals(keepId)) {
                current.setDefault(false);
                destinations.save(current);
            }
        });
    }

    private static String buildLabel(String bankName, String last4, String accountType) {
        String bank = (bankName == null || bankName.isBlank()) ? "Cuenta" : bankName;
        String typePart = (accountType == null || accountType.isBlank()) ? "" : " " + accountType;
        String tail = (last4 == null || last4.isBlank()) ? "" : " ****" + last4;
        return (bank + typePart + tail).trim();
    }
}
