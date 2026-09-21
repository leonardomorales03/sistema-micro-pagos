package com.micropay.backend.infrastructure.payments;

import com.micropay.backend.domain.ports.out.PaymentProviderAdapter;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.WalletId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub / Sandbox Stub del proveedor pagos (Stripe simulado).
 * <p>
 * Activar con: spring.profiles.active=dev,test,sandbox
 * — SIEMPRE retorna PaymentIntent exitoso status = "requires_capture"
 * — NO llama servicios externos — ideal para tests sin API keys de Stripe.
 * <p>
 * Producción implementa reemplazará este bean con {@code RealStripePaymentProviderAdapter}
 * (cuando aplique.
 */
@Component
@Profile({"dev", "test", "sandbox"})
public class StubStripePaymentProviderAdapter implements PaymentProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(StubStripePaymentProviderAdapter.class);
    private static final String PREFIX_INTENT = "pi_stub_";
    private static final String PREFIX_PAYOUT = "po_stub_";

    @Override
    public PaymentIntentResult createTopUpIntent(Money amount, WalletId walletId) {
        String id = PREFIX_INTENT + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String clientSecret = id + "_secret_" + walletId;
        log.info("[STUB STRIPE] TopUp Intent creado id={} amount={} wallet={}", id, amount, walletId);
        return new PaymentIntentResult(id, clientSecret, "requires_capture", amount);
    }

    @Override
    public PayoutResult createWithdrawPayout(Money amount, WalletId fromWalletId, String externalAccountId) {
        String id = PREFIX_PAYOUT + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        log.info("[STUB STRIPE] Payout retiro id={} amount={} from={} account={}",
                id, amount, fromWalletId, externalAccountId);
        // Simula delay aleatorio status = "pending" como si payout bancario.
        return new PayoutResult(id, "pending", amount);
    }

    @Override
    public boolean validateWebhook(String payload, String signatureHeader) {
        // Stub devuelve true SIEMPRE (modo sandbox/dev NO valida firma Stripe-Signature
        // producción implementa hmacSHA256.compare
        log.debug("[STUB STRIPE] Webhook validación firma skip (stub mode), payloadLen={}",
                payload == null ? 0 : payload.length());
        return true;
    }
}
