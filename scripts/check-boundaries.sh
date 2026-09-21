#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

ERRORS=0
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_error() {
  echo -e "${RED}✗ ERROR${NC}: $1"
  ERRORS=$((ERRORS + 1))
}

log_ok() {
  echo -e "${GREEN}✓ OK${NC}: $1"
}

log_warn() {
  echo -e "${YELLOW}⚠ WARN${NC}: $1"
}

echo "================================================"
echo "  CHECK-BOUNDARIES — Separación Back ↔ Front"
echo "  Monorepo: sistema-micro-pagos"
echo "================================================"
echo ""

cd "${ROOT_DIR}"

echo "▸ [1/6] Verificando 0 TypeScript (.ts/.tsx) en backend/"
BACKEND_TS=$(find backend -type f \( -name "*.ts" -o -name "*.tsx" -o -name "*.js" -o -name "*.jsx" \) 2>/dev/null | grep -v node_modules | grep -v target || true)
if [ -n "${BACKEND_TS}" ]; then
  while IFS= read -r file; do
    log_error "Archivo prohibido en backend/: ${file}"
  done <<< "${BACKEND_TS}"
else
  log_ok "0 archivos TS/JS en backend/"
fi

echo ""
echo "▸ [2/6] Verificando 0 Java/SQL/Flyway en frontend-user/"
FRONT_USER_JAVA=$(find frontend-user -type f \( -name "*.java" -o -name "*.sql" -o -name "*.xml" \) 2>/dev/null | grep -v node_modules || true)
if [ -n "${FRONT_USER_JAVA}" ]; then
  while IFS= read -r file; do
    log_error "Archivo prohibido en frontend-user/: ${file}"
  done <<< "${FRONT_USER_JAVA}"
else
  log_ok "0 archivos Java/SQL en frontend-user/"
fi

echo ""
echo "▸ [3/6] Verificando 0 Java/SQL/Flyway en frontend-admin/"
FRONT_ADMIN_JAVA=$(find frontend-admin -type f \( -name "*.java" -o -name "*.sql" -o -name "*.xml" \) 2>/dev/null | grep -v node_modules || true)
if [ -n "${FRONT_ADMIN_JAVA}" ]; then
  while IFS= read -r file; do
    log_error "Archivo prohibido en frontend-admin/: ${file}"
  done <<< "${FRONT_ADMIN_JAVA}"
else
  log_ok "0 archivos Java/SQL en frontend-admin/"
fi

echo ""
echo "▸ [4/6] Verificando package.json y vite.config SÓLO en carpetas frontend"
BAD_PACKAGE=$(find . -maxdepth 3 -name "package.json" 2>/dev/null | grep -v "./frontend-user/" | grep -v "./frontend-admin/" | grep -v node_modules || true)
if [ -n "${BAD_PACKAGE}" ]; then
  while IFS= read -r file; do
    log_error "package.json FUERA de frontend-*/: ${file}"
  done <<< "${BAD_PACKAGE}"
else
  log_ok "package.json sólo en frontend-user/ y frontend-admin/"
fi

BAD_VITE=$(find . -maxdepth 3 -name "vite.config.*" 2>/dev/null | grep -v "./frontend-user/" | grep -v "./frontend-admin/" | grep -v node_modules || true)
if [ -n "${BAD_VITE}" ]; then
  while IFS= read -r file; do
    log_error "vite.config FUERA de frontend-*/: ${file}"
  done <<< "${BAD_VITE}"
else
  log_ok "vite.config sólo en frontend-user/ y frontend-admin/"
fi

echo ""
echo "▸ [5/6] Verificando pom.xml SÓLO en backend/"
BAD_POM=$(find . -maxdepth 3 -name "pom.xml" 2>/dev/null | grep -v "./backend/" | grep -v target || true)
if [ -n "${BAD_POM}" ]; then
  while IFS= read -r file; do
    log_error "pom.xml FUERA de backend/: ${file}"
  done <<< "${BAD_POM}"
else
  log_ok "pom.xml sólo en backend/"
fi

echo ""
echo "▸ [6/6] Verificando variables .env.example con PREFIJOS (sin nombres genéricos)"
if [ -f ".env.example" ]; then
  GENERIC_VARS=$(grep -E "^(API_URL|DB_HOST|DB_PORT|DB_USER|DB_PASS|REDIS_URL|JWT_SECRET|STRIPE_KEY|BACKEND_URL|FRONTEND_URL)=" .env.example 2>/dev/null || true)
  if [ -n "${GENERIC_VARS}" ]; then
    while IFS= read -r line; do
      log_warn "Variable SIN prefijo (debe tener SPRING_/VITE_USER_/VITE_ADMIN_/POSTGRES_/REDIS_/STRIPE_): ${line}"
    done <<< "${GENERIC_VARS}"
  else
    log_ok "Todas las variables en .env.example tienen prefijo correcto"
  fi
else
  log_warn ".env.example no existe aún — se validará después"
fi

echo ""
echo "================================================"
if [ "${ERRORS}" -eq 0 ]; then
  echo -e " ${GREEN}✅ TODAS LAS REGLAS DE SEPARACIÓN CUMPLIDAS${NC}"
  echo "  Infracciones: ${ERRORS}"
  echo "================================================"
  exit 0
else
  echo -e " ${RED}❌ FALLÓ LA VALIDACIÓN DE SEPARACIÓN${NC}"
  echo "  Infracciones: ${ERRORS}"
  echo "  Corrige los errores antes de continuar."
  echo "================================================"
  exit 1
fi
