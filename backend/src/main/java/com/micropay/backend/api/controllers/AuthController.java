package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.AuthDtos;
import com.micropay.backend.application.services.AuthService;
import com.micropay.backend.domain.exceptions.UserNotFoundException;
import com.micropay.backend.domain.ports.in.AuthUseCases;
import com.micropay.backend.domain.valueobjects.Email;
import com.micropay.backend.infrastructure.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Autenticación: login, refresh, logout (JWT Bearer + Refresh HttpOnly Cookie)")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthUseCases auth;
    private final JwtService jwt;

    public AuthController(AuthService auth, JwtService jwt) {
        this.auth = auth;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    @Operation(summary = "Login usuario",
               description = "Retorna access token (JWT Bearer) + setea refresh token HttpOnly cookie.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credenciales válidas",
                    content = @Content(schema = @Schema(implementation = AuthDtos.AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas o usuario bloqueado"),
            @ApiResponse(responseCode = "422", description = "Email formato inválido / campos faltantes")
    })
    public ResponseEntity<AuthDtos.AuthResponse> login(@RequestBody AuthDtos.LoginRequest req,
                                                        HttpServletRequest servlet,
                                                        HttpServletResponse response) {
        String ua = req.userAgent() != null ? req.userAgent() : servlet.getHeader(HttpHeaders.USER_AGENT);
        String ip = clientIp(servlet);
        AuthUseCases.AuthResult r = auth.login(
                new Email(req.email()),
                req.password(),
                req.deviceFingerprint(),
                ua, ip
        );
        setRefreshCookie(response, r.refreshTokenRaw(), false);
        return ResponseEntity.ok(new AuthDtos.AuthResponse(
                r.accessToken(),
                jwt.accessTokenTtlMillis() / 1000,
                "Bearer",
                r.userId().uuid(),
                r.email(),
                r.roles()
        ));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token",
               description = "Usa cookie HttpOnly o body refresh_token. Rotación obligatoria de refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens renovados"),
            @ApiResponse(responseCode = "401", description = "Refresh inválido, expirado o revocado")
    })
    public ResponseEntity<AuthDtos.AuthResponse> refresh(@RequestBody(required = false) AuthDtos.RefreshRequest body,
                                                         @CookieValue(name = "#{@'jjwtJwtService'.refreshCookieName}", required = false) String cookieRefresh,
                                                         HttpServletRequest servlet,
                                                         HttpServletResponse response) {
        String raw = body != null && body.refreshToken() != null ? body.refreshToken() : cookieRefresh;
        if (raw == null || raw.isBlank()) {
            throw new com.micropay.backend.domain.exceptions.BusinessRuleViolationException(
                    "REFRESH_MISSING", "Falta refresh token (cookie o body)");
        }
        String ua = servlet.getHeader(HttpHeaders.USER_AGENT);
        String ip = clientIp(servlet);
        AuthUseCases.AuthResult r = auth.refresh(raw, null, ua, ip);
        setRefreshCookie(response, r.refreshTokenRaw(), false);
        return ResponseEntity.ok(new AuthDtos.AuthResponse(
                r.accessToken(),
                jwt.accessTokenTtlMillis() / 1000,
                "Bearer",
                r.userId().uuid(),
                r.email(),
                r.roles()
        ));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout sesión actual", description = "Revoca refresh token actual (cookie + body)")
    @ApiResponses({ @ApiResponse(responseCode = "204", description = "Logout exitoso") })
    public ResponseEntity<Void> logout(@RequestBody(required = false) AuthDtos.LogoutRequest body,
                                       @CookieValue(name = "#{@'jjwtJwtService'.refreshCookieName}", required = false) String cookieRefresh,
                                       HttpServletResponse response) {
        String raw = body != null && body.refreshToken() != null ? body.refreshToken() : cookieRefresh;
        auth.logout(raw);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    // ────────── helpers cookies ──────────

    private void setRefreshCookie(HttpServletResponse r, String refreshRaw, boolean secureOnly) {
        String sameSite = secureOnly ? "Strict" : "Lax";
        ResponseCookie c = ResponseCookie.from(jwt.refreshCookieName(), refreshRaw)
                .httpOnly(true)
                .secure(secureOnly)
                .path("/api/v1/auth")
                .maxAge(java.time.Duration.ofMillis(jwt.refreshTokenTtlMillis()))
                .sameSite(sameSite)
                .build();
        r.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }

    private void clearRefreshCookie(HttpServletResponse r) {
        ResponseCookie c = ResponseCookie.from(jwt.refreshCookieName(), "")
                .httpOnly(true).secure(false).path("/api/v1/auth")
                .maxAge(0).sameSite("Lax").build();
        r.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
