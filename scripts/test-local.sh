#!/usr/bin/env bash
#
# test-local.sh — sauberer lokaler Test-/Smoke-/E2E-Runner.
#
# Hintergrund: Das Spring-Boot-Backend liefert das gebaute Angular-Bundle aus
# `dist/terminbuchungsapp/browser/` aus. Baut man das Frontend neu, WÄHREND das
# Backend läuft, verweist die ausgelieferte `index.html` weiterhin auf die alte,
# gehashte `main-XXXX.js` — die nach dem Rebuild nicht mehr existiert
# (FileNotFoundException 500, App-Bundle lädt nicht → E2E scheitert an `.location-card`).
#
# Dieses Script erzwingt die richtige Reihenfolge: erst bauen, DANN ein frisches
# Backend gegen das aktuelle dist/ starten.
#
# Usage:
#   scripts/test-local.sh smoke        # Build + Backend + Health-/Root-Check (~1 min)
#   scripts/test-local.sh e2e          # Build + frisches Backend + Playwright (3 Viewports)
#   scripts/test-local.sh e2e REQ-031  # Nur passende Specs (Filter wird an Playwright durchgereicht)
#   scripts/test-local.sh serve        # Build + Backend starten und laufen lassen (manuelles Testen)
#   E2E_SEED_SET=thin scripts/test-local.sh serve   # REQ-038: Statusseite mit Seed-Set thin|fail
#
# Das Backend startet immer im Profil e2e-seed (REQ-038): die Statistik-Stores werden beim Start
# aus playwright/fixtures/req038/<E2E_SEED_SET>/ befüllt (Default full). Ein anderes Set braucht
# einen Neustart, weil die Stores nur beim Start befüllt werden.
#
set -euo pipefail

MODE="${1:-e2e}"
shift || true

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

HEALTH_URL="http://localhost:3000/api/v1/health"
BACKEND_PID=""

kill_backend_on_port() {
  # Kill any process still bound to :3000 so we never serve a stale bundle.
  # Only the LISTENING server — a plain `lsof -ti:3000` also returns clients with an open
  # connection to :3000 (e.g. the REQ-038 screenshot script's keep-alive fetch) and would
  # kill the caller itself.
  local pids
  pids="$(lsof -ti:3000 -sTCP:LISTEN 2>/dev/null || true)"
  if [ -n "$pids" ]; then
    echo "▶ Stoppe altes Backend auf :3000 ($pids)"
    echo "$pids" | xargs kill -9 2>/dev/null || true
    sleep 2
  fi
}

start_backend() {
  echo "▶ Starte frisches Backend (gradle bootRun)…"
  # REQ-037: fixed local credentials so the E2E spec can open the Basic-Auth protected
  # status page (/internal/report). Without this the backend generates a random UUID
  # password on every start and the spec could never authenticate. Local/test only —
  # in production REPORT_ADMIN_PASSWORD comes from the Azure secret store.
  export REPORT_ADMIN_PASSWORD="${REPORT_ADMIN_PASSWORD:-e2e-local-secret}"
  # The Playwright suite drives several hundred /api/ calls from one IP within minutes and
  # would hit the production rate limit (500 per 15 min) mid-run — that is what makes the
  # booking specs fail with "Fehler beim Laden" (HTTP 429), not a product defect.
  # Local test runs only; production keeps the default.
  export APP_RATE_LIMIT_CAPACITY="${APP_RATE_LIMIT_CAPACITY:-100000}"
  # REQ-038 (§14.2): statistics tab with seeded fixtures instead of Azure. The e2e-seed profile
  # loads playwright/fixtures/req038/<E2E_SEED_SET>/ (full | thin | fail, default full) into the
  # stores and refuses to start while USAGE_STORAGE_ACCOUNT is set. All statistics sources are
  # on; both crons are disabled ("-"), so no scan or rollup calls SoftNet or Log Analytics.
  # Keep in sync with playwright-backend.config.ts (webServer.env).
  export SPRING_PROFILES_ACTIVE="e2e-seed"
  export USAGE_INSIGHTS_ENABLED="true"
  export USAGE_FUNNEL_ENABLED="true"
  export USAGE_DROPOFF_ENABLED="true"
  export CAPACITY_SCAN_ENABLED="true"
  export CAPACITY_SCAN_CRON="-"
  export USAGE_INSIGHTS_ROLLUP_CRON="-"
  export E2E_SEED_SET="${E2E_SEED_SET:-full}"
  echo "▶ Seed-Set für die Statusseite: ${E2E_SEED_SET} (Profil e2e-seed)"
  ( cd server && ./gradlew bootRun > /tmp/gs-backend-test.log 2>&1 ) &
  BACKEND_PID=$!
  echo "▶ Warte auf Health ($HEALTH_URL)…"
  for _ in $(seq 1 90); do
    if curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
      echo "✔ Backend healthy"
      return 0
    fi
    sleep 1
  done
  echo "✖ Backend wurde nicht healthy (siehe /tmp/gs-backend-test.log)"
  return 1
}

stop_backend() {
  if [ -n "$BACKEND_PID" ]; then
    echo "▶ Stoppe Backend ($BACKEND_PID)"
    kill "$BACKEND_PID" 2>/dev/null || true
  fi
  kill_backend_on_port
}

build_frontend() {
  echo "▶ Baue Frontend (ng build)…"
  npm run build
}

case "$MODE" in
  smoke)
    kill_backend_on_port
    build_frontend
    trap stop_backend EXIT
    start_backend
    echo "▶ Smoke-Checks…"
    curl -sf "$HEALTH_URL" | grep -q '"status":"healthy"' \
      || { echo "✖ SMOKE FAIL: Health-Check"; exit 1; }
    curl -sf http://localhost:3000/ | grep -q '<app-root' \
      || { echo "✖ SMOKE FAIL: Angular-Root nicht ausgeliefert"; exit 1; }
    echo "✔ SMOKE PASS"
    ;;

  e2e)
    kill_backend_on_port
    build_frontend
    trap stop_backend EXIT
    start_backend
    echo "▶ Playwright E2E (reuse laufendes Backend)…"
    npx playwright test --config playwright-backend.config.ts "$@"
    echo "✔ E2E PASS"
    ;;

  serve)
    kill_backend_on_port
    build_frontend
    start_backend
    echo "✔ App läuft auf http://localhost:3000 — Strg+C beendet das Backend NICHT (läuft im Hintergrund)."
    echo "  Zum Stoppen:  lsof -ti:3000 | xargs kill -9"
    ;;

  *)
    echo "Unbekannter Modus: $MODE"
    echo "Usage: scripts/test-local.sh [smoke|e2e|serve] [playwright-filter…]"
    exit 2
    ;;
esac
