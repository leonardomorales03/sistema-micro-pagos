# ADR-005 — JWT Access Token HttpOnly Cookie Refresh Token

| Campo       | Valor                                      |
|-----------|--------------------------------------------|
| **ID**     | ADR-005                                   |
| **Estado** | Aceptado — 2026-09-21                   |
| **Autor**  | MicroPagos Architecture Team              |
| **Área**  | Seguridad / AuthN/AuthZ                |

---

## 1. Contexto

Autenticación para:
- **Dashboard usuarios finales JWT
- **Backoffice admin same auth usuarios + admins.
- **Sesiones persistentes 7 días.
- **Riego mínimo de **XSS** y **CSRF** tokens por cookieHttpOnly mitigaciones.

Opciones evaluadas:
1.  **JWT en localStorage → XSS huge riesgo.
2.  **JWT + Opaque Token en memoria + Refresh token httpOnly Cookie.
3.  **Spring Session JDBC → más overhead.
4.  **Access JWT Bearer + Refresh HttpOnly Cookie ← elegida ←

## 2. Decisión| Componente | Ubicación | Duración |
|---|---|---|
| JWT Access Token | Cabecera `Authorization: Bearer ...` | 15 min 900,000 ms |
| **Refresh Token | Cookie HttpOnly + Secure + SameSite=Lax | 7 días |
| **Nombre Cookie Refresh | `MICROPAY_REFRESH_TOKEN` |  |
| **JWT Issuer | `micropay-backend` |  |
| **Firma** | HMAC SHA-256 HS256 256-bit secret |

### Spring SecurityFilterChain:
```
/api/v1/auth/**       → PermitAll
/api/v1/public/** → permitAll
/api/v1/admin/**  → ROLE_ADMIN
/actuator/**    → permitAll
swagger-ui     → permitAll dev/sandbox, authenticated prod
/_sandbox/**   → sandbox only perfil sandbox
R38: Refresh rotación Refresh token
R39: Invalidación Logout.
```

## 3. Consecuencias

### Positivas
✅ HttpOnly Refresh Cookie no legible JS → **mitiga **XSS
✅ Access Token 15 min → vida corta
✅ Rotación tokens cada uso
✅ CSRF mitigado SameSite=Lax + doble submitCookie no cross-site.
✅ Compatible **CORS: origins especificado **, no wildcard.

### Negativas
⚠️ Refresh token comprometido con Secure; CSRF ataques.
⚠️ Logout invalidación lista negra (whitelist en Redis/SQL sessions revocados.
⚠️ 2 pasos extra en `refresh_token` endpoint CSRF → rotar.

### Mitigación
⚖️ **CSRF Token BREACH spring.security.csrf.token.repository CookieCsrfTokenRepository ⚖️X-XSS-Protection, CSP frame-ancestors headers.

