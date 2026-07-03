#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

(cd frontend && npm run build)

export SERVER_ADDRESS="${SERVER_ADDRESS:-127.0.0.1}"
export SERVER_PORT="${SERVER_PORT:-8080}"
export ARCHIPELAGO_FRONTEND_BASE_URL="${ARCHIPELAGO_FRONTEND_BASE_URL:-https://archipelago-demo.zhengwangyuan-patrick.com}"
export ARCHIPELAGO_SESSION_COOKIE_SECURE="${ARCHIPELAGO_SESSION_COOKIE_SECURE:-true}"
export ARCHIPELAGO_MAIL_ENABLED="${ARCHIPELAGO_MAIL_ENABLED:-false}"

exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
