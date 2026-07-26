#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SOURCE_BRIDGE_DIR="${DEPLOY_DIR}/sso-bridge"
SOURCE_NGINX_TEMPLATE="${DEPLOY_DIR}/nginx/gak_sso_bridge.conf.template"
TARGET_BRIDGE_DIR="${GAK_SSO_BRIDGE_DIR:-/volume1/Projects/GAK-App/sso-bridge}"
TARGET_NGINX_CONFIG="${GAK_SSO_NGINX_CONFIG:-/etc/nginx/conf.d/gak_sso_bridge.conf}"
GAK_WEB_PORT="${GAK_WEB_PORT:-}"
NGINX_BIN="${NGINX_BIN:-nginx}"
SUDO_CMD="${SUDO_CMD:-sudo}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
WORK_DIR="$(mktemp -d)"
BACKUP_CONFIG=""

cleanup() {
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

if [[ -z "${GAK_WEB_PORT}" && -f "${DEPLOY_DIR}/.env" ]]; then
  GAK_WEB_PORT="$(sed -n 's/^WEB_PORT=//p' "${DEPLOY_DIR}/.env" | tail -n 1)"
fi
GAK_WEB_PORT="${GAK_WEB_PORT:-18088}"

if [[ ! "${GAK_WEB_PORT}" =~ ^[0-9]{1,5}$ ]] || ((GAK_WEB_PORT < 1 || GAK_WEB_PORT > 65535)); then
  echo "GAK_WEB_PORT must be a valid TCP port: ${GAK_WEB_PORT}" >&2
  exit 1
fi
if [[ ! "${TARGET_BRIDGE_DIR}" =~ ^/[A-Za-z0-9._/-]+$ ]]; then
  echo "GAK_SSO_BRIDGE_DIR contains unsupported characters: ${TARGET_BRIDGE_DIR}" >&2
  exit 1
fi
if [[ ! "${TARGET_NGINX_CONFIG}" =~ ^/[A-Za-z0-9._/-]+$ ]]; then
  echo "GAK_SSO_NGINX_CONFIG contains unsupported characters: ${TARGET_NGINX_CONFIG}" >&2
  exit 1
fi

escape_sed_replacement() {
  printf '%s' "$1" | sed 's/[&|\\]/\\&/g'
}

if [[ ! -f "${SOURCE_BRIDGE_DIR}/bridge.html" || ! -f "${SOURCE_BRIDGE_DIR}/bridge.js" ]]; then
  echo "SSO bridge source files are missing: ${SOURCE_BRIDGE_DIR}" >&2
  exit 1
fi
if [[ ! -f "${SOURCE_NGINX_TEMPLATE}" ]]; then
  echo "Nginx template is missing: ${SOURCE_NGINX_TEMPLATE}" >&2
  exit 1
fi

escaped_bridge_dir="$(escape_sed_replacement "${TARGET_BRIDGE_DIR}")"
cp "${SOURCE_BRIDGE_DIR}/bridge.html" "${WORK_DIR}/bridge.html"
cp "${SOURCE_BRIDGE_DIR}/bridge.js" "${WORK_DIR}/bridge.js"
sed \
  -e "s|__GAK_SSO_BRIDGE_DIR__|${escaped_bridge_dir}|g" \
  -e "s|__GAK_WEB_PORT__|${GAK_WEB_PORT}|g" \
  "${SOURCE_NGINX_TEMPLATE}" > "${WORK_DIR}/gak_sso_bridge.conf"

mkdir -p "${TARGET_BRIDGE_DIR}"
install -m 0644 "${WORK_DIR}/bridge.html" "${TARGET_BRIDGE_DIR}/bridge.html"
install -m 0644 "${WORK_DIR}/bridge.js" "${TARGET_BRIDGE_DIR}/bridge.js"

if ${SUDO_CMD} test -f "${TARGET_NGINX_CONFIG}"; then
  BACKUP_CONFIG="${TARGET_NGINX_CONFIG}.bak.${TIMESTAMP}"
  ${SUDO_CMD} cp "${TARGET_NGINX_CONFIG}" "${BACKUP_CONFIG}"
fi
${SUDO_CMD} install -m 0644 "${WORK_DIR}/gak_sso_bridge.conf" "${TARGET_NGINX_CONFIG}"

if ! ${SUDO_CMD} "${NGINX_BIN}" -t; then
  echo "Nginx validation failed; restoring the previous configuration." >&2
  if [[ -n "${BACKUP_CONFIG}" ]]; then
    ${SUDO_CMD} cp "${BACKUP_CONFIG}" "${TARGET_NGINX_CONFIG}"
  else
    ${SUDO_CMD} rm -f "${TARGET_NGINX_CONFIG}"
  fi
  ${SUDO_CMD} "${NGINX_BIN}" -t
  exit 1
fi

${SUDO_CMD} "${NGINX_BIN}" -s reload
echo "GAK SSO bridge and gateway installed: /gak/ -> 127.0.0.1:${GAK_WEB_PORT}"
if [[ -n "${BACKUP_CONFIG}" ]]; then
  echo "Previous Nginx configuration backup: ${BACKUP_CONFIG}"
fi
