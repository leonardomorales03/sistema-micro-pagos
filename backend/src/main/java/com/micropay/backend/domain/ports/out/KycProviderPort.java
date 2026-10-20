package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.valueobjects.DocumentNumber;
import com.micropay.backend.domain.valueobjects.KycLevel;
import com.micropay.backend.domain.valueobjects.UserId;

import java.util.List;

/**
 * KYC provider port. Ambiente SANDBOX/MOCK devuelve APPROVED automático en segundos.
 * Producción sustituye por proveedor real (Onfido, Veriff, etc.).
 */
public interface KycProviderPort {

    record KycSubmissionResult(
            String providerRef,
            DocumentNumber document,
            Status status,
            KycLevel achievedLevel,
            String rejectionReason,
            java.time.Instant reviewedAt
    ) {
        public enum Status { PENDING, APPROVED, REJECTED, EXPIRED }
    }

    /** Envía documento al proveedor. Retorna providerRef para async webhook. */
    KycSubmissionResult submit(UserId userId, DocumentNumber document, byte[] payload);

    /** Resultado síncrono de chequeo mock (solo SANDBOX). Producción retorna PENDING. */
    List<KycSubmissionResult> listByUser(UserId userId);
}
