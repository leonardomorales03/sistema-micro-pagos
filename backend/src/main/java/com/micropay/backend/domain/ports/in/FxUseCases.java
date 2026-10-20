package com.micropay.backend.domain.ports.in;

import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.FxQuote;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;

/**
 * UseCases IN de multi-moneda / FX (T26).
 * Implementado en Application Layer (FxService.java).
 * <p>
 * Toda la feature queda gobernada por el flag {@code micropay.multicurrency.enabled}
 * (desactivado por defecto): con el flag OFF el sistema se comporta exactamente igual que antes.
 */
public interface FxUseCases {

    /** Cotización entre dos monedas (delega en {@code FxRatePort}). */
    FxQuote getQuote(Currency base, Currency quote);

    /**
     * Convierte {@code amount} (en la moneda del wallet origen) a {@code targetCurrency},
     * acreditando el resultado en el wallet del mismo usuario en esa moneda.
     * Sólo disponible con el flag multi-moneda activo.
     */
    Transaction convert(WalletId fromWalletId, Currency targetCurrency, Money amount, UserId initiatedBy);
}
