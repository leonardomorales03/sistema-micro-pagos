package com.micropay.backend.infrastructure.security;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Spring Security configuración oficial:
 *   • Stateless JWT (no SessionCreationPolicy.STATELESS)
 *   • CSRF disabled (stateless no requiere CSRF tokens para API)
 *   • CORS habilitado (mvc config: origins frontend 3000/5173)
 *   • Endpoints públicos: /api/v1/auth/login, /api/v1/auth/refresh, /api/v1/users/register,
 *                          /health, /healthz, /api/v1/system/**, /v3/api-docs/**, /swagger-ui/**
 *   • Todo lo demás: autenticado JWT Bearer
 *   • @EnableMethodSecurity → @PreAuthorize en controllers (ROLE_ADMIN/propietario wallet)
 *   • HSTS, X-Content-Type-Options, FrameOptions DENY, XSS Filter enabled
 *   • JwtAuthFilter → extrae token Bearer, parsea, setea contexto (UsernamePasswordAuthenticationToken)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Access token JWT (HS512) — obtener vía POST /api/v1/auth/login"
)
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> {}) // habilitado global por ApiMvcConfig
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .xssProtection(x -> x.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .contentSecurityPolicy(cps -> cps.policyDirectives("default-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                        .frameOptions(fo -> fo.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true).preload(true).maxAgeInSeconds(31536000))
                        .contentTypeOptions(cto -> {})
                )
                .authorizeHttpRequests(ar -> ar
                        // 1. Auth endpoints públicos (login, refresh, register, system health)
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/users/register",
                                "/api/v1/users/{id}/verify-email",
                                "/api/v1/system/health",
                                "/api/v1/system/healthz",
                                "/api/v1/system/info",
                                "/health",
                                "/healthz",
                                "/info",
                                "/error"
                        ).permitAll()
                        // 2. Swagger / SpringDoc público
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        // 3. Actuator (health, info permitido; resto requiere ROLE_ADMIN o network interno)
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // 4. Todo lo demás requiere autenticación JWT Bearer
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /**
     * PasswordEncoder oficial — BCrypt cost 12 (seguridad + performance).
     * NOTA: AuthService compara hash directamente (String equals) en modo stub.
     *       Producción sustituye por passwordEncoder.matches(password, hash) para
     *       las credenciales reales.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
