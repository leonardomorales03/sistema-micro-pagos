package com.micropay.backend.infrastructure.fees;

import com.micropay.backend.domain.ports.out.FeeEnginePort;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.FeeContext;
import com.micropay.backend.domain.valueobjects.FeeLine;
import com.micropay.backend.domain.valueobjects.FeeOperation;
import com.micropay.backend.domain.valueobjects.FeeType;
import com.micropay.backend.domain.valueobjects.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Adaptador del FeeEngine (Requisito 30.1/30.3/30.4).
 * <p>
 * Reglas configurables por properties (namespace {@code micropay.fees.*}) y versionadas:
 * cambiar los valores de configuración obliga a subir {@code micropay.fees.version} para que
 * las transacciones históricas sigan referenciando la regla que aplicó en su momento.
 * <p>
 * Defaults (todos sin comisión) preservan el comportamiento previo del sistema; al activar
 * una tarifa el cargo se aplica atómicamente en la misma transacción que la origina.
 *  - transfer-p2p: free por defecto (Requisito 30.3).
 *  - topup: % del costo del proveedor + markup.
 *  - withdrawal: flat + fee del proveedor.
 *  - payment-request: MDR (%).
 *  - refund: no se cobra comisión por reembolsar (fee original no reembolsable por defecto).
 */
@Component
public class ConfigurableFeeEngineAdapter implements FeeEnginePort {

    private final String ruleVersion;
    private final BigDecimal transferP2pPercentage;
    private final BigDecimal topupPercentage;
    private final BigDecimal withdrawalFlat;
    private final BigDecimal paymentRequestMdrPercentage;
    private final BigDecimal subscriptionPercentage;

    public ConfigurableFeeEngineAdapter(
            @Value("${micropay.fees.version:v1-default}") String ruleVersion,
            @Value("${micropay.fees.transfer-p2p.percentage:0}") BigDecimal transferP2pPercentage,
            @Value("${micropay.fees.topup.percentage:0}") BigDecimal topupPercentage,
            @Value("${micropay.fees.withdrawal.flat:0}") BigDecimal withdrawalFlat,
            @Value("${micropay.fees.payment-request.mdr-percentage:0}") BigDecimal paymentRequestMdrPercentage,
            @Value("${micropay.fees.subscription.percentage:0}") BigDecimal subscriptionPercentage) {
        this.ruleVersion = ruleVersion;
        this.transferP2pPercentage = transferP2pPercentage;
        this.topupPercentage = topupPercentage;
        this.withdrawalFlat = withdrawalFlat;
        this.paymentRequestMdrPercentage = paymentRequestMdrPercentage;
        this.subscriptionPercentage = subscriptionPercentage;
    }

    @Override
    public List<FeeLine> calculateFees(FeeContext context) {
        if (context == null || context.amount() == null) {
            return List.of();
        }
        Currency cur = context.currency();
        Money base = context.amount();

        return switch (context.operation()) {
            case TRANSFER_P2P -> percentageFee(base, cur, transferP2pPercentage, FeeOperation.TRANSFER_P2P);
            case TOPUP -> percentageFee(base, cur, topupPercentage, FeeOperation.TOPUP);
            case PAYMENT_REQUEST -> percentageFee(base, cur, paymentRequestMdrPercentage, FeeOperation.PAYMENT_REQUEST);
            case SUBSCRIPTION -> percentageFee(base, cur, subscriptionPercentage, FeeOperation.SUBSCRIPTION);
            case WITHDRAWAL -> flatFee(cur, withdrawalFlat, FeeOperation.WITHDRAWAL);
            case REFUND -> List.of(); // comisión de reembolso 0 por defecto (el fee original no se devuelve)
        };
    }

    private List<FeeLine> percentageFee(Money base, Currency cur, BigDecimal percentage, FeeOperation op) {
        if (percentage == null || percentage.signum() <= 0) {
            return List.of();
        }
        Money fee = base.multiply(percentage);
        if (!fee.isPositive()) {
            return List.of();
        }
        return List.of(new FeeLine(fee, FeeType.PERCENTAGE, ruleVersion,
                "%s: %.4f%% de %s".formatted(op.description(), percentage.movePointRight(2), base)));
    }

    private List<FeeLine> flatFee(Currency cur, BigDecimal flat, FeeOperation op) {
        if (flat == null || flat.signum() <= 0) {
            return List.of();
        }
        Money fee = Money.of(flat, cur);
        if (!fee.isPositive()) {
            return List.of();
        }
        return List.of(new FeeLine(fee, FeeType.FLAT, ruleVersion,
                "%s: comisión fija %s".formatted(op.description(), fee)));
    }
}
