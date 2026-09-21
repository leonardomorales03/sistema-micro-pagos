package com.micropay.backend.domain.exceptions;

import java.util.UUID;

public final class TransactionNotFoundException extends DomainException {

    public TransactionNotFoundException(UUID txnId) {
        super("TRANSACTION_NOT_FOUND", "Transacción con id '%s' no existe".formatted(txnId));
    }

    public TransactionNotFoundException(String shortCode) {
        super("TRANSACTION_NOT_FOUND", "Transacción con shortCode '%s' no existe".formatted(shortCode));
    }
}
