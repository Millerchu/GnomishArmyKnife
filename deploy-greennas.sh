#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export REMOTE_DOCKER_CMD="${REMOTE_DOCKER_CMD:-docker}"
export SSH_IDENTITY_FILE="${SSH_IDENTITY_FILE:-$HOME/.ssh/id_ed25519_gak_nas}"
export SSH_KNOWN_HOSTS_FILE="${SSH_KNOWN_HOSTS_FILE:-${SCRIPT_DIR}/../.local/nas_known_hosts}"
export NAS_USER="${NAS_USER:-millerchu}"
export NAS_HOST="${NAS_HOST:-greennas}"
export NAS_SCP_DIR="${NAS_SCP_DIR:-/Projects/GAK-App}"
export NAS_SSH_DIR="${NAS_SSH_DIR:-/volume1/Projects/GAK-App}"
export SSH_PORT="${SSH_PORT:-22}"

exec bash "${SCRIPT_DIR}/deploy/nas/scripts/deploy.sh" "$@"
