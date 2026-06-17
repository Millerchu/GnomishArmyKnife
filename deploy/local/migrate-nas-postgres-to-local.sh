#!/usr/bin/env bash
set -euo pipefail

# 从 NAS PostgreSQL 克隆到本机 PostgreSQL。脚本只在临时文件中保存 NAS .env，退出时自动清理。

NAS_SSH_TARGET="${NAS_SSH_TARGET:-}"
NAS_ENV_PATH="${NAS_ENV_PATH:-/volume1/Projects/GAK-App/.env}"
NAS_POSTGRES_PORT="${NAS_POSTGRES_PORT:-25432}"
LOCAL_TUNNEL_PORT="${LOCAL_TUNNEL_PORT:-25433}"
LOCAL_POSTGRES_HOST="${LOCAL_POSTGRES_HOST:-127.0.0.1}"
LOCAL_POSTGRES_PORT="${LOCAL_POSTGRES_PORT:-5432}"
LOCAL_POSTGRES_DB="${LOCAL_POSTGRES_DB:-mydb}"
LOCAL_POSTGRES_USER="${LOCAL_POSTGRES_USER:-millerchu}"
LOCAL_POSTGRES_PASSWORD="${LOCAL_POSTGRES_PASSWORD:-}"
DUMP_DIR="${DUMP_DIR:-./data/db-migrations}"
PG_BIN_DIR="${PG_BIN_DIR:-}"

if [[ -z "${PG_BIN_DIR}" && -x /opt/homebrew/opt/postgresql@16/bin/pg_dump ]]; then
  PG_BIN_DIR="/opt/homebrew/opt/postgresql@16/bin"
fi
PG_DUMP="${PG_BIN_DIR:+${PG_BIN_DIR}/}pg_dump"
PG_RESTORE="${PG_BIN_DIR:+${PG_BIN_DIR}/}pg_restore"
PSQL="${PG_BIN_DIR:+${PG_BIN_DIR}/}psql"
CREATEDB="${PG_BIN_DIR:+${PG_BIN_DIR}/}createdb"

if [[ -z "${NAS_SSH_TARGET}" ]]; then
  echo "NAS_SSH_TARGET is required, for example: NAS_SSH_TARGET=admin@greennas $0" >&2
  exit 1
fi

for command_name in ssh socat "${PG_DUMP}" "${PG_RESTORE}" "${PSQL}" "${CREATEDB}"; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
done
if ! ssh "${NAS_SSH_TARGET}" "command -v nc >/dev/null 2>&1"; then
  echo "Missing required command on NAS: nc" >&2
  exit 1
fi

mkdir -p "${DUMP_DIR}"
timestamp="$(date +%Y%m%d%H%M%S)"
dump_file="${DUMP_DIR}/nas-${timestamp}.dump"
env_file="$(mktemp)"
tunnel_pid=""

cleanup() {
  rm -f "${env_file}"
  if [[ -n "${tunnel_pid}" ]] && kill -0 "${tunnel_pid}" >/dev/null 2>&1; then
    kill "${tunnel_pid}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "[1/6] Fetch NAS database environment..."
ssh "${NAS_SSH_TARGET}" "cat '${NAS_ENV_PATH}'" > "${env_file}"

read_env_value() {
  local key="$1"
  local value
  value="$(awk -F= -v key="${key}" '$1 == key {print substr($0, length(key) + 2); exit}' "${env_file}")"
  value="${value%\"}"
  value="${value#\"}"
  value="${value%\'}"
  value="${value#\'}"
  printf '%s' "${value}"
}

POSTGRES_DB="$(read_env_value POSTGRES_DB)"
POSTGRES_USER="$(read_env_value POSTGRES_USER)"
POSTGRES_PASSWORD="$(read_env_value POSTGRES_PASSWORD)"

: "${POSTGRES_DB:?POSTGRES_DB is missing from NAS env}"
: "${POSTGRES_USER:?POSTGRES_USER is missing from NAS env}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is missing from NAS env}"

echo "[2/6] Open local relay 127.0.0.1:${LOCAL_TUNNEL_PORT} -> NAS 127.0.0.1:${NAS_POSTGRES_PORT}..."
socat "TCP-LISTEN:${LOCAL_TUNNEL_PORT},bind=127.0.0.1,reuseaddr,fork" \
  "EXEC:ssh -o BatchMode=yes ${NAS_SSH_TARGET} nc 127.0.0.1 ${NAS_POSTGRES_PORT}" &
tunnel_pid="$!"
sleep 2

echo "[3/6] Dump NAS database ${POSTGRES_DB}..."
PGPASSWORD="${POSTGRES_PASSWORD}" "${PG_DUMP}" \
  -h 127.0.0.1 \
  -p "${LOCAL_TUNNEL_PORT}" \
  -U "${POSTGRES_USER}" \
  -d "${POSTGRES_DB}" \
  -Fc \
  -f "${dump_file}"

echo "[4/6] Ensure local database ${LOCAL_POSTGRES_DB} exists..."
if [[ -n "${LOCAL_POSTGRES_PASSWORD}" ]]; then
  export PGPASSWORD="${LOCAL_POSTGRES_PASSWORD}"
else
  unset PGPASSWORD || true
fi

"${CREATEDB}" \
  -h "${LOCAL_POSTGRES_HOST}" \
  -p "${LOCAL_POSTGRES_PORT}" \
  -U "${LOCAL_POSTGRES_USER}" \
  "${LOCAL_POSTGRES_DB}" >/dev/null 2>&1 || true

echo "[5/6] Restore dump into local database ${LOCAL_POSTGRES_DB}..."
"${PG_RESTORE}" \
  -h "${LOCAL_POSTGRES_HOST}" \
  -p "${LOCAL_POSTGRES_PORT}" \
  -U "${LOCAL_POSTGRES_USER}" \
  -d "${LOCAL_POSTGRES_DB}" \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  "${dump_file}"

echo "[6/6] Verify local table counts..."
"${PSQL}" \
  -h "${LOCAL_POSTGRES_HOST}" \
  -p "${LOCAL_POSTGRES_PORT}" \
  -U "${LOCAL_POSTGRES_USER}" \
  -d "${LOCAL_POSTGRES_DB}" \
  -v ON_ERROR_STOP=1 \
  -c "SELECT schemaname, COUNT(*) AS table_count FROM pg_tables WHERE tablename LIKE 'gak_%' GROUP BY schemaname;" \
  -c "SELECT 'gak_user' AS table_name, COUNT(*) FROM gak_user UNION ALL SELECT 'gak_system_app', COUNT(*) FROM gak_system_app UNION ALL SELECT 'gak_data_dictionary', COUNT(*) FROM gak_data_dictionary;"

echo "Done. Dump saved at ${dump_file}"
