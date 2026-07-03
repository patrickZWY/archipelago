#!/usr/bin/env bash
set -euo pipefail

TUNNEL_NAME="${ARCHIPELAGO_TUNNEL_NAME:-archipelago-demo}"
ORIGIN_URL="${ARCHIPELAGO_TUNNEL_ORIGIN:-http://127.0.0.1:8080}"

exec cloudflared tunnel run --url "$ORIGIN_URL" "$TUNNEL_NAME"
