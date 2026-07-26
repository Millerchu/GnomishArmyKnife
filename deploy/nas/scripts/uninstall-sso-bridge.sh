#!/usr/bin/env bash
set -euo pipefail

TARGET_NGINX_CONFIG="${GAK_SSO_NGINX_CONFIG:-/etc/nginx/conf.d/gak_sso_bridge.conf}"
NGINX_BIN="${NGINX_BIN:-nginx}"
SUDO_CMD="${SUDO_CMD:-sudo}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
DISABLED_CONFIG="${TARGET_NGINX_CONFIG}.disabled.${TIMESTAMP}"

if ! ${SUDO_CMD} test -f "${TARGET_NGINX_CONFIG}"; then
  echo "GAK SSO Nginx configuration is not installed."
  exit 0
fi

${SUDO_CMD} mv "${TARGET_NGINX_CONFIG}" "${DISABLED_CONFIG}"
if ! ${SUDO_CMD} "${NGINX_BIN}" -t; then
  ${SUDO_CMD} mv "${DISABLED_CONFIG}" "${TARGET_NGINX_CONFIG}"
  ${SUDO_CMD} "${NGINX_BIN}" -t
  echo "Nginx validation failed; the GAK SSO configuration was restored." >&2
  exit 1
fi

${SUDO_CMD} "${NGINX_BIN}" -s reload
echo "GAK SSO bridge disabled. Recoverable configuration: ${DISABLED_CONFIG}"
