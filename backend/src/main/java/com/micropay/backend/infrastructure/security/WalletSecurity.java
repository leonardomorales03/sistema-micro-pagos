package com.micropay.backend.infrastructure.security;

import com.micropay.backend.application.services.WalletService;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Bean de seguridad para @PreAuthorize SpEL.
 * Uso en controller: @PreAuthorize("hasRole('ROLE_ADMIN') or @walletSecurity.isOwner(#walletId, authentication)")
 */
@Component("walletSecurity")
public class WalletSecurity {

    private final WalletService wallets;

    public WalletSecurity(WalletService wallets) { this.wallets = wallets; }

    public boolean isOwner(String walletId, Authentication auth) {
        if (walletId == null || auth == null) return false;
        try {
            UUID wId = UUID.fromString(walletId);
            Object p = auth.getPrincipal();
            UserId uid = null;
            if (p instanceof UserId u) uid = u;
            else if (p instanceof UUID u) uid = UserId.from(u);
            else if (p instanceof String s && !s.isBlank()) uid = UserId.fromString(s);
            if (uid == null) return false;
            final UserId currentUid = uid;
            return wallets.findById(WalletId.from(wId))
                    .map(w -> w.userId().equals(currentUid))
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isOwner(UUID walletId, Authentication auth) {
        return walletId != null && isOwner(walletId.toString(), auth);
    }
}
