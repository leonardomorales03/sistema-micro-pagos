package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;

import java.util.List;
import java.util.Optional;

/**
 * Port OUT — Repositorio Wallets.
 * Implementado por Infrastructure Adapter → PostgresWalletRepository
 */
public interface WalletRepository {

    Wallet save(Wallet wallet);

    Optional<Wallet> findById(WalletId id);

    Optional<Wallet> findByUserAndCurrency(UserId userId, Currency currency);

    List<Wallet> listByUser(UserId userId);

    boolean exists(WalletId id);
}
