package com.micropay.backend.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.MapRetryContextCache;
import org.springframework.retry.policy.RetryContextCache;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuración global Spring Retry (T9 ACID tuning).
 *
 * Política de reintentos (2 niveles):
 *  [Nivel 1 — RetryConfig SimpleRetryPolicy]: 3 intentos máximo, backoff exponencial
 *      100ms → 200ms → 400ms. Reintenta SOLO para clases marcadas transient en el mapa.
 *      traverseCauses=true para buscar excepción en la causal chain (Hibernate/JPA wrappea).
 *  [Nivel 2 — TransactionService.doTransferP2P]: antes de propagar una excepción hacia el
 *      RetryTemplate, la clasifica: si es BusinessRuleViolation con código WALLET_LOCK* o
 *      DataAccessException cuyo mensaje contenga "deadlock", la envuelve en
 *      TransientConcurrencyException (también incluida en el mapa). El resto de reglas de
 *      negocio NO se reintentan (son fallos estables).
 */
@Configuration
@EnableRetry(proxyTargetClass = true)
public class RetryConfig {

    public static final int MAX_ATTEMPTS = 3;
    public static final long INITIAL_BACKOFF_MS = 100L;
    public static final double BACKOFF_MULTIPLIER = 2.0;
    public static final long MAX_BACKOFF_MS = 800L;

    @Bean
    public RetryTemplate transferRetryTemplate() {
        RetryTemplate tpl = new RetryTemplate();
        tpl.setThrowLastExceptionOnExhausted(true);
        tpl.setRetryContextCache(retryContextCache());
        tpl.setRetryPolicy(transientConcurrencyRetryPolicy());
        tpl.setBackOffPolicy(backOffPolicy());
        return tpl;
    }

    @Bean
    public RetryContextCache retryContextCache() {
        return new MapRetryContextCache();
    }

    private ExponentialBackOffPolicy backOffPolicy() {
        ExponentialBackOffPolicy bp = new ExponentialBackOffPolicy();
        bp.setInitialInterval(INITIAL_BACKOFF_MS);
        bp.setMultiplier(BACKOFF_MULTIPLIER);
        bp.setMaxInterval(MAX_BACKOFF_MS);
        return bp;
    }

    private RetryPolicy transientConcurrencyRetryPolicy() {
        Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
        retryable.put(OptimisticLockingFailureException.class, true);
        retryable.put(ObjectOptimisticLockingFailureException.class, true);
        retryable.put(PessimisticLockingFailureException.class, true);
        retryable.put(CannotAcquireLockException.class, true);
        retryable.put(DeadlockLoserDataAccessException.class, true);
        retryable.put(CannotSerializeTransactionException.class, true);
        retryable.put(QueryTimeoutException.class, true);
        retryable.put(ConcurrencyFailureException.class, true);
        retryable.put(TransientDataAccessException.class, true);
        retryable.put(TransactionSystemException.class, true);
        retryable.put(CannotCreateTransactionException.class, true);
        retryable.put(TransientConcurrencyException.class, true);
        return new SimpleRetryPolicy(MAX_ATTEMPTS, retryable, true, false);
    }

    public static final class TransientConcurrencyException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;
        public TransientConcurrencyException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}


