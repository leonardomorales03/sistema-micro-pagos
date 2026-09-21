# ADR-001 — Estructura Monorepo y Script de Boundaries

| Campo       | Valor                                      |
|-----------|--------------------------------------------|
| **ID**     | ADR-001                                   |
| **Estado** | Aceptado — 2026-09-21                   |
| **Autor**  | MicroPagos Architecture Team              |
| **Área**  | Estructura / Organización código            |

---

## 1. Contexto

El sistema integra **Sistema de Micro-Pagos** tiene **tres aplicaciones despliegue**:
  - Backend Spring Boot (Java 21)
  - Frontend Usuario Dashboard (React 18 + TS + Vite)
  - Frontend Admin Backoffice (React 18 + TS + Vite)

Las opciones evaluadas eran:

**Opción A — Multi-repo (3 repos separados
**Opción B — Monorepo (un único repo con carpetas independientes (elegida

Se requiere además validación automática que no haya **ninguna filtración cruzada de código:
- TypeScript en `backend/`
- Java/SQL/Flyway en `frontend-*/
- Archivos de build (pom.xml/package.json/vite.config fuera de su carpeta correspondiente.

## 2. Decisión

Se adopta **MONOREPO** como estructura de repositorio único, con carpetas top-level:

```
sistema-micro-pagos/
├── backend/            # Spring Boot 3 / Java 21
├── frontend-user/  # React 18 / TS / Vite 5
├── frontend-admin/ # React 18 / TS / Vite 5
├── infra/          # docker-compose, nginx, seed
├── docs/         # OpenAPI, ADRs, testing cards
├── scripts/      # scripts/check-boundaries.sh
├── Makefile       # targets dev/test/build
└── .env.example
```

Se implementa **`scripts/check-boundaries.sh` con 6 reglas ejecutado en:
1.  0 TS/TSX/JS/JSX en `backend/`
2.  0 Java/SQL/XML en `frontend-user/`
3.  0 Java/SQL/XML en `frontend-admin/`
4.  `package.json` + `vite.config.*` **solo** en `frontend-user|frontend-admin/`
5.  `pom.xml` **solo** en `backend/`
6.  Todas variables en `.env.example` con PREFIJO (`SPRING_`, `VITE_USER_`, `VITE_ADMIN_`, `POSTGRES_`, `REDIS_`, `STRIPE_`)

Ejecución obligatoria en:
- Pre-commit hook local`

## 3. Consecuencias

### Positivas
✅ **Menos fricción CI/CD**: Un solo pipeline para validar límites, un solo Makefile con targets agrupados, cambios cross-team.
✅ **Trazabilidad unificada**: TODO el código y su infraestructura y sus ADRs en un solo lugar.
✅ **Refactors coordinados**: Cambios cross-layer (endpoint + frontend usuario/front-end + OpenAPI spec → cliente TS se puedenPR.
✅ **Fácil clonar**: Nuevos contributors** sin tener que buscar 3+ repos.
✅ **Límites estrictos y auditables**: Script ejecuta automáticamente, no** dependemos convenciones.

### Negativas / Riesgos
⚠️ **Tamaño repo más ampliar commits grandes con el tiempo (monorepo escalar a módulos grandes requiere tooling específico (Turborepo/Nx en el futuro si es necesario.
⚠️ **Git blame/commits** permisos granular por carpeta requiere CODEOWNERS.**

### Neutros
⚖️ Script boundaries es **no** reemplaza CODEOWNERS; ambas mecanismo fuerte cuando haya que habilitarlo en el futuro.
⚖️ Si los límites puede debieran separarse en un futuro mediante `git filter-repo` si el monorepo queda muy grande.

