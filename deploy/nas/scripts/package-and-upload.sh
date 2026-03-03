#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <nas_user@nas_host> <nas_project_dir> [ssh_port]"
  echo "Example: $0 admin@192.168.1.20 /volume1/docker/gak 22"
  exit 1
fi

NAS_HOST="$1"
NAS_DIR="$2"
SSH_PORT="${3:-22}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
DEPLOY_DIR="${PROJECT_ROOT}/deploy/nas"
DIST_DIR="${DEPLOY_DIR}/dist"

mkdir -p "${DIST_DIR}"

echo "[1/6] Build image locally ..."
cd "${PROJECT_ROOT}"
docker build -f "${DEPLOY_DIR}/Dockerfile" -t gak-app:1.0.0 .

echo "[2/6] Save image as tar ..."
docker save gak-app:1.0.0 -o "${DIST_DIR}/gak-app-1.0.0.tar"

echo "[3/6] Pack deploy files ..."
tar -C "${DEPLOY_DIR}" -czf "${DIST_DIR}/deploy-nas.tar.gz" docker-compose.yml .env.example scripts

echo "[4/6] Upload to NAS ..."
ssh -p "${SSH_PORT}" "${NAS_HOST}" "mkdir -p '${NAS_DIR}'"
scp -P "${SSH_PORT}" "${DIST_DIR}/gak-app-1.0.0.tar" "${NAS_HOST}:${NAS_DIR}/"
scp -P "${SSH_PORT}" "${DIST_DIR}/deploy-nas.tar.gz" "${NAS_HOST}:${NAS_DIR}/"

echo "[5/6] Load image and extract files on NAS ..."
ssh -p "${SSH_PORT}" "${NAS_HOST}" "cd '${NAS_DIR}' && docker load -i gak-app-1.0.0.tar && tar -xzf deploy-nas.tar.gz && chmod +x scripts/*.sh"

echo "[6/6] Done. On NAS run:"
echo "cd ${NAS_DIR}"
echo "cp .env.example .env && vi .env"
echo "./scripts/up.sh"
