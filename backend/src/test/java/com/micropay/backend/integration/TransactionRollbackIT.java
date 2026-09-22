package com.micropay.backend.integration;

import com.micropay.backend.AbstractIntegrationTest;
import com.micropay.backend.application.services.TransactionService;
import com.micropay.backend.application.services.UserService;
import com.micropay.backend.application.services.WalletService;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.valueobjects.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRollbackIT extends AbstractIntegrationTest {

    @Autowired UserService users;
    @Autowired WalletService wallets;
    @Autowired TransactionService transactions;
    @Autowired WalletRepository walletRepository;
    @Autowired JdbcTemplate jdbc;

    enum Failure { FROZEN, DATABASE_WRITE, INSUFFICIENT_FUNDS, WRONG_OWNER }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void transferP2P_failure_rollback_ACID_balances_unchanged_in_postgres(Failure failure) {
        User a = users.register(new Email("alice-rb+" + UUID.randomUUID() + "@example.com"),
                "StrongTest123!", "Alice Rollback", null);
        User b = users.register(new Email("bob-rb+" + UUID.randomUUID() + "@example.com"),
                "StrongTest123!", "Bob Rollback", null);
        Currency c = Currency.USD;

        Wallet wA = transactions.createWallet(a.id(), c, a.id());
        Wallet wB = transactions.createWallet(b.id(), c, b.id());
        Money aSeed = Money.of(new BigDecimal("10000"), c);
        Money bSeed = Money.of(new BigDecimal("5000"), c);
        Money transfer = Money.of(new BigDecimal("3000"), c);
        wA.credit(aSeed, "seedA"); walletRepository.save(wA);
        wB.credit(bSeed, "seedB"); walletRepository.save(wB);

        if (failure == Failure.FROZEN) {
            wallets.freeze(wB.id());
        }

        // The oversized note fails in PostgreSQL AFTER both wallet writes.
        String note = failure == Failure.DATABASE_WRITE ? "x".repeat(256) : "rollback regression";
        Money amount = failure == Failure.INSUFFICIENT_FUNDS ? aSeed.add(transfer) : transfer;
        UserId initiator = failure == Failure.WRONG_OWNER ? b.id() : a.id();
        var thrown = assertThatThrownBy(() ->
                transactions.transferP2P(wA.id(), wB.id(), amount, initiator, note));
        switch (failure) {
            case FROZEN -> thrown.isInstanceOfSatisfying(BusinessRuleViolationException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo("BUSINESS_RULE_VIOLATION_WALLET_NOT_ACTIVE"));
            case WRONG_OWNER -> thrown.isInstanceOfSatisfying(BusinessRuleViolationException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo("BUSINESS_RULE_VIOLATION_WALLET_NOT_OWNED_BY_USER"));
            case INSUFFICIENT_FUNDS -> thrown.isInstanceOfSatisfying(BusinessRuleViolationException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo("BUSINESS_RULE_VIOLATION_INSUFFICIENT_AVAILABLE_BALANCE"));
            case DATABASE_WRITE -> thrown.isInstanceOf(DataIntegrityViolationException.class);
        }

        Wallet wAFinal = wallets.getByIdOrThrow(wA.id());
        Wallet wBFinal = wallets.getByIdOrThrow(wB.id());
        assertThat(wAFinal.balance()).isEqualTo(aSeed);
        assertThat(wBFinal.balance()).isEqualTo(bSeed);

        Integer txCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE wallet_from_id = ? AND wallet_to_id = ?",
                Integer.class, wA.id().uuid(), wB.id().uuid());
        assertThat(txCount).isEqualTo(0);

        Integer movesCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_moves WHERE wallet_id IN (?, ?)",
                Integer.class, wA.id().uuid(), wB.id().uuid());
        assertThat(movesCount).isEqualTo(0);
        assertThat(redis.hasKey("lock:wallet:" + wA.id().uuid())).isFalse();
        assertThat(redis.hasKey("lock:wallet:" + wB.id().uuid())).isFalse();
    }
}
