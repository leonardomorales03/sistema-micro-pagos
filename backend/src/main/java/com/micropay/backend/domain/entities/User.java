package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.DocumentNumber;
import com.micropay.backend.domain.valueobjects.Email;
import com.micropay.backend.domain.valueobjects.KycLevel;
import com.micropay.backend.domain.valueobjects.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * User — Aggregate Root de la cuenta usuario.
 * <p>
 * Contiene identidad, email, roles (ROLE_USER por defecto, ROLE_ADMIN opcional),
 * nivel KYC y documentos KYC asociados.
 * <p>
 * Reglas negocio:
 *   - Email siempre lowercase, único (invariante infraestructura)
 *   - password_hash NUNCA viaja en getters/métodos públicos (solo validar)
 *   - KYC level sólo puede subir (nunca bajar sin ruta específica downgrade-admin-only)
 *   - Estado BLOCKED impide toda operación que no sea admin unlock
 *   - referral code generado una sola vez
 */
public final class User {

    public enum Status { ACTIVE, BLOCKED, PENDING_EMAIL_VERIFICATION, DELETED }

    private final UserId id;
    private Email email;
    private String fullName;
    private String phoneNumber;
    private final String passwordHash;
    private KycLevel kycLevel;
    private Status status;
    private boolean emailVerified;
    private final String referralCode;
    private UserId referredBy;
    private final List<String> roles;
    private final List<DocumentNumber> kycDocuments;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(Builder b) {
        this.id = b.id;
        this.email = b.email;
        this.fullName = b.fullName;
        this.phoneNumber = b.phoneNumber;
        this.passwordHash = b.passwordHash;
        this.kycLevel = b.kycLevel;
        this.status = b.status;
        this.emailVerified = b.emailVerified;
        this.referralCode = b.referralCode;
        this.referredBy = b.referredBy;
        this.roles = new ArrayList<>(b.roles);
        this.kycDocuments = new ArrayList<>(b.kycDocuments);
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
    }

    public static User registerNew(Email email, String passwordHash, String fullName, UserId referredBy) {
        if (passwordHash == null || passwordHash.length() < 30) {
            throw new BusinessRuleViolationException("PASSWORD_HASH_INVALID",
                    "password_hash debe ser un hash bcrypt/argon (>= 30 chars)");
        }
        return builder()
                .email(email)
                .fullName(fullName)
                .passwordHash(passwordHash)
                .kycLevel(KycLevel.LEVEL_0)
                .status(Status.PENDING_EMAIL_VERIFICATION)
                .emailVerified(false)
                .referralCode(generateReferralCode())
                .referredBy(referredBy)
                .addRole("ROLE_USER")
                .build();
    }

    /** Requiere que el usuario no esté bloqueado para operar. */
    public void assertCanOperate(String operation) {
        if (this.status == Status.BLOCKED || this.status == Status.DELETED) {
            throw new BusinessRuleViolationException("USER_NOT_ACTIVE",
                    "Usuario %s - %s no permitido en estado %s".formatted(id, operation, this.status));
        }
    }

    /** KYC upgrade. Sólo puede subir de nivel — no downgrade. */
    public void upgradeKycLevel(KycLevel target, UUID byAdminId) {
        assertCanOperate("kyc upgrade");
        if (!target.atLeast(this.kycLevel) || target == this.kycLevel) {
            throw new BusinessRuleViolationException("KYC_DOWNGRADE_NOT_ALLOWED",
                    "Nivel KYC actual %s, no se puede degradar a %s".formatted(this.kycLevel, target));
        }
        this.kycLevel = target;
        touch();
    }

    public void addKycDocument(DocumentNumber doc) {
        if (this.kycDocuments.contains(doc)) {
            throw new BusinessRuleViolationException("KYC_DOC_ALREADY_PRESENT",
                    "Documento %s ya está registrado".formatted(doc.number()));
        }
        this.kycDocuments.add(doc);
        touch();
    }

    public void verifyEmail() { this.emailVerified = true; this.status = Status.ACTIVE; touch(); }
    public void block(String reason, UUID byAdminId) { this.status = Status.BLOCKED; touch(); }
    public void unblock(UUID byAdminId) { this.status = Status.ACTIVE; touch(); }

    public void changeEmail(Email newEmail) {
        if (newEmail == null) throw new IllegalArgumentException("newEmail");
        this.email = newEmail;
        this.emailVerified = false;
        touch();
    }

    private static String generateReferralCode() {
        // 8 chars alfanuméricos, uppercase, sin 0/O/1/I
        String cs = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(8);
        java.security.SecureRandom r = new java.security.SecureRandom();
        for (int i = 0; i < 8; i++) sb.append(cs.charAt(r.nextInt(cs.length())));
        return sb.toString();
    }

    private void touch() { this.updatedAt = Instant.now(); }

    // ────────── GETTERS (passwordHash NUNCA expuesto) ──────────
    public UserId id() { return id; }
    public Email email() { return email; }
    public String fullName() { return fullName; }
    public String phoneNumber() { return phoneNumber; }
    public KycLevel kycLevel() { return kycLevel; }
    public Status status() { return status; }
    public boolean emailVerified() { return emailVerified; }
    public String referralCode() { return referralCode; }
    public UserId referredBy() { return referredBy; }
    public List<String> roles() { return Collections.unmodifiableList(roles); }
    public List<DocumentNumber> kycDocuments() { return Collections.unmodifiableList(kycDocuments); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    /** Valida hash de contraseña — SÓLO lectura, NUNCA retorna el hash. */
    public boolean passwordMatches(String inputPasswordHash) {
        return this.passwordHash != null && this.passwordHash.equals(inputPasswordHash);
    }
    public boolean hasRole(String role) { return this.roles.contains(role); }

    // ────────── BUILDER ──────────
    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private UserId id = UserId.generate();
        private Email email;
        private String fullName;
        private String phoneNumber;
        private String passwordHash;
        private KycLevel kycLevel = KycLevel.LEVEL_0;
        private Status status = Status.PENDING_EMAIL_VERIFICATION;
        private boolean emailVerified = false;
        private String referralCode;
        private UserId referredBy;
        private final List<String> roles = new ArrayList<>();
        private final List<DocumentNumber> kycDocuments = new ArrayList<>();
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();

        public Builder id(UserId v) { this.id = v; return this; }
        public Builder email(Email v) { this.email = v; return this; }
        public Builder fullName(String v) { this.fullName = v; return this; }
        public Builder phoneNumber(String v) { this.phoneNumber = v; return this; }
        public Builder passwordHash(String v) { this.passwordHash = v; return this; }
        public Builder kycLevel(KycLevel v) { this.kycLevel = v; return this; }
        public Builder status(Status v) { this.status = v; return this; }
        public Builder emailVerified(boolean v) { this.emailVerified = v; return this; }
        public Builder referralCode(String v) { this.referralCode = v; return this; }
        public Builder referredBy(UserId v) { this.referredBy = v; return this; }
        public Builder addRole(String r) { this.roles.add(r); return this; }
        public Builder addKycDocument(DocumentNumber d) { this.kycDocuments.add(d); return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public User build() {
            if (email == null) throw new IllegalArgumentException("email requerido");
            if (passwordHash == null) throw new IllegalArgumentException("passwordHash requerido");
            return new User(this);
        }
    }
}
