package com.micropay.backend.domain.ports.in;

import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.valueobjects.*;

import java.util.List;
import java.util.Optional;

/**
 * UseCases IN relacionados a Transacciones P2P + Wallet.
 * Implementado en Application Layer (TransactionService.java).
 * Operaciones ACID: @Transactional REQUIRES_NEW en cada método.
 */
public interface TransactionUseCases {

    /** Transferencia P2P. Valida: saldo suficiente, wallets activas, monedas coinciden, RiskEngine velocity. */
    Transaction transferP2P(WalletId from, WalletId to, Money amount, UserId initiatedBy, String note);

    /** Crea una nueva Wallet para un usuario en la moneda especificada. */
    com.micropay.backend.domain.entities.Wallet createWallet(UserId userId, Currency currency, UserId createdBy);

    /** Lista transacciones de wallet (paginada). */
    List<Transaction> listTransactions(WalletId walletId, int page, int pageSize);

    Optional<Transaction> findById(TransactionId id);

    Optional<Transaction> findByShortCode(ShortCode shortCode);
}
