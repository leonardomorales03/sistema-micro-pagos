package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.valueobjects.Money;

/**
 * Port OUT — RiskEngine velocity counters (Redis implementation).
 * Retorna true si la operación es permitida; false si debe bloquearse.
 * Cada check incrementa los counters.
 */
public interface RiskCheckAdapter {

    record RiskResult(boolean allowed, String blockedRule, String detail) {}

    RiskResult checkTransfer(com.micropay.backend.domain.valueobjects.UserId userId,
                             com.micropay.backend.domain.valueobjects.WalletId fromWalletId,
                             com.micropay.backend.domain.valueobjects.WalletId toWalletId,
                             Money amount,
                             String ipAddress);
}
