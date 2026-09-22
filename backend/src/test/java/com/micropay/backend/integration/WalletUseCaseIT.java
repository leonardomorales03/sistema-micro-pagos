package com.micropay.backend.integration;

import com.micropay.backend.AbstractIntegrationTest;
import com.micropay.backend.application.services.TransactionService;
import com.micropay.backend.application.services.UserService;
import com.micropay.backend.application.services.WalletService;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.valueobjects.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WalletUseCaseIT extends AbstractIntegrationTest {

    @Autowired UserService users;
    @Autowired WalletService wallets;
    @Autowired TransactionService transactions;
    @Autowired WalletRepository walletRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void wallet_create_and_find_roundtrip_persists_in_postgres_compose() {
        User u = users.register(new Email("alice+" + UUID.randomUUID() + "@example.com"),
                "StrongTest123!", "Alice Integration", null);
        Currency c = Currency.COP;

        Wallet w = transactions.createWallet(u.id(), c, u.id());

        Optional<Wallet> found = wallets.findById(w.id());
        assertThat(found).isPresent();
        Wallet fetched = found.get();
        assertThat(fetched.userId()).isEqualTo(u.id());
        assertThat(fetched.currency()).isEqualTo(Currency.COP);
        assertThat(fetched.status()).isEqualTo(Wallet.Status.ACTIVE);
        assertThat(fetched.balance()).isEqualTo(Money.zero(Currency.COP));
        assertThat(fetched.available()).isEqualTo(Money.zero(Currency.COP));
        assertThat(fetched.reserved()).isEqualTo(Money.zero(Currency.COP));

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM wallets WHERE id = ?",
                Integer.class, w.id().uuid());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void wallet_credit_and_save_persists_balance_in_postgres() {
        User u = users.register(new Email("bob+" + UUID.randomUUID() + "@example.com"),
                "StrongTest123!", "Bob Integration", null);
        Wallet w = transactions.createWallet(u.id(), Currency.USD, u.id());

        Money seed = Money.of(new BigDecimal("5000.00"), Currency.USD);
        w.credit(seed, "integration seed");
        walletRepository.save(w);

        Wallet refreshed = wallets.getByIdOrThrow(w.id());
        assertThat(refreshed.balance()).isEqualTo(seed);
        assertThat(refreshed.available()).isEqualTo(seed);
    }
}
