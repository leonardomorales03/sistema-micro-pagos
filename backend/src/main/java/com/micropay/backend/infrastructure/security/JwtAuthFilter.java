package com.micropay.backend.infrastructure.security;

import com.micropay.backend.domain.valueobjects.UserId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro JWT (stateless). Ejecuta UNA vez por request:
 *   1. Extrae Authorization: Bearer <jwt> del header
 *   2. Valida firma + claims con JwtService (JJWT)
 *   3. Setea SecurityContext con Authentication:
 *        - principal = UserId (Typed UUID)
 *        - authorities = ROLE_* del claim "roles"
 *        - details = WebAuthenticationDetails (ip, sessionId)
 *      Si el token es inválido/expirado, NO setea contexto (request se procesa como
 *      anónimo, los endpoints @PreAuthorize retornan 403 automáticamente).
 *      NUNCA lanza excepción aquí — se maneja por ExceptionTranslationFilter.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER = "Bearer ";

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) { this.jwt = jwt; }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String jwtRaw = extractToken(req);
            if (jwtRaw != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                JwtService.ParsedToken parsed = jwt.parseAccessToken(jwtRaw);
                if (parsed != null) {
                    UserId principal = parsed.userId();
                    List<SimpleGrantedAuthority> authorities = parsed.roles().stream()
                            .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                            .distinct()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    if (log.isTraceEnabled()) {
                        log.trace("✅ JWT autorizado user={} roles={}", principal, authorities);
                    }
                }
            }
        } catch (Exception e) {
            // Nunca propagar — security context queda vacío (anónimo)
            log.debug("Error parseando JWT: {}", e.getMessage());
        }

        chain.doFilter(req, res);
    }

    private static String extractToken(HttpServletRequest req) {
        String h = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(h) || !h.startsWith(BEARER)) return null;
        String tok = h.substring(BEARER.length()).trim();
        return tok.isEmpty() ? null : tok;
    }
}
