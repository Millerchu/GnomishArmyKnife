#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKER_CMD="${DOCKER_CMD:-docker}"

cd "${DEPLOY_DIR}"
services=(gak-web gak-app postgres)
if [[ "${ENABLE_REDIS:-false}" == "true" ]]; then
  services+=(redis)
fi

${DOCKER_CMD} compose --env-file .env -f docker-compose.yml logs -f --tail=200 "${services[@]}"
