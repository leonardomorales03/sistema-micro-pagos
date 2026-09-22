package com.micropay.backend.integration;

import com.micropay.backend.AbstractIntegrationTest;
import com.micropay.backend.infrastructure.redis.RedisWalletLockAdapter;
import com.micropay.backend.domain.valueobjects.WalletId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheBehaviorIT extends AbstractIntegrationTest {

    @Autowired RedisWalletLockAdapter walletLock;

    @Test
    void stringRedisTemplate_roundtrip_write_read_delete_on_compose_redis_72() {
        assertThat(redis).isNotNull();
        redis.opsForValue().set("it:sandbox:hello", "world-redis-it", 30, TimeUnit.SECONDS);
        assertThat(redis.opsForValue().get("it:sandbox:hello")).isEqualTo("world-redis-it");
        redis.delete("it:sandbox:hello");
        assertThat(redis.hasKey("it:sandbox:hello")).isFalse();
    }

    @Test
    void wallet_redis_distributed_lock_setnx_ttl_via_adapter_on_real_redis_container() {
        WalletId wid = WalletId.generate();
        boolean first = walletLock.tryLock(wid);
        assertThat(first).as("primer SETNX debe adquirir lock").isTrue();

        boolean second = walletLock.tryLock(wid);
        assertThat(second).as("segundo SETNX antes TTL debe fallar: lock tomado").isFalse();

        walletLock.unlock(wid);
        boolean afterRelease = walletLock.tryLock(wid);
        assertThat(afterRelease).as("después de unlock debe volver a adquirir").isTrue();
        walletLock.unlock(wid);
    }
}
