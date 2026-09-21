# ADR-006 — OpenAPI Spec → Cliente TypeScript Autogenerado

| Campo       | Valor                                      |
|-----------|--------------------------------------------|
| **ID**     | ADR-006                                   |
| **Estado** | Aceptado — 2026-09-21                   |
| **Autor**  | MicroPagos Architecture Team              |
| **Área**  | Integración Backend ↔ Frontend                |

---

## 1. Contexto

Dos frontends separados React 18 + TypeScript requieren:
- Consumir **40+ endpoints** REST
- Cero desalineación types typescript interfaces
- **No drift** entre backend DTOs → typescript

Opciones:
1.  Cliente API escrito manualmente → ~60% código boilerplate.
2.  **OpenAPI → TypeScript **Generado orval/fern **← elegida
3.  tRPC acoplamiento full stack React

## 2. Decisión

### Pipeline generación:
```
                  springdoc-openapi
        JAX-RS annotations código fuente
             ↓
   docs/openapi/openapi.yaml (SpringDoc genera
                ↓
      orval / fern v6
          ↓          ↓
  frontend-user/src/api/    frontend-admin/src/api/
  ├── client.gen.ts        ├── client.gen.ts
  ├── models.gen.ts       ├── models.gen.ts
  ├── msw.gen.ts       ├── mocks/
  └── index.ts           └── index.ts
```

### ReglasObligatorias:
1.  **0 ediciones manuales — SIEMPRE regenerar.
2.  **Checksum OpenAPI en CI si cambió sin regenerar cliente TS.
3.  Custom base-url configurable via env `VITE_USER_API_BASE_URL/VITE_ADMIN_API_BASE_URL`.
4.  Error handling axios interceptor 401 → trigger refresh token.
5.  Tarea 16 checkpoint.

## 3. Consecuencias

✅ Tipos TypeScript 100% en sincronía.
✅ Ahorro ~2000 líneas types interfaces.
✅ Mock Service Worker mocks para tests frontend sin backend.
⚠️ **Regenerar build CI romper runtime si regenera en cada build.
⚠️ **Cuidado con nombres DTOs back compat → romper types deprecated.

