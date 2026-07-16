#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_FILE="$SCRIPT_DIR/.backend.pid"
APP_JAR="$BACKEND_ROOT/gak-start/target/gak-start-1.0.0-SNAPSHOT.jar"
LOG_DIR="$BACKEND_ROOT/logs"
LOG_FILE="$LOG_DIR/backend.log"

is_managed_process() {
    local process_id="${1:-}"
    [[ "$process_id" =~ ^[0-9]+$ ]] || return 1
    kill -0 "$process_id" 2>/dev/null || return 1

    local command_line
    command_line="$(ps -p "$process_id" -o command= 2>/dev/null || true)"
    [[ "$command_line" == *"$APP_JAR"* ]]
}

if [[ -f "$PID_FILE" ]]; then
    existing_process_id="$(tr -d '[:space:]' < "$PID_FILE")"
    if is_managed_process "$existing_process_id"; then
        echo "Backend is already running (PID $existing_process_id)."
        exit 0
    fi
    rm -f "$PID_FILE"
fi

# The local password is defined in application.properties. Remove a stale
# session value so it cannot override the project configuration.
unset SPRING_DATASOURCE_PASSWORD

mkdir -p "$LOG_DIR"

JAVA_BIN="$(command -v java || true)"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
fi
if [[ -z "$JAVA_BIN" ]]; then
    echo "Backend cannot start: Java was not found in PATH." >&2
    exit 1
fi
if ! "$JAVA_BIN" -version 2>&1 | grep -Eq '"21([.]|")'; then
    echo "Backend cannot start: Java 21 is required." >&2
    exit 1
fi

echo "Building backend..."
cd "$BACKEND_ROOT"
if ! bash ./mvnw -pl gak-start -am package -DskipTests; then
    echo "Backend build failed." >&2
    exit 1
fi
if [[ ! -f "$APP_JAR" ]]; then
    echo "Backend JAR was not produced at $APP_JAR" >&2
    exit 1
fi

nohup "$JAVA_BIN" -jar "$APP_JAR" >"$LOG_FILE" 2>&1 < /dev/null &
started_process_id=$!
printf '%s\n' "$started_process_id" > "$PID_FILE"
startup_confirmed=0
for _ in {1..60}; do
    if ! is_managed_process "$started_process_id"; then
        rm -f "$PID_FILE"
        echo "Backend process exited during startup. See $LOG_FILE" >&2
        exit 1
    fi
    if grep -Fq "Started GnomishArmyKnifeApplication" "$LOG_FILE" 2>/dev/null; then
        startup_confirmed=1
        break
    fi
    sleep 0.5
done

if [[ "$startup_confirmed" -ne 1 ]]; then
    kill -9 "$started_process_id" 2>/dev/null || true
    rm -f "$PID_FILE"
    echo "Backend did not finish startup within 30 seconds. See $LOG_FILE" >&2
    exit 1
fi

echo "Backend started (PID $started_process_id)."
