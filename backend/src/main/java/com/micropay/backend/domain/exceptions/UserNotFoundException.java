package com.micropay.backend.domain.exceptions;

import java.util.UUID;

public final class UserNotFoundException extends DomainException {

    public UserNotFoundException(UUID userId) {
        super("USER_NOT_FOUND", "Usuario con id '%s' no existe".formatted(userId));
    }

    public UserNotFoundException(String email) {
        super("USER_NOT_FOUND", "Usuario con email '%s' no existe".formatted(email));
    }
}
