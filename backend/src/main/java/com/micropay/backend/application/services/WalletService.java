package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.exceptions.WalletNotFoundException;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Casos de uso específicos Wallet (consulta y operaciones admin: freeze/close).
 * Operaciones críticas (transfer/createWallet) están en TransactionService.
 */
@Service
@Transactional(readOnly = true)
public class WalletService {

    private final WalletRepository wallets;

    public WalletService(WalletRepository wallets) {
        this.wallets = wallets;
    }

    public Optional<Wallet> findById(WalletId id) { return wallets.findById(id); }

    public Wallet getByIdOrThrow(WalletId id) {
        return wallets.findById(id).orElseThrow(() -> new WalletNotFoundException(id.uuid()));
    }

    public List<Wallet> listByUser(UserId userId) {
        return wallets.listByUser(userId);
    }

    public Optional<Wallet> findByUserAndCurrency(UserId userId, Currency currency) {
        return wallets.findByUserAndCurrency(userId, currency);
    }

    @Transactional
    public Wallet freeze(WalletId id) {
        Wallet w = getByIdOrThrow(id);
        w.freeze();
        return wallets.save(w);
    }

    @Transactional
    public Wallet close(WalletId id) {
        Wallet w = getByIdOrThrow(id);
        w.close();
        return wallets.save(w);
    }

    @Transactional
    public Wallet activate(WalletId id) {
        Wallet w = getByIdOrThrow(id);
        w.activate();
        return wallets.save(w);
    }
}
