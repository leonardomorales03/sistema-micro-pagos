package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.valueobjects.AnnualTaxLine;
import com.micropay.backend.domain.valueobjects.ShortCode;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.domain.valueobjects.TransactionStatus;
import com.micropay.backend.domain.valueobjects.WalletId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port OUT — Repositorio Transacciones + Ledger (AccountMove).
 * Implementado por Infrastructure Adapter → PostgresTransactionRepository
 */
public interface TransactionRepository {

    /** Salva Transaction + sus AccountMove (dentro de misma transacción). */
    Transaction save(Transaction transaction);

    Optional<Transaction> findById(TransactionId id);

    Optional<Transaction> findByShortCode(ShortCode shortCode);

    List<Transaction> listByWallet(WalletId walletId, int page, int pageSize);

    /**
     * Búsqueda admin (T24) con filtros opcionales (null = sin restricción), paginada.
     * Orden: createdAt DESC.
     */
    List<Transaction> search(UUID walletId,
                             String type,
                             String status,
                             String currency,
                             java.time.Instant from,
                             java.time.Instant to,
                             int page,
                             int pageSize);

    List<AccountMove> listAccountMoves(TransactionId transactionId);

    /**
     * Resumen fiscal (T25): totales COMPLETED agrupados por usuario (pata de crédito),
     * tipo de transacción y moneda, dentro del rango {@code [from, to]}. Orden: userId, type.
     */
    List<AnnualTaxLine> annualTaxSummary(Instant from, Instant to);

    /** Actualiza únicamente el status de una transacción (sin tocar sus AccountMove). */
    void updateStatus(TransactionId id, TransactionStatus status);
}
