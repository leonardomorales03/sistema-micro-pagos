package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.valueobjects.FeeContext;
import com.micropay.backend.domain.valueobjects.FeeLine;

import java.util.List;

/**
 * Port OUT — Motor de comisiones (Requisito 30.1).
 * Implementado por Infrastructure Adapter → ConfigurableFeeEngineAdapter.
 * <p>
 * Retorna las líneas de comisión aplicables al contexto de la operación.
 * Una lista vacía significa "operación sin comisión".
 */
public interface FeeEnginePort {

    List<FeeLine> calculateFees(FeeContext context);
}
