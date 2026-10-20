package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.WalletId;

/**
 * Port OUT — Lock distribuido wallet (Redis SETNX TTL 10s).
 * Evita race conditions en debit operations concurrentes.
 */
public interface WalletLockAdapter {

    /** Intenta adquirir lock. Retorna true si lo adquirió, false si está ocupado. */
    boolean tryLock(WalletId walletId);

    /** Libera lock manualmente (opcional; se libera solo por TTL). */
    void unlock(WalletId walletId);

    /** TTL del lock en segundos. No configurable para mantener consistencia. */
    int TTL_SECONDS = 10;
}
