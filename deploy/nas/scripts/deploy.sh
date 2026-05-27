#!/usr/bin/env bash
set -euo pipefail

########################
# Base Config
########################
NAS_USER="${NAS_USER:-millerchu}"
NAS_HOST="${NAS_HOST:-greennas}"

NAS_SCP_DIR="${NAS_SCP_DIR:-/Projects/GAK-App}"
NAS_SSH_DIR="${NAS_SSH_DIR:-/volume1/Projects/GAK-App}"
SSH_PORT="${SSH_PORT:-22}"

BACKEND_IMAGE_NAME="gak-app"
FRONTEND_IMAGE_NAME="gak-web"
IMAGE_TAG="${IMAGE_TAG:-1.0.0}"
TARGET_PLATFORM="${TARGET_PLATFORM:-linux/amd64}"
REMOTE_DOCKER_CMD="${REMOTE_DOCKER_CMD:-docker}"

TIME="$(date +"%Y%m%d_%H%M%S")"
IMAGE_TAR="gak_images_${IMAGE_TAG}_${TIME}.tar"
BUNDLE_TGZ="deploy_nas_${TIME}.tar.gz"
REMOTE_SCRIPT="deploy_${TIME}.sh"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_ROOT="$(cd "${DEPLOY_DIR}/../.." && pwd)"
WORKSPACE_ROOT="$(cd "${BACKEND_ROOT}/.." && pwd)"
FRONTEND_ROOT="${FRONTEND_ROOT:-${WORKSPACE_ROOT}/GnomishArmyKnife-Web}"

########################
# Banner
########################
echo ""
echo "======================================"
echo " NAS Backend Deploy Script"
echo " Host      : ${NAS_HOST}"
echo " DeployDir : ${NAS_SSH_DIR}"
echo " Images    : ${BACKEND_IMAGE_NAME}:${IMAGE_TAG}, ${FRONTEND_IMAGE_NAME}:${IMAGE_TAG}"
echo " Platform  : ${TARGET_PLATFORM}"
echo " Docker    : ${REMOTE_DOCKER_CMD}"
echo " Time      : ${TIME}"
echo "======================================"
echo ""

if [[ ! -d "${FRONTEND_ROOT}" ]]; then
  echo "frontend project not found: ${FRONTEND_ROOT}"
  echo "Set FRONTEND_ROOT=/path/to/GnomishArmyKnife-Web and retry."
  exit 1
fi

build_image() {
  if docker buildx version >/dev/null 2>&1; then
    docker buildx build --platform "${TARGET_PLATFORM}" --load "$@"
  else
    docker build --platform "${TARGET_PLATFORM}" "$@"
  fi
}

########################
# 1. Build Images
########################
echo "[1/7] Build docker images..."
(cd "${BACKEND_ROOT}" && ./mvnw -B -DskipTests clean package)
build_image -f "${DEPLOY_DIR}/Dockerfile.prebuilt" -t "${BACKEND_IMAGE_NAME}:${IMAGE_TAG}" "${BACKEND_ROOT}"
(cd "${FRONTEND_ROOT}" && npm ci && npm run build)
build_image -f "${FRONTEND_ROOT}/Dockerfile.prebuilt" -t "${FRONTEND_IMAGE_NAME}:${IMAGE_TAG}" "${FRONTEND_ROOT}"

########################
# 2. Export Images
########################
echo "[2/7] Export image tar..."
docker save "${BACKEND_IMAGE_NAME}:${IMAGE_TAG}" "${FRONTEND_IMAGE_NAME}:${IMAGE_TAG}" -o "${IMAGE_TAR}"

########################
# 3. Pack deploy files
########################
echo "[3/7] Package deploy files..."
tar --no-xattrs -C "${DEPLOY_DIR}" -zcf "${BUNDLE_TGZ}" docker-compose.yml .env.example scripts

########################
# 4. Upload
########################
echo "[4/7] Upload package to NAS..."
ssh -p "${SSH_PORT}" "${NAS_USER}@${NAS_HOST}" "mkdir -p '${NAS_SSH_DIR}'"
scp -P "${SSH_PORT}" "${IMAGE_TAR}" "${NAS_USER}@${NAS_HOST}:${NAS_SCP_DIR}/"
scp -P "${SSH_PORT}" "${BUNDLE_TGZ}" "${NAS_USER}@${NAS_HOST}:${NAS_SCP_DIR}/"

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
${REMOTE_DOCKER_CMD} load -i "${IMAGE_TAR}"

if [ ! -f .env ]; then
  cp .env.example .env
  echo ".env created from .env.example, please verify credentials."
fi

chmod +x scripts/*.sh

echo "Start services..."
DOCKER_CMD="${REMOTE_DOCKER_CMD}" ./scripts/up.sh

echo "Cleanup upload package..."
rm -f "${IMAGE_TAR}" "${BUNDLE_TGZ}"

echo "Deploy success."
EOF

chmod +x "${REMOTE_SCRIPT}"

########################
# 6. Execute remote deploy
########################
echo "[5/7] Upload remote deploy script..."
scp -P "${SSH_PORT}" "${REMOTE_SCRIPT}" "${NAS_USER}@${NAS_HOST}:${NAS_SCP_DIR}/"

echo "[6/7] Execute deploy on NAS..."
ssh -tt -p "${SSH_PORT}" "${NAS_USER}@${NAS_HOST}" "cd '${NAS_SSH_DIR}' && chmod +x '${REMOTE_SCRIPT}' && ./'${REMOTE_SCRIPT}' && rm -f '${REMOTE_SCRIPT}'"

########################
# 7. Clean local temp files
########################
echo "[7/7] Clean local temp files..."
rm -f "${IMAGE_TAR}" "${BUNDLE_TGZ}" "${REMOTE_SCRIPT}"

echo ""
echo "Deploy finished successfully."
echo "======================================"
