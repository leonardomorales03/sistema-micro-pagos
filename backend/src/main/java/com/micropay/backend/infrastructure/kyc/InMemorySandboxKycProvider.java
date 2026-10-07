package com.micropay.backend.infrastructure.kyc;

import com.micropay.backend.domain.ports.out.KycProviderPort;
import com.micropay.backend.domain.valueobjects.DocumentNumber;
import com.micropay.backend.domain.valueobjects.KycLevel;
import com.micropay.backend.domain.valueobjects.UserId;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Primary
public class InMemorySandboxKycProvider implements KycProviderPort {

    private final Map<UserId, List<KycSubmissionResult>> store = new ConcurrentHashMap<>();

    @Override
    public KycSubmissionResult submit(UserId userId, DocumentNumber document, byte[] payload) {
        List<KycSubmissionResult> list = store.computeIfAbsent(userId, k -> new ArrayList<>());
        long approved = list.stream()
                .filter(r -> r.status() == KycSubmissionResult.Status.APPROVED)
                .count();
        KycLevel achieved = approved == 0 ? KycLevel.LEVEL_1 : KycLevel.LEVEL_2;
        KycSubmissionResult r = new KycSubmissionResult(
                "SANDBOX-" + UUID.randomUUID(),
                document,
                KycSubmissionResult.Status.APPROVED,
                achieved,
                null,
                Instant.now()
        );
        list.add(r);
        return r;
    }

    @Override
    public List<KycSubmissionResult> listByUser(UserId userId) {
        return List.copyOf(store.getOrDefault(userId, List.of()));
    }
}
