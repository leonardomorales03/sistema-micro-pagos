package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.WebhookDeliveryJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryJpaRepository extends JpaRepository<WebhookDeliveryJpa, UUID> {

    Page<WebhookDeliveryJpa> findByMerchantUserIdOrderByCreatedAtDesc(UUID merchantUserId, Pageable pageable);

    @Query("select d from WebhookDeliveryJpa d " +
           "where d.status = :status and d.nextRetryAt is not null and d.nextRetryAt <= :now " +
           "order by d.nextRetryAt asc")
    List<WebhookDeliveryJpa> findDue(@Param("status") WebhookDeliveryJpa.WebhookDeliveryStatusJpa status,
                                     @Param("now") Instant now,
                                     Pageable pageable);
}
