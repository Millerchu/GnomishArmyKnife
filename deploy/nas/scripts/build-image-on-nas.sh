#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
DEPLOY_DIR="${PROJECT_ROOT}/deploy/nas"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required"
  exit 1
fi

cd "${PROJECT_ROOT}"
echo "[1/2] Building image gak-app:1.0.0 ..."
docker build -f "${DEPLOY_DIR}/Dockerfile" -t gak-app:1.0.0 .

echo "[2/2] Build complete."
