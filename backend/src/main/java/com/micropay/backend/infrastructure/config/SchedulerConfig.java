package com.micropay.backend.infrastructure.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Configuración de jobs distribuidos (T20) — Spring Scheduler + ShedLock sobre Redis.
 * <p>
 * Evita que múltiples pods ejecuten el mismo job a la vez (p. ej. el billing de suscripciones).
 * La ventana {@code defaultLockAtMostFor} acota el tiempo máximo que un lock puede quedar tomado
 * si el pod muere sin liberarlo.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory,
                                     @Value("${micropay.shedlock.environment:micropay}") String environment) {
        return new RedisLockProvider(connectionFactory, environment);
    }
}
