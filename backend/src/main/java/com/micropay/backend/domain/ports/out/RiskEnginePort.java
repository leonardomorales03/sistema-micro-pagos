package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.valueobjects.RiskContext;
import com.micropay.backend.domain.valueobjects.RiskDecision;

/**
 * Puerto OUT — RiskEngine configurable (reglas + velocity counters).
 * Implementado por infraestructura ({@code ConfigurableRiskEngineAdapter}).
 * <p>
 * No consulta persistencia: sólo configuración + estado efímero (Redis).
 */
public interface RiskEnginePort {

    /** Evalúa el contexto y devuelve una decisión (ALLOW / CHALLENGE / BLOCK). No lanza. */
    RiskDecision evaluate(RiskContext context);
}
