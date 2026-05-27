#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKER_CMD="${DOCKER_CMD:-docker}"

cd "${DEPLOY_DIR}"
${DOCKER_CMD} compose --env-file .env -f docker-compose.yml down
