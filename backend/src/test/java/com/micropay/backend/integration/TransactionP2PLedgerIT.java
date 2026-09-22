package com.micropay.backend.integration;

import com.micropay.backend.AbstractIntegrationTest;
import com.micropay.backend.application.services.TransactionService;
import com.micropay.backend.application.services.UserService;
import com.micropay.backend.application.services.WalletService;
import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.ports.out.TransactionRepository;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.valueobjects.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionP2PLedgerIT extends AbstractIntegrationTest {

    @Autowired UserService users;
    @Autowired WalletService wallets;
    @Autowired TransactionService transactions;
    @Autowired WalletRepository walletRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void transferP2P_full_complete_creates_two_account_moves_and_preserves_balances_ACID() {
        User a = users.register(new Email("alice-tx+" + UUID.randomUUID() + "@example.com"),
                "StrongTest123!", "Alice Tx", null);
        User b = users.register(new Email("bob-tx+" + UUID.randomUUID() + "@example.com"),
                "StrongTest123!", "Bob Tx", null);
        Currency c = Currency.COP;

        Wallet wA = transactions.createWallet(a.id(), c, a.id());
        Wallet wB = transactions.createWallet(b.id(), c, b.id());
        Money aSeed = Money.of(new BigDecimal("10000000"), c);
        Money bSeed = Money.of(new BigDecimal("2000000"), c);
        Money transfer = Money.of(new BigDecimal("1500000"), c);
        wA.credit(aSeed, "seedA"); walletRepository.save(wA);
        wB.credit(bSeed, "seedB"); walletRepository.save(wB);
        Money sumBefore = aSeed.add(bSeed);

        Transaction tx = transactions.transferP2P(wA.id(), wB.id(), transfer, a.id(), "integration tx copia");

        assertThat(tx.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(tx.grossAmount()).isEqualTo(transfer);
        assertThat(tx.accountMoves()).hasSize(2);
        List<AccountMove> debit = tx.accountMoves().stream().filter(AccountMove::isDebit).toList();
        List<AccountMove> credit = tx.accountMoves().stream().filter(AccountMove::isCredit).toList();
        assertThat(debit).hasSize(1);
        assertThat(credit).hasSize(1);
        assertThat(debit.get(0).walletId()).isEqualTo(wA.id());
        assertThat(credit.get(0).walletId()).isEqualTo(wB.id());
        Money zero = debit.get(0).amount().add(credit.get(0).amount());
        assertThat(zero).isEqualTo(Money.zero(c));

        Wallet wAFinal = wallets.getByIdOrThrow(wA.id());
        Wallet wBFinal = wallets.getByIdOrThrow(wB.id());
        Money expectedA = aSeed.subtract(transfer);
        Money expectedB = bSeed.add(transfer);
        assertThat(wAFinal.balance()).isEqualTo(expectedA);
        assertThat(wBFinal.balance()).isEqualTo(expectedB);
        assertThat(wAFinal.balance().add(wBFinal.balance())).isEqualTo(sumBefore);
        assertThat(debit.get(0).balanceAfter()).isEqualTo(expectedA);
        assertThat(credit.get(0).balanceAfter()).isEqualTo(expectedB);

        Integer ledgerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_moves WHERE transaction_id = ?",
                Integer.class, tx.id().uuid());
        assertThat(ledgerCount).isEqualTo(2);
    }
}
