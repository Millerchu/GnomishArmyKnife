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

echo "[1/2] Starting containers ..."
if [[ "${ENABLE_REDIS:-false}" == "true" ]]; then
  ${DOCKER_CMD} compose --env-file .env -f docker-compose.yml --profile redis up -d
else
  ${DOCKER_CMD} compose --env-file .env -f docker-compose.yml up -d
fi

echo "[2/2] Current status:"
${DOCKER_CMD} compose --env-file .env -f docker-compose.yml ps
