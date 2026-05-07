#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR"
BACKEND_APP_DIR="$BACKEND_DIR/gak-start"
FRONTEND_DIR="$ROOT_DIR/../GnomishArmyKnife-Web"
LOG_DIR="$ROOT_DIR/.dev-logs"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"

BACKEND_PID=""
FRONTEND_PID=""
TAIL_PID=""

require_command() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
}

cleanup() {
  local exit_code=$?

  for pid in "$TAIL_PID" "$FRONTEND_PID" "$BACKEND_PID"; do
    if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
    fi
  done

  wait >/dev/null 2>&1 || true
  exit "$exit_code"
}

trap cleanup EXIT INT TERM

require_command java
require_command npm
require_command awk
require_command tail

if [[ ! -d "$BACKEND_DIR" || ! -d "$FRONTEND_DIR" ]]; then
  echo "Project directories not found. Expected:" >&2
  echo "  $BACKEND_DIR" >&2
  echo "  $FRONTEND_DIR" >&2
  exit 1
fi

if [[ ! -f "$BACKEND_DIR/mvnw" ]]; then
  echo "Backend wrapper not found: $BACKEND_DIR/mvnw" >&2
  exit 1
fi

if [[ ! -f "$BACKEND_APP_DIR/pom.xml" ]]; then
  echo "Backend app module not found: $BACKEND_APP_DIR/pom.xml" >&2
  exit 1
fi

if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
  echo "Frontend dependencies are missing. Run: cd $FRONTEND_DIR && npm install" >&2
  exit 1
fi

mkdir -p "$LOG_DIR"
: > "$BACKEND_LOG"
: > "$FRONTEND_LOG"

echo "Starting backend on http://localhost:8080"
(
  cd "$BACKEND_DIR"
  ./mvnw -pl gak-start -am -DskipTests install
  cd "$BACKEND_APP_DIR"
  ../mvnw spring-boot:run
) >"$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!

echo "Starting frontend on http://localhost:5173"
(
  cd "$FRONTEND_DIR"
  npm run dev -- --host 0.0.0.0
) >"$FRONTEND_LOG" 2>&1 &
FRONTEND_PID=$!

sleep 2

if ! kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
  echo "Backend failed to start. Check $BACKEND_LOG" >&2
  exit 1
fi

if ! kill -0 "$FRONTEND_PID" >/dev/null 2>&1; then
  echo "Frontend failed to start. Check $FRONTEND_LOG" >&2
  exit 1
fi

echo
echo "Services are starting."
echo "Backend log : $BACKEND_LOG"
echo "Frontend log: $FRONTEND_LOG"
echo "Press Ctrl+C to stop both services."
echo

tail -n 20 -f "$BACKEND_LOG" "$FRONTEND_LOG" | awk '
  /^==> .*backend\.log <==$/ { prefix="[backend]"; next }
  /^==> .*frontend\.log <==$/ { prefix="[frontend]"; next }
  NF == 0 { print ""; next }
  { print prefix, $0 }
' &
TAIL_PID=$!

wait "$BACKEND_PID" "$FRONTEND_PID"
