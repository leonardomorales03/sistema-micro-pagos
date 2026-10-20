package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.PayoutDestination;
import com.micropay.backend.domain.valueobjects.PayoutDestinationId;
import com.micropay.backend.domain.valueobjects.UserId;

import java.util.List;
import java.util.Optional;

public interface PayoutDestinationRepository {

    PayoutDestination save(PayoutDestination destination);

    Optional<PayoutDestination> findById(PayoutDestinationId id);

    List<PayoutDestination> listByUser(UserId userId);

    Optional<PayoutDestination> findDefaultByUser(UserId userId);

    Optional<PayoutDestination> findByProviderTokenRef(String providerTokenRef);

    void deleteById(PayoutDestinationId id);

    long countByUser(UserId userId);
}
