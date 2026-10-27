#!/bin/bash
# Despliegue en el EC2. Copiar a ~/app/deploy.sh junto con docker-compose.prod.yml y .env
set -e

echo "==> Iniciando despliegue en $(date)"
cd ~/app

# Toma la imagen de ${DOCKER_HUB_USERNAME} definida en .env
docker compose -f docker-compose.prod.yml pull api
docker compose -f docker-compose.prod.yml down --remove-orphans || true
docker compose -f docker-compose.prod.yml up -d

sleep 20
docker compose -f docker-compose.prod.yml ps

curl -f http://localhost:8085/health \
  && echo "API UP" \
  || echo "ADVERTENCIA: API no responde"

echo "==> Despliegue completado en $(date)"
