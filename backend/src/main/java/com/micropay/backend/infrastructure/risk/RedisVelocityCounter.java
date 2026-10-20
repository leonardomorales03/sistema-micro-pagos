package com.micropay.backend.infrastructure.risk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Contador de velocidad atómico sobre Redis (T23 / Requisito 27.2).
 * <p>
 * {@link #increment(String, long)} ejecuta un script Lua que hace INCR + EXPIRE
 * en una sola operación atómica, evitando carreras entre el incremento y el TTL.
 * <p>
 * Modo permisivo: ante cualquier fallo de Redis devuelve {@code -1} (contador inválido)
 * y registra un warning, de modo que las reglas de riesgo no bloqueen operaciones por
 * indisponibilidad del almacén efímero.
 */
@Component
public class RedisVelocityCounter {

    private static final Logger log = LoggerFactory.getLogger(RedisVelocityCounter.class);

    /** INCR key; si es el primer incremento (== 1) fija EXPIRE ttl. Devuelve el valor actual. */
    private static final DefaultRedisScript<Long> INCR_EXPIRE = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]); " +
            "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end; " +
            "return c;",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisVelocityCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Incrementa el contador de la ventana {@code key} y le asigna {@code ttlSeconds} en el primer hit.
     *
     * @return valor actual del contador, o {@code -1} si Redis no está disponible (modo permisivo).
     */
    public long increment(String key, long ttlSeconds) {
        try {
            Long value = redis.execute(INCR_EXPIRE, List.of(key), String.valueOf(ttlSeconds));
            return value == null ? -1L : value;
        } catch (Exception e) {
            log.warn("RedisVelocityCounter no disponible para key={}; se omite el conteo de velocidad", key, e);
            return -1L;
        }
    }
}
