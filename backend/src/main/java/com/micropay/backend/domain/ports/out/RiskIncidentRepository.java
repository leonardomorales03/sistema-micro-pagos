package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.RiskIncident;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto OUT — persistencia de incidentes de riesgo (tabla {@code risk_incidents}).
 */
public interface RiskIncidentRepository {

    void record(RiskIncident incident);

    Optional<RiskIncident> findById(UUID id);
}
