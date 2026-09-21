package com.micropay.backend.api.config;

import com.micropay.backend.api.controllers.CurrentUser;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.infrastructure.security.JwtService;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.UUID;

/**
 * Configuración MVC:
 *   1. CORS permisivo para desarrollo (frontend-user:3000, frontend-admin:5173)
 *   2. Custom resolver @CurrentUser → injecta UserId del token JWT en controllers
 */
@Component
public class ApiMvcConfig implements WebMvcConfigurer, HandlerMethodArgumentResolver {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:5173",
                                "https://localhost:3000", "https://localhost:5173")
                .allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS")
                .allowedHeaders("Authorization","Content-Type","Accept","X-Requested-With","X-Forwarded-For")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().equals(UserId.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof UserId uid) return uid;
        if (principal instanceof java.util.UUID u) return UserId.from(u);
        if (principal instanceof String s && !s.isBlank()) {
            try { return UserId.from(UUID.fromString(s)); }
            catch (IllegalArgumentException ignored) {}
        }
        return null;
    }
}
