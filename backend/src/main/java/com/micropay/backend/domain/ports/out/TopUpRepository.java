package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.TopUp;
import com.micropay.backend.domain.valueobjects.TopUpId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;

import java.util.List;
import java.util.Optional;

public interface TopUpRepository {

    TopUp save(TopUp topUp);

    Optional<TopUp> findById(TopUpId id);

    Optional<TopUp> findByProviderRef(String providerRef);

    List<TopUp> listByWallet(WalletId walletId, int page, int pageSize);

    List<TopUp> listByUser(UserId userId, int page, int pageSize);
}
