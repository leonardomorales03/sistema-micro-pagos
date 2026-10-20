package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.Refund;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.RefundId;
import com.micropay.backend.domain.valueobjects.TransactionId;

import java.util.List;
import java.util.Optional;

/**
 * Port OUT — Repositorio de reembolsos (Requirement 23).
 * Implementado por Infrastructure Adapter → PostgresRefundRepository.
 */
public interface RefundRepository {

    Refund save(Refund refund);

    Optional<Refund> findById(RefundId id);

    List<Refund> listBySourceTransaction(TransactionId sourceTxId);

    /** Suma acumulada de reembolsos de una transacción origen (Requirement 23.4 / Property 26). */
    Money sumRefundedFor(TransactionId sourceTxId, Currency currency);
}
