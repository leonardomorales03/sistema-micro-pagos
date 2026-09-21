# ADR-002 — Arquitectura Hexagonal (Ports & Adapters)

| Campo       | Valor                                      |
|-----------|--------------------------------------------|
| **ID**     | ADR-002                                   |
| **Estado** | Aceptado — 2026-09-21                   |
| **Autor**  | MicroPagos Architecture Team              |
| **Área**  | Arquitectura / Backend                |

---

## 1. Contexto

El backend del sistema de micro-pagos requiere:
1.  Soportar **multiples métodos pago (Stripe, PSE, Wompi, Nequi)
2.  Modo **Sandbox 100% offline (mockear pagos)
3.  Sustituir proveedores sin tocar negocio.
4.  Testing aislado: tests unitarios 35 propiedades
5.  Tests integración Postgres + Redis reales via Testcontainers.
6.  Reemplazo de Notification Channel (Email/Push stubEmail+FCM) sin impacto.

Evaluamos:
- **Arquitectura por capas tradicional N-Capas** → acoplamiento fuerte con infraestructura.
- **Arquitectura limpia / Onion.
- **Arquitectura Hexagonal (Ports & Adapters( elegida por Alistair Cockburn.

## 2. Decisión

Adoptamos **Arquitectura Hexagonal 4 capas explícitas** (top-down):

```
com.micropay.backend
│
├── api/                              ← Capa I/O Entrada/Salida HTTP
│   └── v1/{controllers,dtos,assemblers}
│       * REST → springdoc-openapi auto-genera spec ←
│
├── application/                      ← Casos de Uso
│   └── {usecases,dtos,mappers}
│       * Orquesta entidades, llama Ports (IN/OUT)
│
├── domain/                         ← Núcleo — SIN dependencias externas
│   ├── entities, valueobjects, exceptions
│   └── ports/{in,out}            ← INTERFACES (no implementaciones)
│       * Ports IN: interfaces usadas por use case
│       * Ports OUT: repositorios / stubs implementan adapters
│
└── infrastructure/                ← Adaptadores (implementan Ports OUT)
    ├── adapters/
    │   ├── persistence/postgres/{entities,repositories,specifications}
    │   ├── cache/redis/
    │   ├── notifications/
    │   └── stripe/
    ├── config/                      ← Beans Spring Configuración
    └── security/                  ← Security Configuración Spring Security
```

**Reglas de dependencia** (SÓLO se permitidas estas direcciones):
```
 api → application → domain ← infrastructure
```

`domain` NO DEPENDE DE NINGUNA OTRA CAPA.
Ports IN son interfaces en domain.ports.in y usados desde application.
Ports OUT son interfaces en domain.ports.out y se implementan en infrastructure.

## 3. Consecuencias

### Positivas
✅ **Independencia de framework: Spring Boot, Postgres, Stripe** pueden**cambiar sin tocar dominio
✅ **Tests unitarios domain sin infraestructura
✅ **Sandbox Mode trivial: Basta con implementar **`MockStripeAdapter` y 0 llamadas reales
✅ **Swappable adapters**: Email/Push a FCM reales.
✅ **Spring Profile `sandbox` switchea de Adapter SIN tocar use cases**.
✅ **Alta cobertura tests** 80% alcanzable.

### Negativas
⚠️ **Más boilerplate inicial (interfaces + DTOs assemblies.
⚠️ **Curva aprendizaje equipo nuevo con el patrón.
⚠️ **Naming estricto: Controllers NO pueden llamar Repositorios JPA directamente; vía port.

### Mitigación
El Code Review obliga el script boundaries + conventions (ADR-001 + regla Arquitectura Hexagonal 4-layer architecture package naming + conventions naming conventions
- Naming conventions en los PR templates.
⚖️ Podremos usar MapStruct para mappers DTO ↔ entidad.
⚖️ Usaremos Assemblers REST Hateoas patrón assembler HAL.
