package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.NotificationDtos;
import com.micropay.backend.application.services.NotificationService;
import com.micropay.backend.domain.entities.UserNotification;
import com.micropay.backend.domain.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de notificaciones del usuario (Requisito 25.3) — T22.
 * Log append-only: el usuario sólo puede leer sus propias notificaciones y marcarlas como leídas.
 */
@Tag(name = "Notifications", description = "Centro de notificaciones del usuario (log in-app, no leídas)")
@RestController
@RequestMapping("/api/v1/notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Operation(summary = "Listar mis notificaciones",
               description = "Más recientes primero, con contador de no leídas incluido.")
    @GetMapping
    public ResponseEntity<NotificationDtos.NotificationListResponse> list(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        List<UserNotification> rows = notifications.list(currentUser, page, pageSize);
        return ResponseEntity.ok(new NotificationDtos.NotificationListResponse(
                rows.stream().map(NotificationController::toResp).toList(),
                page, pageSize, notifications.unreadCount(currentUser)));
    }

    @Operation(summary = "Contador de no leídas (badge)")
    @GetMapping("/unread-count")
    public ResponseEntity<NotificationDtos.UnreadCountResponse> unreadCount(
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        return ResponseEntity.ok(new NotificationDtos.UnreadCountResponse(notifications.unreadCount(currentUser)));
    }

    @Operation(summary = "Marcar una notificación como leída")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificación marcada como leída."),
            @ApiResponse(responseCode = "422", description = "Notificación inexistente o de otro usuario.")
    })
    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationDtos.NotificationResponse> markRead(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        return ResponseEntity.ok(toResp(notifications.markRead(currentUser, id)));
    }

    @Operation(summary = "Marcar todas como leídas")
    @PostMapping("/read-all")
    public ResponseEntity<NotificationDtos.MarkAllReadResponse> markAllRead(
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        return ResponseEntity.ok(new NotificationDtos.MarkAllReadResponse(notifications.markAllRead(currentUser)));
    }

    // ───────────────── Mapper ─────────────────

    static NotificationDtos.NotificationResponse toResp(UserNotification n) {
        return new NotificationDtos.NotificationResponse(
                n.id(),
                n.eventType(),
                n.channel().name(),
                n.status().name(),
                n.payload(),
                n.createdAt(),
                n.readAt()
        );
    }
}
