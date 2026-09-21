package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.UserDtos;
import com.micropay.backend.application.services.UserService;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.exceptions.UserNotFoundException;
import com.micropay.backend.domain.ports.in.UserUseCases;
import com.micropay.backend.domain.valueobjects.Email;
import com.micropay.backend.domain.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Users", description = "Usuarios: registrar, buscar, bloquear (admin), listar")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserUseCases users;

    public UserController(UserService users) { this.users = users; }

    @PostMapping("/register")
    @Operation(summary = "Registrar un usuario nuevo",
               description = "Endpoint público. Crea User en estado PENDING_EMAIL_VERIFICATION, envía notificación welcome.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente"),
            @ApiResponse(responseCode = "422", description = "Email inválido / password demasiado corto / email ya existe")
    })
    public ResponseEntity<UserDtos.UserResponse> register(@RequestBody UserDtos.RegisterRequest req) {
        UserId referred = null;
        if (req.referralCode() != null && !req.referralCode().isBlank()) {
            User r = users.findByReferralCode(req.referralCode()).orElse(null);
            if (r != null) referred = r.id();
        }
        User created = users.register(new Email(req.email()), req.password(), req.fullName(), referred);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResp(created));
    }

    @GetMapping("/me")
    @Operation(summary = "Obtener perfil del usuario autenticado", security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<UserDtos.UserResponse> me(@Parameter(hidden = true) @CurrentUser UserId currentId) {
        User u = users.findById(currentId)
                .orElseThrow(() -> new UserNotFoundException(currentId.uuid()));
        return ResponseEntity.ok(toResp(u));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN') or #id == authentication.principal.userId")
    @Operation(summary = "Buscar usuario por ID (ROLE_USER propio, ROLE_ADMIN cualquiera)", security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserDtos.UserResponse> findById(@PathVariable UUID id) {
        User u = users.findById(UserId.from(id))
                .orElseThrow(() -> new UserNotFoundException(id));
        return ResponseEntity.ok(toResp(u));
    }

    @GetMapping(params = {"email"})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Buscar usuario por email (sólo ADMIN)", security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserDtos.UserResponse> findByEmail(@RequestParam String email) {
        User u = users.findByEmail(new Email(email))
                .orElseThrow(() -> new UserNotFoundException("email=" + email));
        return ResponseEntity.ok(toResp(u));
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Listar usuarios (sólo ADMIN, paginado)", security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<UserDtos.UserResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(users.listUsers(page, pageSize).stream().map(this::toResp).toList());
    }

    @PostMapping("/{id}/verify-email")
    @Operation(summary = "Marcar email verificado (cambia status ACTIVE) — público (link email)")
    public ResponseEntity<Void> verifyEmail(@PathVariable UUID id) {
        users.verifyEmail(UserId.from(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/block")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Bloquear usuario (sólo ADMIN)", security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> block(@PathVariable UUID id,
                                      @RequestBody(required = false) UserDtos.BlockRequest body,
                                      @Parameter(hidden = true) @CurrentUser UserId adminId) {
        users.blockUser(UserId.from(id), body != null ? body.reason() : "ADMIN", adminId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unblock")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Desbloquear usuario (sólo ADMIN)", security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> unblock(@PathVariable UUID id,
                                        @Parameter(hidden = true) @CurrentUser UserId adminId) {
        users.unblockUser(UserId.from(id), adminId);
        return ResponseEntity.noContent().build();
    }

    // ────────── mapper ──────────

    UserDtos.UserResponse toResp(User u) {
        return new UserDtos.UserResponse(
                u.id().uuid(),
                u.email().value(),
                u.fullName(),
                u.phoneNumber(),
                u.kycLevel().name(),
                u.status().name(),
                u.emailVerified(),
                u.referralCode(),
                u.referredBy() != null ? u.referredBy().uuid() : null,
                u.roles(),
                u.createdAt(),
                u.updatedAt()
        );
    }
}
