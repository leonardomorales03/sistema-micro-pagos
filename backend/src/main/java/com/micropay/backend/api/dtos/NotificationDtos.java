package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs de notificaciones del usuario (Requisito 25.3) — T22.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class NotificationDtos {

    @Schema(name = "NotificationResponse", description = "Notificación del log append-only del usuario")
    public record NotificationResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("channel") String channel,
            @JsonProperty("status") String status,
            @JsonProperty("payload") Map<String, Object> payload,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("read_at") Instant readAt
    ) {}

    @Schema(name = "NotificationListResponse", description = "Listado paginado + contador de no leídas")
    public record NotificationListResponse(
            @JsonProperty("data") List<NotificationResponse> data,
            @JsonProperty("page") int page,
            @JsonProperty("page_size") int pageSize,
            @JsonProperty("unread_count") long unreadCount
    ) {}

    @Schema(name = "UnreadCountResponse", description = "Contador de notificaciones no leídas")
    public record UnreadCountResponse(
            @JsonProperty("unread") long unread
    ) {}

    @Schema(name = "MarkAllReadResponse", description = "Resultado de marcar todas como leídas")
    public record MarkAllReadResponse(
            @JsonProperty("updated") int updated
    ) {}
}
