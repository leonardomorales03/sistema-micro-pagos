package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.ReferralDtos;
import com.micropay.backend.domain.entities.Referral;
import com.micropay.backend.domain.ports.in.ReferralUseCases;
import com.micropay.backend.domain.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints de Referrals & Rewards del usuario autenticado (T27 / Requisito 31).
 * Expone el código propio, el enlace de invitación, los referidos y el resumen de bonos.
 */
@Tag(name = "Referrals", description = "Programa de referidos: código propio, enlace, referidos y bonos (Requisito 31)")
@RestController
@RequestMapping("/api/v1/me/referrals")
@SecurityRequirement(name = "bearerAuth")
public class ReferralController {

    private final ReferralUseCases referrals;

    public ReferralController(ReferralUseCases referrals) {
        this.referrals = referrals;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar mis referidos (estado, invitee, bonos, fechas) + mi código/enlace de invitación")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de referidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<ReferralDtos.ReferralListResponse> list(
            @Parameter(hidden = true) @CurrentUser UserId currentId) {
        ReferralUseCases.ReferralSummary summary = referrals.referralSummary(currentId.uuid()).orElse(null);
        List<Referral> rows = referrals.listMine(currentId.uuid());
        return ResponseEntity.ok(new ReferralDtos.ReferralListResponse(
                summary != null ? summary.referralCode() : null,
                summary != null ? summary.referralLink() : null,
                rows.stream().map(this::toResp).toList()));
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Resumen del programa de referidos (nº referidos, bonos ganados)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen de referidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<ReferralDtos.ReferralSummaryResponse> summary(
            @Parameter(hidden = true) @CurrentUser UserId currentId) {
        return referrals.referralSummary(currentId.uuid())
                .map(s -> ResponseEntity.ok(new ReferralDtos.ReferralSummaryResponse(
                        s.referralCode(), s.referralLink(),
                        s.totalReferrals(), s.qualifiedCount(), s.pendingCount(), s.totalBonusEarned())))
                .orElseGet(() -> ResponseEntity.ok(new ReferralDtos.ReferralSummaryResponse(
                        null, null, 0, 0, 0, null)));
    }

    // ────────── Mapper ──────────

    private ReferralDtos.ReferralResponse toResp(Referral r) {
        return new ReferralDtos.ReferralResponse(
                r.id(),
                r.code(),
                r.referrerId(),
                r.inviteeId(),
                r.status() != null ? r.status().name() : null,
                r.referrerBonus(),
                r.inviteeBonus(),
                r.bonusTxId(),
                r.createdAt(),
                r.qualifiedAt()
        );
    }
}
