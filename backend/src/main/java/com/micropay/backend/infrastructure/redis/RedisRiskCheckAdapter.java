package com.micropay.backend.infrastructure.redis;

import com.micropay.backend.domain.ports.out.RiskCheckAdapter;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * RiskEngine implementation simple — velocity counters en Redis para:
 *   - MAX_5min: máximo 5 txs por usuario en 5 minutos
 *   - MAX_DAY: límite diario por usuario según Money (COP 10M diario hard limit
 *                para LEVEL_0)
 *   - IP: 5 IPs distintas por usuario en 24h
 * <p>
 * Cada check INCREMENTA los counters — retorna allowed=true si pasa todas
 * las reglas. Si alguna pasa.
 */
@Component
public class RedisRiskCheckAdapter implements RiskCheckAdapter {

    private static final Logger log = LoggerFactory.getLogger(RedisRiskCheckAdapter.class);

    private static final BigDecimal HARD_DAILY_LIMIT_COP = new BigDecimal("10000000"); // 10M COP
    private static final int MAX_TX_5MIN = 5;
    private static final int MAX_DIFF_IPS_24H = 5;

    private final StringRedisTemplate redis;

    public RedisRiskCheckAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public RiskResult checkTransfer(UserId userId,
                                   WalletId fromWalletId,
                                   WalletId toWalletId,
                                   Money amount,
                                   String ipAddress) {
        try {
            // Regla 1: 5 txs máximo por usuario en 5 minutos
            String txCountKey = "risk:tx:5m:" + userId.uuid();
            Long txCount = incrCounter(txCountKey, Duration.ofMinutes(5));
            if (txCount != null && txCount > MAX_TX_5MIN) {
                return new RiskResult(false, "VELOCITY_5MIN",
                        "Superado límite %d txs en 5 minutos por usuario".formatted(MAX_TX_5MIN));
            }

            // Regla 2: Límite diario duro 10M COP por día (hard stop)
            if ("COP".equalsIgnoreCase(amount.currency().isoCode())) {
                String dayKey = "risk:daily:" + userId.uuid() + ":" + java.time.LocalDate.now();
                Long daily = redis.opsForValue().increment(dayKey, amount.amount().longValue());
                redis.expire(dayKey, Duration.ofDays(2));
                if (daily != null && new BigDecimal(daily).compareTo(HARD_DAILY_LIMIT_COP) > 0) {
                    return new RiskResult(false, "DAILY_LIMIT",
                            "Superado límite diario COP %s".formatted(HARD_DAILY_LIMIT_COP));
                }
            }

            // Regla 3: 5 IPs distintas por día por usuario
            if (ipAddress != null && !ipAddress.isBlank()) {
                String ipKey = "risk:ips:" + userId.uuid() + ":" + java.time.LocalDate.now();
                Long ips = redis.opsForSet().add(ipKey, ipAddress);
                redis.expire(ipKey, Duration.ofDays(2));
                Long ipCount = redis.opsForSet().size(ipKey);
                if (ipCount != null && ipCount > MAX_DIFF_IPS_24H) {
                    return new RiskResult(false, "IP_MULTI_LOGIN",
                            "Superado límite %d IPs en 24h".formatted(MAX_DIFF_IPS_24H));
                }
            }

            return new RiskResult(true, null, null);
        } catch (Exception e) {
            // Redis caído: modo permisivo en local,
            // devolvemos permitido + warning
            log.warn("RiskEngine Redis no disponible, permitiendo operación userId={}", userId, e);
            return new RiskResult(true, "REDIS_DOWN_SKIP", "Check riesgo omitido (Redis no disponible)");
        }
    }

    private Long incrCounter(String key, Duration ttl) {
        try {
            Long cnt = redis.opsForValue().increment(key);
            if (cnt != null && cnt == 1) redis.expire(key, ttl);
            return cnt;
        } catch (Exception e) {
            return null;
        }
    }
}
