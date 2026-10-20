package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.PaymentRequest;
import com.micropay.backend.domain.valueobjects.PaymentRequestId;
import com.micropay.backend.domain.valueobjects.ShortCode;
import com.micropay.backend.domain.valueobjects.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia de PaymentRequests.
 * {@code findByShortCode} es único (BR#05) y se usa para resolver el link/QR público.
 */
public interface PaymentRequestRepository {

    PaymentRequest save(PaymentRequest paymentRequest);

    Optional<PaymentRequest> findById(PaymentRequestId id);

    Optional<PaymentRequest> findByShortCode(ShortCode shortCode);

    boolean existsByShortCode(ShortCode shortCode);

    List<PaymentRequest> listByMerchant(UserId merchantUserId, int page, int pageSize);
}
