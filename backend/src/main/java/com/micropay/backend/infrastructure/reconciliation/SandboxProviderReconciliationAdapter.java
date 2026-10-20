package com.micropay.backend.infrastructure.reconciliation;

import com.micropay.backend.domain.ports.out.ProviderReconciliationPort;
import com.micropay.backend.domain.valueobjects.ProviderEntry;
import com.micropay.backend.infrastructure.persistence.jpa.entities.TransactionJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.TransactionJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * Adaptador SANDBOX de {@link ProviderReconciliationPort} (T25 / Requisito 28.3).
 * <p>
 * Deriva las entradas del "proveedor" a partir de las transacciones internas
 * respaldadas por proveedor (con {@code external_ref}), lo que permite ejecutar
 * el job de reconciliación end-to-end sin un proveedor real.
 * <p>
 * ⚠ En PRODUCCIÓN se sustituye por un adaptador que consuma el CSV export o la
 * API del proveedor (Stripe/PSE/…) — mismo puerto, distinta implementación. Las
 * transacciones internas sin {@code external_ref} (p. ej. ajustes) no aparecen
 * aquí y serán reportadas como {@code MISSING_AT_PROVIDER} en la reconciliación.
 */
@Component
public class SandboxProviderReconciliationAdapter implements ProviderReconciliationPort {

    private static final Logger log = LoggerFactory.getLogger(SandboxProviderReconciliationAdapter.class);

    private static final EnumSet<TransactionJpa.TxType> PROVIDER_TYPES = EnumSet.of(
            TransactionJpa.TxType.DEPOSIT_TOPUP,
            TransactionJpa.TxType.WITHDRAW,
            TransactionJpa.TxType.TRANSFER_P2P,
            TransactionJpa.TxType.PAYMENT_REQUEST,
            TransactionJpa.TxType.REFUND);

    private final TransactionJpaRepository transactions;

    public SandboxProviderReconciliationAdapter(TransactionJpaRepository transactions) {
        this.transactions = transactions;
    }

    @Override
    public List<ProviderEntry> fetchEntries(String provider, Instant periodStart, Instant periodEnd) {
        Instant from = periodStart != null ? periodStart : Instant.EPOCH;
        Instant to = periodEnd != null ? periodEnd : Instant.now();
        List<ProviderEntry> entries = transactions.findProviderBacked(from, to, PROVIDER_TYPES)
                .stream()
                .map(t -> new ProviderEntry(
                        t.getExternalRef(),
                        t.getGrossAmount(),
                        t.getCurrency(),
                        t.getType().name(),
                        t.getCreatedAt()))
                .toList();
        log.debug("[SANDBOX_RECONCILIATION] provider={} periodo=[{}..{}] → {} entradas simuladas",
                provider, from, to, entries.size());
        return entries;
    }
}
