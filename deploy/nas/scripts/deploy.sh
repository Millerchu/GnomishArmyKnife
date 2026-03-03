#!/usr/bin/env bash
set -euo pipefail

########################
# Base Config
########################
NAS_USER="millerchu"
NAS_HOST="greennas"

NAS_SCP_DIR="/Projects/GAK-App"
NAS_SSH_DIR="/volume1/Projects/GAK-App"

IMAGE_NAME="gak-app"
IMAGE_TAG="1.0.0"

TIME="$(date +"%Y%m%d_%H%M%S")"
IMAGE_TAR="${IMAGE_NAME}_${IMAGE_TAG}_${TIME}.tar"
BUNDLE_TGZ="deploy_nas_${TIME}.tar.gz"
REMOTE_SCRIPT="deploy_${TIME}.sh"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_ROOT="$(cd "${DEPLOY_DIR}/../.." && pwd)"

########################
# Banner
########################
echo ""
echo "======================================"
echo " NAS Backend Deploy Script"
echo " Host      : ${NAS_HOST}"
echo " DeployDir : ${NAS_SSH_DIR}"
echo " Image     : ${IMAGE_NAME}:${IMAGE_TAG}"
echo " Time      : ${TIME}"
echo "======================================"
echo ""

########################
# 1. Build Image
########################
echo "[1/7] Build docker image..."
cd "${PROJECT_ROOT}"
docker build -f "${DEPLOY_DIR}/Dockerfile" -t "${IMAGE_NAME}:${IMAGE_TAG}" .

########################
# 2. Export Image
########################
echo "[2/7] Export image tar..."
docker save "${IMAGE_NAME}:${IMAGE_TAG}" -o "${IMAGE_TAR}"

########################
# 3. Pack deploy files
########################
echo "[3/7] Package deploy files..."
tar -C "${DEPLOY_DIR}" -zcf "${BUNDLE_TGZ}" docker-compose.yml .env.example scripts

########################
# 4. Upload
########################
echo "[4/7] Upload package to NAS..."
ssh "${NAS_USER}@${NAS_HOST}" "mkdir -p '${NAS_SSH_DIR}'"
scp "${IMAGE_TAR}" "${NAS_USER}@${NAS_HOST}:${NAS_SCP_DIR}/"
scp "${BUNDLE_TGZ}" "${NAS_USER}@${NAS_HOST}:${NAS_SCP_DIR}/"

########################
# 5. Generate remote deploy script
########################
cat > "${REMOTE_SCRIPT}" << EOF
#!/bin/sh
set -e

cd "${NAS_SSH_DIR}"
mkdir -p backup

if [ -f .env ]; then
  cp .env "backup/env_${TIME}.bak"
fi

if [ -f docker-compose.yml ]; then
  cp docker-compose.yml "backup/docker-compose_${TIME}.yml"
fi

echo "Extract deploy bundle..."
tar -zxf "${BUNDLE_TGZ}"

echo "Load docker image..."
docker load -i "${IMAGE_TAR}"

if [ ! -f .env ]; then
  cp .env.example .env
  echo ".env created from .env.example, please verify credentials."
fi

chmod +x scripts/*.sh

echo "Start services..."
docker compose --env-file .env -f docker-compose.yml up -d

echo "Cleanup upload package..."
rm -f "${IMAGE_TAR}" "${BUNDLE_TGZ}"

echo "Deploy success."
EOF

chmod +x "${REMOTE_SCRIPT}"

########################
# 6. Execute remote deploy
########################
echo "[5/7] Upload remote deploy script..."
scp "${REMOTE_SCRIPT}" "${NAS_USER}@${NAS_HOST}:${NAS_SCP_DIR}/"

echo "[6/7] Execute deploy on NAS..."
ssh -tt "${NAS_USER}@${NAS_HOST}" "cd '${NAS_SSH_DIR}' && chmod +x '${REMOTE_SCRIPT}' && ./'${REMOTE_SCRIPT}' && rm -f '${REMOTE_SCRIPT}'"

########################
# 7. Clean local temp files
########################
echo "[7/7] Clean local temp files..."
rm -f "${IMAGE_TAR}" "${BUNDLE_TGZ}" "${REMOTE_SCRIPT}"

echo ""
echo "Deploy finished successfully."
echo "======================================"
