package com.micropay.backend.infrastructure.risk;

import com.micropay.backend.domain.ports.out.RiskEnginePort;
import com.micropay.backend.domain.valueobjects.KycLevel;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.RiskContext;
import com.micropay.backend.domain.valueobjects.RiskDecision;
import com.micropay.backend.domain.valueobjects.RiskOperation;
import com.micropay.backend.domain.valueobjects.RiskReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * RiskEngine configurable (T23 / Requisito 27).
 * <p>
 * Reglas basadas sólo en configuración ({@code micropay.risk.*}) y estado efímero en Redis.
 * NUNCA consulta base de datos. Reglas implementadas:
 * <ul>
 *   <li>Velocity P2P por minuto / hora / día → BLOCK.</li>
 *   <li>Top-up diario sobre umbral para usuarios no verificados (KYC nivel 0) → BLOCK.</li>
 *   <li>País de origen en lista de alto riesgo (cross-border) → BLOCK.</li>
 *   <li>Primer top-up sobre umbral → CHALLENGE (EMAIL_CODE).</li>
 *   <li>Retiro / reembolso grande sobre umbral → CHALLENGE (EMAIL_CODE).</li>
 * </ul>
 * Si {@code micropay.risk.enabled=false} siempre devuelve ALLOW.
 */
@Component
public class ConfigurableRiskEngineAdapter implements RiskEnginePort {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableRiskEngineAdapter.class);
    private static final String CHALLENGE_METHOD_EMAIL = "EMAIL_CODE";

    private final RedisVelocityCounter velocity;

    @Value("${micropay.risk.enabled:true}")
    private boolean enabled;

    @Value("${micropay.risk.first-topup-max:100}")
    private BigDecimal firstTopupMax;

    @Value("${micropay.risk.topup-per-day-unverified:2}")
    private long topupPerDayUnverified;

    @Value("${micropay.risk.large-withdrawal-threshold:1000}")
    private BigDecimal largeWithdrawalThreshold;

    @Value("${micropay.risk.large-refund-threshold:1000}")
    private BigDecimal largeRefundThreshold;

    @Value("${micropay.risk.cross-border-blocklist:CUBA,IRAN,NORTH_KOREA,RUSSIA,SYRIA}")
    private List<String> crossBorderBlocklist;

    @Value("${micropay.risk.velocity.p2p-per-minute:10}")
    private long p2pPerMinute;

    @Value("${micropay.risk.velocity.p2p-per-hour:100}")
    private long p2pPerHour;

    @Value("${micropay.risk.velocity.p2p-per-day:500}")
    private long p2pPerDay;

    public ConfigurableRiskEngineAdapter(RedisVelocityCounter velocity) {
        this.velocity = velocity;
    }

    @Override
    public RiskDecision evaluate(RiskContext ctx) {
        if (!enabled || ctx == null) {
            return RiskDecision.allow();
        }

        List<RiskReason> blocks = new ArrayList<>();
        List<RiskReason> challenges = new ArrayList<>();
        String uid = ctx.userId() == null ? "anon" : ctx.userId().uuid().toString();

        // 1. Cross-border: país de origen en lista de alto riesgo
        if (ctx.countryISO2() != null && !ctx.countryISO2().isBlank()
                && isBlockedCountry(ctx.countryISO2())) {
            blocks.add(new RiskReason("CROSS_BORDER_HIGH_RISK",
                    "País de origen en lista de alto riesgo: " + ctx.countryISO2(),
                    RiskReason.CRITICAL));
        }

        // 2. Velocity P2P por minuto / hora / día
        if (ctx.operation() == RiskOperation.P2P_TRANSFER) {
            long perMinute = velocity.increment("risk:velocity:p2p:min:" + uid, 60);
            if (perMinute > p2pPerMinute) {
                blocks.add(new RiskReason("VELOCITY_MINUTE",
                        "Excedido límite de %d transferencias P2P por minuto".formatted(p2pPerMinute),
                        RiskReason.HIGH));
            }
            long perHour = velocity.increment("risk:velocity:p2p:hour:" + uid, 3600);
            if (perHour > p2pPerHour) {
                blocks.add(new RiskReason("VELOCITY_HOUR",
                        "Excedido límite de %d transferencias P2P por hora".formatted(p2pPerHour),
                        RiskReason.HIGH));
            }
            long perDay = velocity.increment("risk:velocity:p2p:day:" + uid + ":" + LocalDate.now(), 86400);
            if (perDay > p2pPerDay) {
                blocks.add(new RiskReason("VELOCITY_DAY",
                        "Excedido límite de %d transferencias P2P por día".formatted(p2pPerDay),
                        RiskReason.HIGH));
            }
        }

        // 3. Top-up diario para usuarios no verificados (KYC nivel 0)
        if (ctx.operation() == RiskOperation.TOPUP && isUnverified(ctx.kycLevel())) {
            long topupsToday = velocity.increment(
                    "risk:velocity:topup:day:unverified:" + uid + ":" + LocalDate.now(), 86400);
            if (topupsToday > topupPerDayUnverified) {
                blocks.add(new RiskReason("KYC_TOPUP_DAILY_LIMIT",
                        "Excedido número de recargas diarias (%d) para usuario no verificado"
                                .formatted(topupPerDayUnverified),
                        RiskReason.HIGH));
            }
        }

        // 4. Primer top-up sobre umbral → step-up
        if (ctx.operation() == RiskOperation.TOPUP && isGreaterThan(ctx.amount(), firstTopupMax)) {
            challenges.add(new RiskReason("FIRST_TOPUP_OVER_LIMIT",
                    "Monto de recarga supera el umbral permitido sin verificación adicional",
                    RiskReason.MEDIUM));
        }

        // 5. Retiro / reembolso grande → step-up
        if (ctx.operation() == RiskOperation.WITHDRAWAL && isGreaterThanOrEqual(ctx.amount(), largeWithdrawalThreshold)) {
            challenges.add(new RiskReason("LARGE_OPERATION",
                    "Retiro por encima del umbral que requiere verificación adicional",
                    RiskReason.MEDIUM));
        }
        if (ctx.operation() == RiskOperation.REFUND && isGreaterThanOrEqual(ctx.amount(), largeRefundThreshold)) {
            challenges.add(new RiskReason("LARGE_OPERATION",
                    "Reembolso por encima del umbral que requiere verificación adicional",
                    RiskReason.MEDIUM));
        }

        if (!blocks.isEmpty()) {
            log.info("[RISK] BLOCK uid={} op={} rules={}", uid, ctx.operation(),
                    blocks.stream().map(RiskReason::code).toList());
            return RiskDecision.block(blocks.toArray(RiskReason[]::new));
        }
        if (!challenges.isEmpty()) {
            log.info("[RISK] CHALLENGE uid={} op={} rules={}", uid, ctx.operation(),
                    challenges.stream().map(RiskReason::code).toList());
            return RiskDecision.challenge(CHALLENGE_METHOD_EMAIL, null, challenges.toArray(RiskReason[]::new));
        }
        return RiskDecision.allow();
    }

    // ─────────────────────── helpers ───────────────────────

    private boolean isBlockedCountry(String countryISO2) {
        if (crossBorderBlocklist == null) return false;
        String normalized = countryISO2.trim().toUpperCase();
        return crossBorderBlocklist.stream()
                .filter(c -> c != null && !c.isBlank())
                .anyMatch(c -> c.trim().toUpperCase().equals(normalized));
    }

    private static boolean isUnverified(KycLevel kycLevel) {
        return kycLevel == null || kycLevel == KycLevel.LEVEL_0;
    }

    private static boolean isGreaterThan(Money amount, BigDecimal threshold) {
        return amount != null && amount.amount() != null && threshold != null
                && amount.amount().compareTo(threshold) > 0;
    }

    private static boolean isGreaterThanOrEqual(Money amount, BigDecimal threshold) {
        return amount != null && amount.amount() != null && threshold != null
                && amount.amount().compareTo(threshold) >= 0;
    }
}
