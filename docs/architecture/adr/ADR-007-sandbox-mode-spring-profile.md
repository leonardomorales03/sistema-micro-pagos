# ADR-007 — Sandbox Mode: Adaptadores Mock + Spring Profile 100% Offline

| Campo       | Valor                                      |
|-----------|--------------------------------------------|
| **ID**     | ADR-007                                   |
| **Estado** | Aceptado — 2026-09-21                   |
| **Autor**  | MicroPagos Architecture Team              |
| **Área**  | Testing / Desarrollo sin credenciales reales.

---

## 1. Contexto

Desarrolladores frontend, QA, demos NO deben usar credenciales reales stripe/pagos reales.
- Frontend: UI **0**Stripe
- Fork Colombia Wompi, PlaceToPay, Nequi
- Demo alianzas

Opciones:
1.  Stripe test mode con API externo
2.  **Sandbox Spring Profiles
3.  **Prism Mock Server API spec yaml

## 2. Decisión**
| Componente | Implementación
|---|---|
| Spring Profile | `sandbox` (incluye dev → 100% offline 0 llamadas redes
| Adaptadores Mock | `MockStripeAdapter`, `MockBankPSEAdapter`, `MockEmailAdapter`, `MockFcmAdapter` |
| Endpoints Admin | `/_sandbox/**` (solo activos profile |
| Prism Server | `stoplight/prism:5` server `/spec yaml|
| Tarjetas test | VISA 4242 4242 4242 4242, AMEX, MC, PSE |
| Máximo txn | USD $1 USD |
| KYC mock | Always auto-aprueba cédula fake colombia cédula 1023456789|
| Base datos | Misma Postgres o H2 (perfil)
| **No** activo** Spring Configuración application-sandbox.yml

## 3. Consecuencias

✅ Desarrollo full sin red, internet, pruebas E2E Playwright, demostraciones clientes.
✅ Documentado en spec
⚠️ Diferencia mínima prod.
⚠️ Perfiles `sandbox` testing properties.

Sandbox es solo dev.

