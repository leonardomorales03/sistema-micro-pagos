package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.RiskIncident;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.exceptions.RiskChallengeRequiredException;
import com.micropay.backend.domain.ports.in.RiskUseCases;
import com.micropay.backend.domain.ports.out.NotificationAdapter;
import com.micropay.backend.domain.ports.out.RiskEnginePort;
import com.micropay.backend.domain.ports.out.RiskIncidentRepository;
import com.micropay.backend.domain.valueobjects.RiskContext;
import com.micropay.backend.domain.valueobjects.RiskDecision;
import com.micropay.backend.domain.valueobjects.RiskReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de aplicación del RiskEngine (T23 / Requisito 27).
 * <p>
 * Orquesta la evaluación ({@link RiskEnginePort}) con:
 * <ul>
 *   <li>persistencia de incidentes en BLOCK (transacción independiente, no se pierde si el caller
 *       revierte su propia transacción),</li>
 *   <li>emisión y verificación de challenges de step-up almacenados en Redis (TTL configurable),</li>
 *   <li>envío del código por canal EMAIL vía {@link NotificationAdapter}.</li>
 * </ul>
 */
@Service
public class RiskService implements RiskUseCases {

    private static final Logger log = LoggerFactory.getLogger(RiskService.class);
    private static final Logger audit = LoggerFactory.getLogger("AUDIT_RISK");

    private static final String CHALLENGE_KEY_PREFIX = "risk:challenge:";
    private static final String APPROVED_KEY_PREFIX = "risk:challenge:approved:";
    private static final String CHALLENGE_TEMPLATE = "RISK_CHALLENGE_CODE";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RiskEnginePort engine;
    private final StringRedisTemplate redis;
    private final RiskIncidentRepository incidents;
    private final NotificationAdapter notifications;
    private final int challengeTtlSeconds;
    private final TransactionTemplate incidentTx;

    public RiskService(RiskEnginePort engine,
                       StringRedisTemplate redis,
                       RiskIncidentRepository incidents,
                       NotificationAdapter notifications,
                       @Value("${micropay.risk.challenge-ttl-seconds:300}") int challengeTtlSeconds,
                       PlatformTransactionManager transactionManager) {
        this.engine = engine;
        this.redis = redis;
        this.incidents = incidents;
        this.notifications = notifications;
        this.challengeTtlSeconds = challengeTtlSeconds;
        this.incidentTx = new TransactionTemplate(transactionManager);
        // Los incidentes deben persistirse aunque el caller revierta su transacción.
        this.incidentTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public RiskDecision evaluate(RiskContext ctx) {
        if (ctx == null) return RiskDecision.allow();
        return engine.evaluate(ctx);
    }

    @Override
    public void enforce(RiskContext ctx, String challengeApprovalToken) {
        RiskDecision decision = evaluate(ctx);
        switch (decision.type()) {
            case ALLOW -> { /* sin acción */ }
            case BLOCK -> {
                recordIncident(ctx, decision);
                audit.warn("⛔ RISK_BLOCKED uid={} op={} rules={}",
                        ctx.userId(), ctx.operation(), reasonCodes(decision));
                // Mensaje genérico: no se revelan detalles de la regla al cliente.
                throw new BusinessRuleViolationException("RISK_BLOCKED",
                        "Operación bloqueada por políticas de seguridad. Contacte a soporte si cree que es un error.");
            }
            case CHALLENGE -> {
                if (isChallengeApproved(challengeApprovalToken)) {
                    log.debug("[RISK] challenge ya aprobado token={} op={}", challengeApprovalToken, ctx.operation());
                    return;
                }
                String method = decision.challengeMethod() == null ? "EMAIL_CODE" : decision.challengeMethod();
                String token = UUID.randomUUID().toString();
                String code = generateCode();
                redis.opsForValue().set(CHALLENGE_KEY_PREFIX + token, code, Duration.ofSeconds(challengeTtlSeconds));
                sendChallengeCode(ctx, code);
                audit.info("🔐 RISK_CHALLENGE uid={} op={} method={} rules={}",
                        ctx.userId(), ctx.operation(), method, reasonCodes(decision));
                throw new RiskChallengeRequiredException(token, method, reasonCodes(decision));
            }
        }
    }

    @Override
    public String verifyChallenge(String token, String code) {
        if (token == null || token.isBlank()) {
            throw new BusinessRuleViolationException("RISK_CHALLENGE_NOT_FOUND", "Token de challenge ausente");
        }
        String key = CHALLENGE_KEY_PREFIX + token;
        String stored = redis.opsForValue().get(key);
        if (stored == null) {
            throw new BusinessRuleViolationException("RISK_CHALLENGE_EXPIRED",
                    "El challenge %s no existe o ha expirado".formatted(token));
        }
        if (code == null || !stored.equals(code.trim())) {
            throw new BusinessRuleViolationException("RISK_CHALLENGE_INVALID_CODE",
                    "Código de verificación incorrecto");
        }
        redis.delete(key);
        redis.opsForValue().set(APPROVED_KEY_PREFIX + token, "1", Duration.ofSeconds(challengeTtlSeconds));
        audit.info("✅ RISK_CHALLENGE_VERIFIED token={}", token);
        return token;
    }

    // ─────────────────────── helpers ───────────────────────

    private boolean isChallengeApproved(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            return Boolean.TRUE.equals(redis.hasKey(APPROVED_KEY_PREFIX + token));
        } catch (Exception e) {
            log.warn("[RISK] Redis no disponible al validar challenge aprobado token={}: {}", token, e.getMessage());
            return false;
        }
    }

    private void sendChallengeCode(RiskContext ctx, String code) {
        if (ctx.userId() == null) return;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("code", code);
            payload.put("operation", ctx.operation() == null ? null : ctx.operation().name());
            payload.put("amount", ctx.amount() == null ? null : ctx.amount().toString());
            notifications.send(new NotificationAdapter.NotificationRequest(
                    ctx.userId(), NotificationAdapter.Channel.EMAIL, CHALLENGE_TEMPLATE, payload));
        } catch (Exception e) {
            log.warn("No se pudo enviar el código de challenge a user {}: {}", ctx.userId(), e.getMessage());
        }
    }

    /**
     * Persiste el incidente en una transacción independiente (REQUIRES_NEW): aunque el caller
     * revierta su operación, la auditoría del bloqueo queda registrada. Best-effort.
     */
    private void recordIncident(RiskContext ctx, RiskDecision decision) {
        try {
            incidentTx.executeWithoutResult(status -> {
                for (RiskReason reason : decision.reasons()) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("operation", ctx.operation() == null ? null : ctx.operation().name());
                    payload.put("amount", ctx.amount() == null ? null : ctx.amount().toString());
                    payload.put("country", ctx.countryISO2());
                    payload.put("ip", ctx.ipAddress());
                    payload.put("reason", reason.code());
                    incidents.record(RiskIncident.open(
                            reason.code(), ctx.userId(), null, reason.severity(), payload));
                }
            });
        } catch (Exception e) {
            log.warn("[RISK] No se pudo registrar incidente de bloqueo uid={} op={}: {}",
                    ctx.userId(), ctx.operation(), e.getMessage());
        }
    }

    private static List<String> reasonCodes(RiskDecision decision) {
        return decision.reasons().stream().map(RiskReason::code).toList();
    }

    private static String generateCode() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }
}
