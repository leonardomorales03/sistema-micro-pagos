package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.RiskDtos;
import com.micropay.backend.domain.ports.in.RiskUseCases;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints del RiskEngine: verificación de challenges de step-up (T23 / Requisito 27.3/27.4).
 * <p>
 * El endpoint de creación de la operación sensible devuelve HTTP 428 con las cabeceras
 * {@code X-Risk-Challenge-Token} y {@code X-Risk-Challenge-Method}; el cliente verifica el
 * código aquí y reintenta la operación original incluyendo el token en la cabecera
 * {@code X-Risk-Challenge}.
 */
@Tag(name = "Risk", description = "RiskEngine — verificación de challenges de step-up (anti-fraude)")
@RestController
@RequestMapping("/api/v1/risk")
@SecurityRequirement(name = "bearerAuth")
public class RiskController {

    private final RiskUseCases risk;

    public RiskController(RiskUseCases risk) {
        this.risk = risk;
    }

    @Operation(summary = "Verificar código de challenge",
               description = "Valida el código enviado por EMAIL para un challenge emitido por el RiskEngine. " +
                       "Devuelve el token aprobado que debe reenviarse en la cabecera X-Risk-Challenge de la operación original.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Challenge verificado; token aprobado"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "422", description = "Código inválido o challenge expirado")
    })
    @PostMapping("/challenges/{token}/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RiskDtos.VerifyChallengeResponse> verifyChallenge(
            @PathVariable("token") String token,
            @Valid @RequestBody RiskDtos.VerifyChallengeRequest req) {
        String approvedToken = risk.verifyChallenge(token, req.code());
        return ResponseEntity.ok(new RiskDtos.VerifyChallengeResponse(true, approvedToken));
    }
}
