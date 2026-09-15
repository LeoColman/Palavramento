#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 Leonardo Colman Lopes
#
# Build da imagem + deploy do stack no Swarm. Roda NO SERVIDOR (ritalee), com o código em
# /root/manual-stacks/palavramento/src, enviado por deploy/publish.sh (ADR 0013).
set -euo pipefail
cd "$(dirname "$0")/../.."

if [[ ! -f .env ]]; then
  echo "Erro: .env não encontrado. Copie src/deploy/.env.example para .env e preencha." >&2
  exit 1
fi
set -a
source .env
set +a

for var in APP_HOST POSTGRES_PASSWORD JWT_SECRET; do
  if [[ -z "${!var:-}" ]]; then
    echo "Erro: $var vazio no .env" >&2
    exit 1
  fi
done

# Tag única por build. Com a mesma tag o Swarm mantém o digest já fixado no service e o
# rollout vira no-op: a imagem nova simplesmente não sobe.
IMAGE_TAG="$(date +%Y%m%d%H%M%S)"
export IMAGE_TAG

# O Swarm exige que o diretório do bind mount já exista.
mkdir -p pgdata

echo ">> build palavramento-server:${IMAGE_TAG} (Gradle + léxico, alguns minutos)"
DOCKER_BUILDKIT=1 docker build -f src/deploy/Dockerfile -t "palavramento-server:${IMAGE_TAG}" src

echo ">> docker stack deploy palavramento (IMAGE_TAG=${IMAGE_TAG})"
docker stack deploy \
  --resolve-image never \
  -c src/deploy/docker-compose.yml \
  --detach=false \
  palavramento

docker service ls --filter name=palavramento
echo ">> pronto. Caddy publica em https://${APP_HOST}"
