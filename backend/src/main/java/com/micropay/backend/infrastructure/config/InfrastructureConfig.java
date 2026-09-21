package com.micropay.backend.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Punto central de configuración de INFRAESTRUCTURA (Arquitectura Hexagonal).
 * <p>
 * Paquetes:
 *   - domain.* : NUNCA importa Spring → pure Domain, escaneado por JPA solo @Entity
 *   - infrastructure.*: contiene @Repository adapters y @Component externos (Redis, Stripe, Notif)
 *   - application.*: contiene Use Cases (servicios)
 *   - api.*: Controllers REST
 */
@Configuration
@EntityScan(basePackages = {
        "com.micropay.backend.infrastructure.persistence.jpa.entities"
})
@EnableJpaRepositories(basePackages = {
        "com.micropay.backend.infrastructure.persistence.jpa.repositories"
})
@EnableRedisRepositories
@EnableTransactionManagement
@EnableCaching
@EnableScheduling
@EnableJpaAuditing
public class InfrastructureConfig {
}
