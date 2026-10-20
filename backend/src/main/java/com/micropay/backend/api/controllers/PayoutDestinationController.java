package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.PayoutDestinationDtos;
import com.micropay.backend.application.services.PayoutDestinationService;
import com.micropay.backend.domain.entities.PayoutDestination;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.PayoutDestinationId;
import com.micropay.backend.domain.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "PayoutDestinations", description = "CRUD cuentas externas destino de retiros (números enmascarados)")
@RestController
@RequestMapping("/api/v1/me/payout-destinations")
@SecurityRequirement(name = "bearerAuth")
public class PayoutDestinationController {

    private final PayoutDestinationService destinations;

    public PayoutDestinationController(PayoutDestinationService destinations) { this.destinations = destinations; }

    @Operation(summary = "Crear destino de retiro",
               description = "Registra una cuenta bancaria. El número se enmascara (**** **** **** 1234); " +
                             "sólo se persiste el enmascarado + last4. El primer destino queda por defecto.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Destino creado."),
            @ApiResponse(responseCode = "400", description = "Cuenta inválida/duplicada o límite alcanzado.")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<PayoutDestinationDtos.PayoutDestinationResponse> create(
            @Valid @RequestBody PayoutDestinationDtos.CreatePayoutDestinationRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        PayoutDestination d = destinations.create(currentUser,
                req.accountNumber(), req.bankName(), req.accountType(),
                req.holderName(), req.country(), req.currency(),
                Boolean.TRUE.equals(req.isDefault()));
        return ResponseEntity.status(201).body(toResp(d));
    }

    @Operation(summary = "Listar mis destinos de retiro")
    @GetMapping
    public ResponseEntity<PayoutDestinationDtos.PayoutDestinationListResponse> list(
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        List<PayoutDestination> rows = destinations.listByUser(currentUser);
        return ResponseEntity.ok(new PayoutDestinationDtos.PayoutDestinationListResponse(
                rows.stream().map(this::toResp).toList()));
    }

    @Operation(summary = "Eliminar destino (no permite borrar el default)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        destinations.delete(currentUser, PayoutDestinationId.from(id));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Marcar destino como por defecto")
    @PutMapping("/{id}/default")
    public ResponseEntity<PayoutDestinationDtos.PayoutDestinationResponse> setDefault(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        return ResponseEntity.ok(toResp(destinations.setDefault(currentUser, PayoutDestinationId.from(id))));
    }

    @Operation(summary = "Verificar destino (sandbox)")
    @PostMapping("/{id}/verify")
    public ResponseEntity<PayoutDestinationDtos.PayoutDestinationResponse> verify(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        return ResponseEntity.ok(toResp(destinations.markVerified(currentUser, PayoutDestinationId.from(id))));
    }

    // ───────────────── Mapper ─────────────────

    private PayoutDestinationDtos.PayoutDestinationResponse toResp(PayoutDestination d) {
        return new PayoutDestinationDtos.PayoutDestinationResponse(
                d.id().uuid(),
                d.userId().uuid(),
                d.type().name(),
                d.maskedLabel(),
                d.bankName(),
                d.accountType(),
                d.accountNumberMasked(),
                d.last4(),
                d.holderName(),
                d.country(),
                d.currency(),
                d.isDefault(),
                d.verified(),
                d.createdAt(),
                d.updatedAt()
        );
    }
}
