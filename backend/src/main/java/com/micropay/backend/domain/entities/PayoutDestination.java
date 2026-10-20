package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.PayoutDestinationId;
import com.micropay.backend.domain.valueobjects.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * PayoutDestination — cuenta externa destino de retiros (bank account).
 *
 * SEGURIDAD: NUNCA almacenar el número de cuenta completo en claro fuera del proveedor.
 * Se persiste sólo el número enmascarado (`accountNumberMasked`, ej: "**** **** **** 1234")
 * + los últimos 4 dígitos. El token del proveedor (si aplica) va en `providerTokenRef`.
 *
 * INVARIANTES:
 *   BR#01: maskedLabel y accountNumberMasked requeridos.
 *   BR#02: sólo un destino puede ser default (isDefault=true) por usuario — orquestado en service.
 *   BR#03: `verified` sólo se activa tras validación del proveedor; un destino sin verificar no debería
 *          habilitar retiros (validado en service al iniciar Withdrawal).
 */
public final class PayoutDestination {

    public enum Type { BANK_ACCOUNT }

    private final PayoutDestinationId id;
    private final UserId userId;
    private final Type type;
    private String maskedLabel;
    private String bankName;
    private String accountType;
    private String accountNumberMasked;
    private String last4;
    private String holderName;
    private final String country;
    private final String currency;
    private boolean isDefault;
    private boolean verified;
    private final String providerTokenRef;
    private final Instant createdAt;
    private Instant updatedAt;

    private PayoutDestination(Builder b) {
        if (b.maskedLabel == null || b.maskedLabel.isBlank()) {
            throw new BusinessRuleViolationException("PAYOUT_DEST_LABEL_REQUIRED",
                    "PayoutDestination requiere maskedLabel");
        }
        if (b.accountNumberMasked == null || b.accountNumberMasked.isBlank()) {
            throw new BusinessRuleViolationException("PAYOUT_DEST_ACCOUNT_REQUIRED",
                    "PayoutDestination requiere accountNumberMasked");
        }
        this.id = b.id == null ? PayoutDestinationId.generate() : b.id;
        this.userId = Objects.requireNonNull(b.userId, "userId");
        this.type = b.type == null ? Type.BANK_ACCOUNT : b.type;
        this.maskedLabel = b.maskedLabel;
        this.bankName = b.bankName;
        this.accountType = b.accountType;
        this.accountNumberMasked = b.accountNumberMasked;
        this.last4 = b.last4;
        this.holderName = b.holderName;
        this.country = b.country;
        this.currency = b.currency;
        this.isDefault = b.isDefault;
        this.verified = b.verified;
        this.providerTokenRef = b.providerTokenRef;
        this.createdAt = b.createdAt == null ? Instant.now() : b.createdAt;
        this.updatedAt = b.updatedAt == null ? this.createdAt : b.updatedAt;
    }

    public static Builder builder() { return new Builder(); }

    /** Enmascara un número de cuenta dejando sólo los últimos 4 dígitos. */
    public static String mask(String rawAccount, String last4) {
        if (last4 == null || last4.isBlank()) return "**** **** **** ****";
        return "**** **** **** " + last4;
    }

    public static String last4Of(String rawAccount) {
        if (rawAccount == null) return null;
        String digits = rawAccount.replaceAll("\\D", "");
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }

    public void markVerified() { this.verified = true; touch(); }
    public void setDefault(boolean def) { this.isDefault = def; touch(); }
    public void updateLabel(String maskedLabel, String bankName, String accountType, String holderName) {
        if (maskedLabel != null && !maskedLabel.isBlank()) this.maskedLabel = maskedLabel;
        this.bankName = bankName;
        this.accountType = accountType;
        this.holderName = holderName;
        touch();
    }

    private void touch() { this.updatedAt = Instant.now(); }

    // ────────── Getters ──────────
    public PayoutDestinationId id() { return id; }
    public UserId userId() { return userId; }
    public Type type() { return type; }
    public String maskedLabel() { return maskedLabel; }
    public String bankName() { return bankName; }
    public String accountType() { return accountType; }
    public String accountNumberMasked() { return accountNumberMasked; }
    public String last4() { return last4; }
    public String holderName() { return holderName; }
    public String country() { return country; }
    public String currency() { return currency; }
    public boolean isDefault() { return isDefault; }
    public boolean verified() { return verified; }
    public String providerTokenRef() { return providerTokenRef; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public static final class Builder {
        private PayoutDestinationId id;
        private UserId userId;
        private Type type;
        private String maskedLabel;
        private String bankName;
        private String accountType;
        private String accountNumberMasked;
        private String last4;
        private String holderName;
        private String country;
        private String currency;
        private boolean isDefault;
        private boolean verified;
        private String providerTokenRef;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(PayoutDestinationId v){ this.id = v; return this; }
        public Builder userId(UserId v){ this.userId = v; return this; }
        public Builder type(Type v){ this.type = v; return this; }
        public Builder maskedLabel(String v){ this.maskedLabel = v; return this; }
        public Builder bankName(String v){ this.bankName = v; return this; }
        public Builder accountType(String v){ this.accountType = v; return this; }
        public Builder accountNumberMasked(String v){ this.accountNumberMasked = v; return this; }
        public Builder last4(String v){ this.last4 = v; return this; }
        public Builder holderName(String v){ this.holderName = v; return this; }
        public Builder country(String v){ this.country = v; return this; }
        public Builder currency(String v){ this.currency = v; return this; }
        public Builder isDefault(boolean v){ this.isDefault = v; return this; }
        public Builder verified(boolean v){ this.verified = v; return this; }
        public Builder providerTokenRef(String v){ this.providerTokenRef = v; return this; }
        public Builder createdAt(Instant v){ this.createdAt = v; return this; }
        public Builder updatedAt(Instant v){ this.updatedAt = v; return this; }

        public PayoutDestination build() { return new PayoutDestination(this); }
    }
}
