#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

if ! command -v docker >/dev/null 2>&1; then
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
docker compose --env-file .env -f docker-compose.yml up -d

echo "[2/2] Current status:"
docker compose --env-file .env -f docker-compose.yml ps
