#!/usr/bin/env bash
# Shared local-side SSH configuration and deployment preflight.
configure_deploy_connection() {
  [[ "$IMAGE_TAG" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$ ]] || { echo 'Invalid Docker IMAGE_TAG' >&2; return 1; }
  SSH_OPTIONS=(-o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=10)
  if [[ -n "${SSH_IDENTITY_FILE:-}" ]]; then
    [[ -r "$SSH_IDENTITY_FILE" ]] || { echo "SSH private key not readable: $SSH_IDENTITY_FILE" >&2; return 1; }
    SSH_OPTIONS+=(-i "$SSH_IDENTITY_FILE" -o IdentitiesOnly=yes)
  fi
  if [[ -n "${SSH_KNOWN_HOSTS_FILE:-}" ]]; then
    [[ -r "$SSH_KNOWN_HOSTS_FILE" ]] || { echo "SSH known-hosts file not readable: $SSH_KNOWN_HOSTS_FILE" >&2; return 1; }
    SSH_OPTIONS+=(-o "UserKnownHostsFile=$SSH_KNOWN_HOSTS_FILE")
  fi
}

check_remote_deployment() {
  # Fail closed: never fall back to interactive SSH or sudo password prompts.
  ssh "${SSH_OPTIONS[@]}" -p "$SSH_PORT" "$SSH_TARGET" true
  case "${REMOTE_DOCKER_CMD:-}" in
    '')
      if ssh "${SSH_OPTIONS[@]}" -p "$SSH_PORT" "$SSH_TARGET" 'docker info >/dev/null 2>&1'; then
        REMOTE_DOCKER_CMD=docker
      else
        REMOTE_DOCKER_CMD='sudo -n docker'
      fi
      ;;
    'sudo docker') REMOTE_DOCKER_CMD='sudo -n docker' ;;
    docker|'sudo -n docker') ;;
    *) echo 'REMOTE_DOCKER_CMD must be docker or sudo -n docker' >&2; return 1 ;;
  esac
  ssh "${SSH_OPTIONS[@]}" -p "$SSH_PORT" "$SSH_TARGET" "$REMOTE_DOCKER_CMD info >/dev/null && $REMOTE_DOCKER_CMD compose version"
  echo "NAS SSH and Docker checks passed ($REMOTE_DOCKER_CMD)."
}

check_local_build() {
  # Homebrew keg-only Node and Docker Desktop CLI on this Mac; no system PATH changes.
  for runtime_dir in /opt/homebrew/opt/node@22/bin /Applications/Docker.app/Contents/Resources/bin; do
    [[ ! -d "$runtime_dir" ]] || export PATH="$PATH:$runtime_dir"
  done
  command -v docker >/dev/null || { echo 'Docker CLI is required.' >&2; return 1; }
  docker info >/dev/null 2>&1 || { echo 'Local Docker engine is unavailable. Start Docker Desktop and retry; nothing has been uploaded or restarted.' >&2; return 1; }
  command -v npm >/dev/null || { echo 'Node.js/npm is required.' >&2; return 1; }
  node -e 'const [major,minor]=process.versions.node.split(".").map(Number);if(!((major===20&&minor>=19)||(major===22&&minor>=12)||major>22))process.exit(1)' || { echo 'Node.js 20.19+ or 22.12+ is required.' >&2; return 1; }
  [[ -d "$FRONTEND_ROOT" ]] || { echo "Frontend directory missing: $FRONTEND_ROOT" >&2; return 1; }
  echo 'Local build environment checks passed.'
}
