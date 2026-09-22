package com.micropay.backend.infrastructure.redis;

import com.micropay.backend.domain.ports.out.WalletLockAdapter;
import com.micropay.backend.domain.valueobjects.WalletId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Implementación distribuida de WalletLockAdapter usando Redis SETNX (redisson-like.
 * <p>
 *   Failover: si RedisTemplate no está disponible o falla, usa fallback a ConcurrentHashMap
 *   en memoria (single JVM — válido para entornos de 1 nodo local; producción
 *   (desarrollo.
 */
@Component
public class RedisWalletLockAdapter implements WalletLockAdapter {

    private static final Logger log = LoggerFactory.getLogger(RedisWalletLockAdapter.class);
    private static final String KEY_PREFIX = "lock:wallet:";

    private final StringRedisTemplate redis;
    private final ConcurrentMap<String, Long> memoryFallback = new ConcurrentHashMap<>();

    public RedisWalletLockAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryLock(WalletId walletId) {
        String key = KEY_PREFIX + walletId.uuid();
        Boolean acquired = redisTryLock(key);
        if (acquired != null) {
            return acquired;
        }
        long now = System.currentTimeMillis();
        Long existing = memoryFallback.putIfAbsent(key, now + TTL_SECONDS * 1000L);
        if (existing == null) return true;
        if (existing < now) {
            memoryFallback.replace(key, existing, now + TTL_SECONDS * 1000L);
            return true;
        }
        log.debug("Wallet lock ocupado para walletId={}", walletId);
        return false;
    }

    @Override
    public void unlock(WalletId walletId) {
        String key = KEY_PREFIX + walletId.uuid();
        try {
            redis.delete(key);
        } catch (Exception ignore) {}
        memoryFallback.remove(key);
    }

    private Boolean redisTryLock(String key) {
        try {
            return redis.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(TTL_SECONDS));
        } catch (Exception e) {
            // Redis caído o sin conexión — devolver null para activar fallback
            return null;
        }
    }
}
