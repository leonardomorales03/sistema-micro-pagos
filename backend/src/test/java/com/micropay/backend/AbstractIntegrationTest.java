package com.micropay.backend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected JdbcTemplate testJdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${spring.data.redis.database}")
    private int redisDatabase;

    @Autowired
    protected StringRedisTemplate redis;

    @BeforeEach
    protected void resetTestData() {
        String database = testJdbc.queryForObject("SELECT current_database()", String.class);
        if (!"micropay_test".equals(database)) {
            throw new IllegalStateException("Integration cleanup requires micropay_test");
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> testJdbc.execute("""
                TRUNCATE account_moves, transactions, payment_requests, notifications,
                         risk_incidents, sessions, kyc_documents, wallets, users
                """));
        flushAllRedis();
    }

    @AfterEach
    protected void flushRedisAfterTest() {
        flushAllRedis();
    }

    private void flushAllRedis() {
        if (redisDatabase != 15) {
            throw new IllegalStateException("Integration cleanup requires Redis database 15");
        }
        if (redis != null) {
            Set<String> keys = redis.keys("*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
    }
}
