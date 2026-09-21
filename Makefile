# ======================================================
#  Makefile — sistema-micro-pagos (Monorepo Full Stack)
#  Uso: make [target]
# ======================================================
#
# ⚠️  WARNING / AVISO IMPORTANTE ⚠️
# GNU Make NO soporta correctamente rutas absolutas con ESPACIOS
# en su variable $(shell pwd) ni en sustituciones $(ROOT). Si el proyecto
# está guardado en iCloud Drive ("Mobile Documents/com~apple~CloudDocs")
# o en cualquier path con espacios, **evita usar make directamente**.
#
# Workaround oficial para este proyecto (ejecuta comandos cd DIRECTO):
#
#   ┌───────────────────────────────────────────────────────────────┐
#   │  Infra local (Postgres + Redis + Adminer):                   │
#   │    cd "backend/infra" && docker compose up -d                 │
#   │    cd "backend/infra" && docker compose down                 │
#   │                                                               │
#   │  Backend (Maven Spring Boot):                                │
#   │    cd backend && mvn clean compile -DskipTests               │
#   │    cd backend && export SPRING_DATASOURCE_PASSWORD=...  \    │
#   │       && mvn spring-boot:run -Dspring-boot.run.profiles=dev  │
#   │                                                               │
#   │  Boundaries check (sí funciona con bash + quotes):           │
#   │    bash scripts/check-boundaries.sh                           │
#   └───────────────────────────────────────────────────────────────┘
#
# Si mueves el repo a un path SIN espacios (ej: ~/code/sistema-micro-pagos),
# todos los targets de este Makefile volverán a funcionar automáticamente.

SHELL := /bin/bash
ROOT := $(shell pwd)

# ──────────────────────────────────────────────────────
#  HELPERS
# ──────────────────────────────────────────────────────
.PHONY: help
help: ## Muestra esta ayuda
	@awk 'BEGIN {FS = ":.*##"; printf "\n\033[1mTargets Makefile:\033[0m\n\n"} /^[a-zA-Z_-]+:.*?##/ { printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2 } END { print "" }' $(MAKEFILE_LIST)

# ──────────────────────────────────────────────────────
#  SEPARACIÓN — Límites estrictos Back ↔ Front
# ──────────────────────────────────────────────────────
.PHONY: check-boundaries
check-boundaries: ## Valida reglas separación Backend/Frontend
	@bash $(ROOT)/scripts/check-boundaries.sh

.PHONY: pre-commit
pre-commit: check-boundaries ## Ejecuta hooks locales (sólo check-boundaries por ahora)
	@echo "✅ Pre-commit OK"

# ──────────────────────────────────────────────────────
#  BACKEND — Spring Boot
# ──────────────────────────────────────────────────────
.PHONY: backend-dev
backend-dev: ## Arranca backend en modo dev (Hot Reload + perfil dev)
	@cd $(ROOT)/backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

.PHONY: backend-build
backend-build: ## Compila backend (sin tests)
	@cd $(ROOT)/backend && ./mvnw clean package -DskipTests -q && echo "✅ Build backend OK"

.PHONY: backend-test
backend-test: ## Tests unitarios + property-based
	@cd $(ROOT)/backend && ./mvnw test

.PHONY: backend-test-property
backend-test-property: ## Sólo tests property-based (jqwik, >=100 iteraciones)
	@cd $(ROOT)/backend && ./mvnw test -Dtest="*_Property" -Djqwik.default.tries=100

.PHONY: backend-test-integration
backend-test-integration: ## Tests de integración con Testcontainers
	@cd $(ROOT)/backend && ./mvnw verify -P integration

.PHONY: backend-clean
backend-clean: ## Limpia build artefactos backend
	@cd $(ROOT)/backend && ./mvnw clean

# ──────────────────────────────────────────────────────
#  FRONTEND USUARIO — Dashboard
# ──────────────────────────────────────────────────────
.PHONY: user-install
user-install: ## Instala dependencias frontend-user
	@cd $(ROOT)/frontend-user && npm install

.PHONY: user-dev
user-dev: ## Arranca frontend-user Vite dev server
	@cd $(ROOT)/frontend-user && npm run dev

.PHONY: user-build
user-build: ## Build producción frontend-user
	@cd $(ROOT)/frontend-user && npm run build

.PHONY: user-test
user-test: ## Tests Vitest frontend-user
	@cd $(ROOT)/frontend-user && npm run test

# ──────────────────────────────────────────────────────
#  FRONTEND ADMIN — Backoffice
# ──────────────────────────────────────────────────────
.PHONY: admin-install
admin-install: ## Instala dependencias frontend-admin
	@cd $(ROOT)/frontend-admin && npm install

.PHONY: admin-dev
admin-dev: ## Arranca frontend-admin Vite dev server
	@cd $(ROOT)/frontend-admin && npm run dev

.PHONY: admin-build
admin-build: ## Build producción frontend-admin
	@cd $(ROOT)/frontend-admin && npm run build

.PHONY: admin-test
admin-test: ## Tests Vitest frontend-admin
	@cd $(ROOT)/frontend-admin && npm run test

# ──────────────────────────────────────────────────────
#  DOCKER / INFRAESTRUCTURA
#  Nota: infra/ VIVE DENTRO DE backend/ para autonomía.
#  Si backend se separa como repo git independiente,
#  se lleva TODO lo necesario (Dockerfile + infra compose).
# ──────────────────────────────────────────────────────
.PHONY: infra-up
infra-up: ## Sube PostgreSQL, Redis, Nginx y Adminer
	@cd $(ROOT)/backend/infra && docker compose up -d

.PHONY: infra-down
infra-down: ## Detiene y elimina contenedores infra
	@cd $(ROOT)/backend/infra && docker compose down

.PHONY: infra-logs
infra-logs: ## Logs en vivo de infraestructura
	@cd $(ROOT)/backend/infra && docker compose logs -f

.PHONY: infra-prune
infra-prune: infra-down ## Borra volumes infra (resetea DB!)
	@docker volume prune -f 2>/dev/null; echo "⚠ Volúmenes Docker limpiados"

.PHONY: prism-up
prism-up: ## Arranca Prism Mock Server (OpenAPI spec)
	@cd $(ROOT) && docker run --rm -it -p 4010:4010 -v $(PWD)/docs/openapi:/spec \
		stoplight/prism:5 mock -h 0.0.0.0 /spec/openapi.yaml

# ──────────────────────────────────────────────────────
#  E2E / PLAYWRIGHT
# ──────────────────────────────────────────────────────
.PHONY: e2e-install
e2e-install: ## Instala browsers Playwright (una sola vez)
	@npx playwright install --with-deps

.PHONY: e2e
e2e: ## Ejecuta tests Playwright E2E
	@npx playwright test

# ──────────────────────────────────────────────────────
#  BUILDS COMPLETOS
# ──────────────────────────────────────────────────────
.PHONY: build-all
build-all: check-boundaries backend-build user-build admin-build ## Build all (valida boundaries + 3 builds)
	@echo "✅ Build completo monorepo OK"

.PHONY: test-all
test-all: check-boundaries backend-test user-test admin-test ## Tests all
	@echo "✅ Suite tests completa OK"

# ──────────────────────────────────────────────────────
#  UTILIDADES
# ──────────────────────────────────────────────────────
.PHONY: env
env: ## Valida y muestra variables entorno (carga .env si existe)
	@if [ -f $(ROOT)/.env ]; then \
		echo "ℹ Cargando variables de .env"; \
		export $$(grep -v '^#' $(ROOT)/.env | xargs); \
	else \
		echo "⚠ Sin .env — usando .env.example como referencia"; \
	fi
	@echo "  POSTGRES_HOST = $${POSTGRES_HOST:-postgres}"
	@echo "  REDIS_HOST    = $${REDIS_HOST:-redis}"
	@echo "  SPRING_PROFILE= $${SPRING_PROFILES_ACTIVE:-dev}"
