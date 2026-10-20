package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.TransactionJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<TransactionJpa, UUID> {

    Optional<TransactionJpa> findByShortCode(String shortCode);

    Page<TransactionJpa> findByWalletFromIdOrWalletToIdOrderByCreatedAtDesc(UUID walletFromId, UUID walletToId, Pageable pageable);

    /** Búsqueda admin (T24) con filtros opcionales. */
    @Query("SELECT t FROM TransactionJpa t "
            + "WHERE (:walletId IS NULL OR t.walletFromId = :walletId OR t.walletToId = :walletId) "
            + "AND (:type IS NULL OR t.type = :type) "
            + "AND (:status IS NULL OR t.status = :status) "
            + "AND (:currency IS NULL OR t.currency = :currency) "
            + "AND (:from IS NULL OR t.createdAt >= :from) "
            + "AND (:to IS NULL OR t.createdAt <= :to) "
            + "ORDER BY t.createdAt DESC")
    Page<TransactionJpa> search(@Param("walletId") UUID walletId,
                                @Param("type") TransactionJpa.TxType type,
                                @Param("status") TransactionJpa.TxStatus status,
                                @Param("currency") String currency,
                                @Param("from") Instant from,
                                @Param("to") Instant to,
                                Pageable pageable);

    /**
     * Resumen fiscal (T25): totales COMPLETED agrupados por usuario (pata de crédito vía wallet),
     * tipo y moneda. Proyección: [userId, type, currency, sum(gross), sum(fee), sum(net), count].
     */
    @Query("SELECT w.userId, t.type, t.currency, SUM(t.grossAmount), SUM(t.feeAmount), SUM(t.netAmount), COUNT(t) "
            + "FROM TransactionJpa t, WalletJpa w "
            + "WHERE w.id = t.walletToId "
            + "AND t.status = :status "
            + "AND t.createdAt >= :from AND t.createdAt <= :to "
            + "GROUP BY w.userId, t.type, t.currency "
            + "ORDER BY w.userId, t.type")
    List<Object[]> annualTaxSummary(@Param("from") Instant from,
                                    @Param("to") Instant to,
                                    @Param("status") TransactionJpa.TxStatus status);

    /** Transacciones respaldadas por proveedor (con external_ref) en un rango — sandbox reconciliación. */
    @Query("SELECT t FROM TransactionJpa t "
            + "WHERE t.createdAt >= :from AND t.createdAt <= :to "
            + "AND t.externalRef IS NOT NULL "
            + "AND t.type IN :types "
            + "ORDER BY t.createdAt ASC")
    List<TransactionJpa> findProviderBacked(@Param("from") Instant from,
                                            @Param("to") Instant to,
                                            @Param("types") java.util.Collection<TransactionJpa.TxType> types);
}
