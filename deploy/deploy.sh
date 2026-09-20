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

for var in APP_HOST POSTGRES_PASSWORD JWT_SECRET METRICS_TOKEN GRAFANA_HOST GRAFANA_ADMIN_PASSWORD \
           BORG_PASSPHRASE; do
  if [[ -z "${!var:-}" ]]; then
    echo "Erro: $var vazio no .env" >&2
    exit 1
  fi
done

# Tag única por build. Com a mesma tag o Swarm mantém o digest já fixado no service e o
# rollout vira no-op: a imagem nova simplesmente não sobe.
IMAGE_TAG="$(date +%Y%m%d%H%M%S)"
export IMAGE_TAG

# O Swarm exige que os diretórios dos bind mounts já existam.
mkdir -p pgdata prometheus-data grafana-data

# Prometheus (uid 65534, "nobody" na imagem oficial) e Grafana (uid 472) rodam sem root: a posse do
# diretório de dados precisa bater com o usuário do container, senão eles não conseguem escrever.
chown 65534:65534 prometheus-data
chown 472:472 grafana-data

# Arquivo com o token de métricas: nunca no git, nunca no .env do container. Só o dono (o
# Prometheus, mesmo uid 65534) consegue ler; root sempre pode reescrever para girar o token.
echo -n "$METRICS_TOKEN" > metrics_token
chmod 600 metrics_token
chown 65534:65534 metrics_token

echo ">> build palavramento-server:${IMAGE_TAG} (Gradle + léxico, alguns minutos)"
DOCKER_BUILDKIT=1 docker build -f src/deploy/Dockerfile -t "palavramento-server:${IMAGE_TAG}" src

echo ">> docker stack deploy palavramento (IMAGE_TAG=${IMAGE_TAG})"
docker stack deploy \
  --resolve-image never \
  -c src/deploy/docker-compose.yml \
  --detach=false \
  palavramento

docker service ls --filter name=palavramento
echo ">> pronto. Caddy publica o servidor em https://${APP_HOST} e o Grafana em https://${GRAFANA_HOST}"
