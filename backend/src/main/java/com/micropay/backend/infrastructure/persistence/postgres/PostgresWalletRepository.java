package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.WalletJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.WalletJpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class PostgresWalletRepository implements WalletRepository {

    private final WalletJpaRepository jpa;

    public PostgresWalletRepository(WalletJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Wallet save(Wallet wallet) {
        WalletJpa saved = jpa.save(toJpa(wallet));
        return toDomain(saved);
    }

    @Override
    public Optional<Wallet> findById(WalletId id) {
        return jpa.findById(id.uuid()).map(this::toDomain);
    }

    @Override
    public Optional<Wallet> findByUserAndCurrency(UserId userId, Currency currency) {
        return jpa.findByUserIdAndCurrency(userId.uuid(), currency.isoCode())
                .map(this::toDomain);
    }

    @Override
    public List<Wallet> listByUser(UserId userId) {
        return jpa.findByUserId(userId.uuid()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean exists(WalletId id) {
        return jpa.existsById(id.uuid());
    }

    // ──────────────── MAPPERS Domain ↔ JPA ────────────────

    Wallet toDomain(WalletJpa j) {
        if (j == null) return null;
        Currency cur = Currency.valueOf(j.getCurrency());
        Money balanceJpa = Money.of(j.getBalance() != null ? j.getBalance() : BigDecimal.ZERO, cur);
        Money reservedJpa = Money.of(j.getReserved() != null ? j.getReserved() : BigDecimal.ZERO, cur);
        long versionJpa = j.getVersion();
        Wallet w = Wallet.builder()
                .id(WalletId.from(j.getId()))
                .userId(UserId.from(j.getUserId()))
                .currency(cur)
                .status(Wallet.Status.valueOf(j.getStatus().name()))
                .createdAt(j.getCreatedAt())
                .updatedAt(j.getUpdatedAt())
                .createdBy(j.getCreatedBy())
                .build();
        setField(w, "balance", balanceJpa);
        setField(w, "reserved", reservedJpa);
        setField(w, "version", versionJpa);
        return w;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo settear " + name + " en " + target.getClass().getSimpleName(), e);
        }
    }

    WalletJpa toJpa(Wallet d) {
        if (d == null) return null;
        WalletJpa j = new WalletJpa();
        j.setId(d.id().uuid());
        j.setUserId(d.userId().uuid());
        j.setCurrency(d.currency().isoCode());
        j.setBalance(d.balance().amount());
        j.setReserved(d.reserved().amount());
        j.setStatus(WalletJpa.WalletStatus.valueOf(d.status().name()));
        j.setCreatedAt(d.createdAt());
        j.setUpdatedAt(d.updatedAt());
        j.setCreatedBy(d.createdBy());
        j.setVersion(d.version());
        return j;
    }
}
