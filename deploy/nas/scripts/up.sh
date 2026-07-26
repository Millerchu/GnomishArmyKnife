#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKER_CMD="${DOCKER_CMD:-docker}"

if ! command -v "${DOCKER_CMD%% *}" >/dev/null 2>&1; then
  echo "docker is required"
  exit 1
fi

cd "${DEPLOY_DIR}"

if [[ ! -f ".env" ]]; then
  echo ".env not found, creating from .env.example"
  cp .env.example .env
  echo "Please edit deploy/nas/.env and re-run."
  exit 1
fi

echo "[1/3] Starting containers ..."
if [[ "${ENABLE_REDIS:-false}" == "true" ]]; then
  ${DOCKER_CMD} compose --env-file .env -f docker-compose.yml --profile redis up -d
else
  ${DOCKER_CMD} compose --env-file .env -f docker-compose.yml up -d
fi

echo "[2/3] Current status:"
${DOCKER_CMD} compose --env-file .env -f docker-compose.yml ps

APP_PORT="$(sed -n 's/^APP_PORT=//p' .env | tail -n 1)"
APP_PORT="${APP_PORT:-18081}"
echo "[3/3] Waiting for backend health check ..."
for attempt in $(seq 1 30); do
  if curl -fsS --max-time 2 "http://127.0.0.1:${APP_PORT}/auth/captcha" >/dev/null; then
    echo "Backend is ready: http://127.0.0.1:${APP_PORT}"
    exit 0
  fi
  sleep 2
done

echo "Backend did not become ready; recent gak-app logs:" >&2
${DOCKER_CMD} compose --env-file .env -f docker-compose.yml logs --tail=120 gak-app >&2
exit 1
