package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO base: ErrorResponse estándar RFC 7807 Problem Detail + error_code (códigos Business).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @JsonProperty("type") String type,
        @JsonProperty("title") String title,
        @JsonProperty("status") int status,
        @JsonProperty("detail") String detail,
        @JsonProperty("instance") String instance,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("trace_id") String traceId
) {}
