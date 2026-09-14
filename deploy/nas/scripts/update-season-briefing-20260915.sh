#!/usr/bin/env bash
set -euo pipefail

# 此脚本需在 NAS 通过 sudo 执行，保留旧镜像和配置供回滚。
cd /volume1/Projects/GAK-App
RELEASE_IMAGE='gak-app:season-briefing-20260915'
BACKUP_IMAGE="gak-app:before-season-briefing-$(date +%Y%m%d-%H%M%S)"
docker image tag "$(docker inspect --format '{{.Image}}' gak-app)" "${BACKUP_IMAGE}"
docker load -i gak-season-briefing-20260915.tar

# 只重建后端，避免情报更新触发初始化 SQL 对业务种子数据的改写。
OVERRIDE_FILE="$(mktemp /volume1/Projects/GAK-App/season-briefing-override.XXXXXX.yml)"
printf 'services:\n  gak-app:\n    image: %s\n    environment:\n      SPRING_SQL_INIT_MODE: never\n' "${RELEASE_IMAGE}" > "${OVERRIDE_FILE}"
docker compose -f docker-compose.yml -f "${OVERRIDE_FILE}" up -d --no-deps gak-app
for attempt in {1..30}; do
  if curl -fsS http://127.0.0.1:18081/wow-characters/season | grep -q '国服情报更新于 2026-09-15'; then
    printf '赛季情报已发布。旧镜像：%s\n本次覆盖配置：%s\n' "${BACKUP_IMAGE}" "${OVERRIDE_FILE}"
    exit 0
  fi
  sleep 2
done

printf '新版本验证未通过，恢复旧后端镜像。\n' >&2
printf 'services:\n  gak-app:\n    image: %s\n    environment:\n      SPRING_SQL_INIT_MODE: never\n' "${BACKUP_IMAGE}" > "${OVERRIDE_FILE}"
docker compose -f docker-compose.yml -f "${OVERRIDE_FILE}" up -d --no-deps gak-app
exit 1
