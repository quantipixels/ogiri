#!/usr/bin/env bash
# Run Ogiri live integration tests (or dev UI) against a real Spring Boot sample server.
#
# Usage:
#   ./run-live.sh [java|kotlin]        # run vitest integration tests (default)
#   ./run-live.sh [java|kotlin] --ui   # start Vite dev server proxied to the real backend
#
# Starts the Java (port 48080) or Kotlin (port 48081) sample app with forced
# token rotation, then either runs the React integration tests or opens the
# Vite dev server (MSW bypassed, /api/* proxied to Spring Boot).
# Defaults to kotlin.
set -euo pipefail

SAMPLE="${1:-kotlin}"
MODE="${2:-test}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

case "$SAMPLE" in
  java)
    MODULE=":sample:sample-java"
    PORT=48080
    ;;
  kotlin)
    MODULE=":sample:sample-kotlin"
    PORT=48081
    ;;
  *)
    echo "Usage: $0 [java|kotlin] [--ui]" >&2
    exit 1
    ;;
esac

if [[ "$MODE" != "--ui" && "$MODE" != "test" ]]; then
  echo "Usage: $0 [java|kotlin] [--ui]" >&2
  exit 1
fi

SERVER_PID=

cleanup() {
  if [[ -n "${SERVER_PID}" ]]; then
    echo "Stopping server (PID $SERVER_PID)..."
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "Starting $SAMPLE server on port $PORT (rotation forced on every request)..."
(cd "$ROOT" && ./gradlew "$MODULE:bootRun" \
  -Dogiri.auth.rotate-stale-seconds=0 \
  -Dogiri.auth.batch-grace-seconds=0 \
  -Dogiri.cookies.enabled=false) &
SERVER_PID=$!

echo "Waiting for server to be ready at http://localhost:$PORT..."
MAX_WAIT=90
WAITED=0
until curl -s --max-time 2 "http://localhost:$PORT/api/demo/info" > /dev/null 2>&1; do
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "Server process exited unexpectedly." >&2
    exit 1
  fi
  if [[ $WAITED -ge $MAX_WAIT ]]; then
    echo "Server did not respond within ${MAX_WAIT}s." >&2
    exit 1
  fi
  sleep 2
  WAITED=$((WAITED + 2))
done

echo "Server ready."
cd "$SCRIPT_DIR"

if [[ "$MODE" == "--ui" ]]; then
  echo "Starting Vite dev server (MSW disabled, /api proxied to $SAMPLE on port $PORT)..."
  echo "Open http://localhost:5173 in your browser. Press Ctrl+C to stop."
  VITE_API_TARGET="http://localhost:$PORT" pnpm dev
else
  echo "Running React live integration tests against $SAMPLE (port $PORT)..."
  OGIRI_BASE_URL="http://localhost:$PORT" pnpm test:live
fi
