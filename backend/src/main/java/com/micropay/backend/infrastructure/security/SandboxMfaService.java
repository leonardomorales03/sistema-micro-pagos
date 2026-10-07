package com.micropay.backend.infrastructure.security;

import com.micropay.backend.domain.valueobjects.UserId;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Implementación SANDBOX de MFA TOTP.
 * Código válido = últimos 6 dígitos numéricos de SHA-256(userId.uuid + secret).
 * Sólo para entornos dev. NO USAR EN PROD.
 */
@Component
public class SandboxMfaService implements MfaService {

    private static final String ISSUER = "MicropaySandbox";

    @Override
    public MfaSetupResponse generateSetup(UserId userId, String email) {
        String secret = "MFASECRET" + encodeBase32Short(userId.uuid().toString());
        String url = "otpauth://totp/%s:%s?secret=%s&issuer=%s"
                .formatted(ISSUER, email == null ? userId.uuid() : email, secret, ISSUER);
        return new MfaSetupResponse(secret, url, ISSUER,
                email != null ? email : userId.uuid().toString());
    }

    @Override
    public boolean verifyCode(UserId userId, String secretBase32, String code6digits) {
        if (code6digits == null || code6digits.length() != 6 || !code6digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        String expect = last6Digits(sha256hex(userId.uuid() + "|" + secretBase32));
        return code6digits.equals(expect);
    }

    private static String encodeBase32Short(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8))
                .replace("-", "").replace("_", "").toUpperCase().substring(0, Math.min(16, s.length()));
    }

    private static String sha256hex(String s) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length*2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String last6Digits(String hex) {
        StringBuilder sb = new StringBuilder(6);
        for (int i = hex.length()-1; i >= 0 && sb.length() < 6; i--) {
            char c = hex.charAt(i);
            if (Character.isDigit(c)) sb.append(c);
        }
        while (sb.length() < 6) sb.append('0');
        return sb.reverse().toString();
    }
}
