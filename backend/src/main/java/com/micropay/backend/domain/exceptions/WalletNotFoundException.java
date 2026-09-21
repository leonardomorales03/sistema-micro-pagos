package com.micropay.backend.domain.exceptions;

import java.util.UUID;

public final class WalletNotFoundException extends DomainException {

    public WalletNotFoundException(UUID walletId) {
        super("WALLET_NOT_FOUND", "Wallet con id '%s' no existe".formatted(walletId));
    }

    public WalletNotFoundException(String ownerAndCurrency) {
        super("WALLET_NOT_FOUND", "Wallet no encontrada: %s".formatted(ownerAndCurrency));
    }
}
