#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage:"
  echo "  $0 <nas_user@nas_host> <nas_scp_dir> <nas_ssh_dir> [ssh_port]"
  echo "  $0 <nas_user@nas_host> <nas_project_dir> [ssh_port]"
  echo ""
  echo "Example for UGREEN NAS:"
  echo "  $0 admin@192.168.1.20 /Projects/GAK-App /volume1/Projects/GAK-App 22"
  exit 1
fi

NAS_HOST="$1"
NAS_SCP_DIR="$2"
if [[ $# -ge 3 && ! "$3" =~ ^[0-9]+$ ]]; then
  NAS_SSH_DIR="$3"
  SSH_PORT="${4:-22}"
else
  NAS_SSH_DIR="$2"
  SSH_PORT="${3:-22}"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
WORKSPACE_ROOT="$(cd "${BACKEND_ROOT}/.." && pwd)"
FRONTEND_ROOT="${FRONTEND_ROOT:-${WORKSPACE_ROOT}/GnomishArmyKnife-Web}"
DEPLOY_DIR="${BACKEND_ROOT}/deploy/nas"
DIST_DIR="${DEPLOY_DIR}/dist"
IMAGE_TAG="${IMAGE_TAG:-1.0.0}"
TARGET_PLATFORM="${TARGET_PLATFORM:-linux/amd64}"
REMOTE_DOCKER_CMD="${REMOTE_DOCKER_CMD:-docker}"

mkdir -p "${DIST_DIR}"

echo ""
echo "======================================"
echo " NAS Package And Upload"
echo " Host      : ${NAS_HOST}"
echo " SCP Dir   : ${NAS_SCP_DIR}"
echo " SSH Dir   : ${NAS_SSH_DIR}"
echo " Image Tag : ${IMAGE_TAG}"
echo " Platform  : ${TARGET_PLATFORM}"
echo " Remote Docker : ${REMOTE_DOCKER_CMD}"
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

echo "[1/6] Build images locally ..."
(cd "${BACKEND_ROOT}" && ./mvnw -B -DskipTests clean package)
build_image -f "${DEPLOY_DIR}/Dockerfile.prebuilt" -t "gak-app:${IMAGE_TAG}" "${BACKEND_ROOT}"
(cd "${FRONTEND_ROOT}" && npm ci && npm run build)
build_image -f "${FRONTEND_ROOT}/Dockerfile.prebuilt" -t "gak-web:${IMAGE_TAG}" "${FRONTEND_ROOT}"

echo "[2/6] Save images as tar ..."
docker save "gak-app:${IMAGE_TAG}" "gak-web:${IMAGE_TAG}" -o "${DIST_DIR}/gak-images-${IMAGE_TAG}.tar"

echo "[3/6] Pack deploy files ..."
tar --no-xattrs -C "${DEPLOY_DIR}" -czf "${DIST_DIR}/deploy-nas.tar.gz" docker-compose.yml .env.example scripts

echo "[4/6] Upload to NAS ..."
ssh -p "${SSH_PORT}" "${NAS_HOST}" "mkdir -p '${NAS_SSH_DIR}'"
scp -P "${SSH_PORT}" "${DIST_DIR}/gak-images-${IMAGE_TAG}.tar" "${NAS_HOST}:${NAS_SCP_DIR}/"
scp -P "${SSH_PORT}" "${DIST_DIR}/deploy-nas.tar.gz" "${NAS_HOST}:${NAS_SCP_DIR}/"

echo "[5/6] Load image and extract files on NAS ..."
ssh -tt -p "${SSH_PORT}" "${NAS_HOST}" "cd '${NAS_SSH_DIR}' && ${REMOTE_DOCKER_CMD} load -i 'gak-images-${IMAGE_TAG}.tar' && tar -xzf deploy-nas.tar.gz && chmod +x scripts/*.sh"

echo "[6/6] Done. On NAS run:"
echo "cd ${NAS_SSH_DIR}"
echo "cp .env.example .env && vi .env"
echo "DOCKER_CMD=\"${REMOTE_DOCKER_CMD}\" ./scripts/up.sh"
