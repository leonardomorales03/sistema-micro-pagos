package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.valueobjects.ShortCode;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.domain.valueobjects.TransactionStatus;
import com.micropay.backend.domain.valueobjects.WalletId;

import java.util.List;
import java.util.Optional;

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

    List<AccountMove> listAccountMoves(TransactionId transactionId);

    /** Actualiza únicamente el status de una transacción (sin tocar sus AccountMove). */
    void updateStatus(TransactionId id, TransactionStatus status);
}
