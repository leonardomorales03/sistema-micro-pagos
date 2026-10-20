package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.FeeLedgerEntry;
import com.micropay.backend.domain.valueobjects.TransactionId;

import java.util.List;

/**
 * Port OUT — Repositorio del libro de comisiones (fee_ledger).
 * Implementado por Infrastructure Adapter → PostgresFeeLedgerRepository.
 */
public interface FeeLedgerRepository {

    FeeLedgerEntry save(FeeLedgerEntry entry);

    List<FeeLedgerEntry> listByTransaction(TransactionId transactionId);
}
