#!/usr/bin/env bash
# 服务器上手动更新（Jenkins 不可用时的兜底）
# 用法：./deploy/update.sh [IMAGE_TAG]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

TAG="${1:-}"
if [[ -n "$TAG" ]]; then
  if grep -q '^IMAGE_TAG=' .env 2>/dev/null; then
    sed -i.bak "s|^IMAGE_TAG=.*|IMAGE_TAG=${TAG}|" .env
  else
    echo "IMAGE_TAG=${TAG}" >> .env
  fi
fi

docker compose pull app
docker compose up -d app
docker compose ps
docker compose logs --tail=80 app
