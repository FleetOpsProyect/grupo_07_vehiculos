#!/bin/bash
# Despliegue en el EC2: ./deploy.sh <image_tag>   (el tag es el SHA del commit que sube el CD)
# Vive en ~/app junto con docker-compose.prod.yml y .env. El CD lo copia en cada despliegue.
# Si la API nueva no queda UP, vuelve a la versión anterior (la guarda en .deployed_tag).
set -euo pipefail

TAG="${1:?Uso: ./deploy.sh <image_tag>}"
COMPOSE="docker compose -f docker-compose.prod.yml"

cd ~/app
PREVIOUS="$(cat .deployed_tag 2>/dev/null || true)"

wait_for_api() {
  for _ in $(seq 1 30); do
    if curl -sf http://localhost:8085/health > /dev/null; then
      return 0
    fi
    sleep 3
  done
  return 1
}

deploy() {
  export IMAGE_TAG="$1"
  $COMPOSE pull api
  $COMPOSE up -d --remove-orphans
  $COMPOSE ps
}

echo "==> Desplegando ${TAG} ($(date))"
deploy "${TAG}"

if wait_for_api; then
  echo "${TAG}" > .deployed_tag
  echo "==> API UP con ${TAG}"
  exit 0
fi

echo "==> ERROR: la API no respondió en /health"
$COMPOSE logs --tail=50 api || true
if [ -n "${PREVIOUS}" ] && [ "${PREVIOUS}" != "${TAG}" ]; then
  echo "==> Volviendo a ${PREVIOUS}"
  deploy "${PREVIOUS}"
fi
exit 1
