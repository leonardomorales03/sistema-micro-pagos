package com.micropay.backend.api.config;

import com.micropay.backend.api.dtos.ErrorResponse;
import com.micropay.backend.domain.exceptions.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Exception Handler Global (RFC 7807 Problem Detail JSON).
 * Convierte todas las excepciones Java en respuestas JSON estándar:
 *   { status, error_code, detail, timestamp, trace_id, instance }
 *
 * HttpStatus mapping:
 *   BusinessRuleViolationException  → 422 UNPROCESSABLE_ENTITY (error_code interno)
 *   WalletLockConflict                 → 409 CONFLICT (WALLET_LOCK_CONFLICT)
 *   ConcurrencyConflictException       → 409 CONFLICT + Retry-After header
 *   UserNotFoundException          → 404 NOT_FOUND
 *   TransactionNotFoundException   → 404
 *   WalletNotFoundException        → 404
 *   AccessDeniedException          → 403 FORBIDDEN
 *   BadCredentialsException        → 401 UNAUTHORIZED
 *   MethodArgumentNotValidException → 400 BAD_REQUEST (validation)
 *   InvalidValueObjectException       → 400 BAD_REQUEST
 *   Exception default                → 500 (INTERNAL_SERVER_ERROR, log ERROR
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String RETRY_AFTER_SECONDS = "2";

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleViolationException ex,
                                                           HttpServletRequest req) {
        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;
        String code = ex.getCode();
        if (code != null && code.contains("LOCK")) status = HttpStatus.CONFLICT;
        if (isAuthError(code)) status = HttpStatus.UNAUTHORIZED;
        return build(status, code, ex.getMessage(), req);
    }

    @ExceptionHandler(ConcurrencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleConcurrencyConflict(ConcurrencyConflictException ex,
                                                                    HttpServletRequest req) {
        String detail = ("%s Operación: %s. Reintente en %ss o implemente backoff exponencial local.").formatted(
                ex.getMessage(),
                ex.operation() != null ? ex.operation() : "N/A",
                RETRY_AFTER_SECONDS);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("Retry-After", RETRY_AFTER_SECONDS)
                .header("X-Micropay-Attempts", String.valueOf(ex.attempts()))
                .header("X-Micropay-Operation", ex.operation() != null ? ex.operation() : "");
        String traceId = UUID.randomUUID().toString();
        ErrorResponse body = new ErrorResponse(
                "about:blank",
                HttpStatus.CONFLICT.getReasonPhrase(),
                HttpStatus.CONFLICT.value(),
                detail,
                req.getRequestURI(),
                ex.errorCode(),
                Instant.now(),
                traceId);
        log.atWarn()
                .addKeyValue("error_code", ex.errorCode())
                .addKeyValue("attempts", ex.attempts())
                .addKeyValue("operation", ex.operation())
                .addKeyValue("trace_id", traceId)
                .log("ConcurrencyConflict path={} attempts={} cause={}",
                        req.getRequestURI(), ex.attempts(),
                        ex.getCause() == null ? null : ex.getCause().getClass().getSimpleName() + ": " + ex.getCause().getMessage());
        return builder.body(body);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWalletNotFound(WalletNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTxNotFound(TransactionNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidValueObjectException.class)
    public ResponseEntity<ErrorResponse> handleInvalidVO(InvalidValueObjectException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_VALUE_OBJECT", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Cuerpo de la solicitud mal formado: JSON inválido o faltan campos requeridos", req);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoHandlerFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND",
                "Endpoint no encontrado: " + ex.getHttpMethod() + " " + ex.getRequestURL(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Permisos insuficientes para realizar esta operación", req);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Credenciales inválidas", req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        log.debug("Argumento ilegal: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), req);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                         HttpServletRequest req) {
        return build(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
                "Accept header media type no soportado", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                 HttpServletRequest req) {
        String supported = ex.getSupportedHttpMethods() != null
                ? String.join(", ", ex.getSupportedHttpMethods().stream().map(HttpMethod::name).toList())
                : "";
        String detail = "Método HTTP %s no permitido para %s. Métodos soportados: [%s]"
                .formatted(ex.getMethod(), req.getRequestURI(), supported);
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", detail, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAny(Exception ex, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString();
        log.error("[trace_id={} path={} error=\"{}\"", traceId, req.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "Error interno del servidor. Trace ID: " + traceId, req, traceId);
    }

    // ───────── helpers ─────────

    private static boolean isAuthError(String code) {
        if (code == null) return false;
        return code.contains("INVALID_CREDENTIALS")
                || code.startsWith("REFRESH_")
                || code.equals("USER_BLOCKED")
                || code.equals("USER_NOT_ACTIVE");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String detail,
                                            HttpServletRequest req) {
        return build(status, code, detail, req, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String detail,
                                               HttpServletRequest req, String traceIdOverride) {
        String path = req.getRequestURI();
        String traceId = traceIdOverride != null ? traceIdOverride : UUID.randomUUID().toString();
        ErrorResponse body = new ErrorResponse(
                "about:blank",                    // RFC 7807 type = "about:blank" si no categorías
                status.getReasonPhrase(),        // title = status phrase
                status.value(),                  // status
                detail,
                path,                            // instance = URI request path
                code,                            // error_code interno (micropay)
                Instant.now(),
                traceId
        );
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
