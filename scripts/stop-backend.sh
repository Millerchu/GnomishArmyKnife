#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_FILE="$SCRIPT_DIR/.backend.pid"
APP_JAR="$BACKEND_ROOT/gak-start/target/gak-start-1.0.0-SNAPSHOT.jar"

is_managed_process() {
    local process_id="${1:-}"
    [[ "$process_id" =~ ^[0-9]+$ ]] || return 1
    kill -0 "$process_id" 2>/dev/null || return 1

    local command_line
    command_line="$(ps -p "$process_id" -o command= 2>/dev/null || true)"
    [[ "$command_line" == *"$APP_JAR"* ]]
}

if [[ ! -f "$PID_FILE" ]]; then
    echo "Backend is not running."
    exit 0
fi

process_id="$(tr -d '[:space:]' < "$PID_FILE")"
if ! is_managed_process "$process_id"; then
    rm -f "$PID_FILE"
    echo "Backend PID file was stale; no unrelated process was stopped."
    exit 0
fi

echo "Stopping backend (PID $process_id)..."
kill "$process_id" 2>/dev/null || true
for _ in {1..40}; do
    if ! kill -0 "$process_id" 2>/dev/null; then
        break
    fi
    sleep 0.25
done

if kill -0 "$process_id" 2>/dev/null; then
    kill -9 "$process_id" 2>/dev/null || true
    sleep 0.25
fi

if kill -0 "$process_id" 2>/dev/null; then
    echo "Backend process $process_id did not stop." >&2
    exit 1
fi

rm -f "$PID_FILE"
echo "Backend stopped."
