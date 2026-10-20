package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.Withdrawal;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import com.micropay.backend.domain.valueobjects.WithdrawalId;

import java.util.List;
import java.util.Optional;

public interface WithdrawalRepository {

    Withdrawal save(Withdrawal withdrawal);

    Optional<Withdrawal> findById(WithdrawalId id);

    Optional<Withdrawal> findByProviderRef(String providerRef);

    List<Withdrawal> listByWallet(WalletId walletId, int page, int pageSize);

    List<Withdrawal> listByUser(UserId userId, int page, int pageSize);
}
