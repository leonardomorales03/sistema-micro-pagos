package com.micropay.backend.domain.valueobjects;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * CashflowReport — informe diario de flujo de caja (T25 / Requisito 28.2).
 * <p>
 * Java puro (sin Spring/JPA).
 *
 * @param date            fecha del informe
 * @param creditedByType  total acreditado (grossAmount de la pata de crédito) por tipo de transacción
 * @param debitedByType   total debitado (grossAmount de la pata de débito) por tipo de transacción
 * @param net             neto global = suma(creditedByType) - suma(debitedByType)
 * @param feesByType      fees recolectadas (feeAmount) por tipo de transacción
 */
public record CashflowReport(
        LocalDate date,
        Map<String, BigDecimal> creditedByType,
        Map<String, BigDecimal> debitedByType,
        BigDecimal net,
        Map<String, BigDecimal> feesByType
) {}
