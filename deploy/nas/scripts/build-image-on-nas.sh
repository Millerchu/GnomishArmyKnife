#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
WORKSPACE_ROOT="$(cd "${BACKEND_ROOT}/.." && pwd)"
FRONTEND_ROOT="${FRONTEND_ROOT:-${WORKSPACE_ROOT}/GnomishArmyKnife-Web}"
DEPLOY_DIR="${BACKEND_ROOT}/deploy/nas"
IMAGE_TAG="${IMAGE_TAG:-1.0.0}"
TARGET_PLATFORM="${TARGET_PLATFORM:-linux/amd64}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required"
  exit 1
fi

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

echo "[1/3] Building backend image gak-app:${IMAGE_TAG} ..."
build_image -f "${DEPLOY_DIR}/Dockerfile" -t "gak-app:${IMAGE_TAG}" "${BACKEND_ROOT}"

echo "[2/3] Building frontend image gak-web:${IMAGE_TAG} ..."
build_image -t "gak-web:${IMAGE_TAG}" "${FRONTEND_ROOT}"

echo "[3/3] Build complete."
