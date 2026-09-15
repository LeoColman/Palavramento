#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 Leonardo Colman Lopes
#
# Publica uma versão no ritalee (ADR 0013): envia o código versionado de [ref] (padrão HEAD,
# via git archive, então sem build/ nem local.properties) para
# /root/manual-stacks/palavramento/src e roda deploy/deploy.sh lá.
#
#   deploy/publish.sh            # publica o HEAD
#   deploy/publish.sh 1a2b3c4    # publica (ou volta para) outro commit
set -euo pipefail

REF="${1:-HEAD}"
HOST="${DEPLOY_HOST:-root@ritalee.colman.com.br}"
STACK_DIR="/root/manual-stacks/palavramento"
cd "$(git rev-parse --show-toplevel)"

echo ">> enviando $(git rev-parse --short "$REF") para $HOST:$STACK_DIR/src"
git archive --format=tar "$REF" | ssh "$HOST" "set -e
  mkdir -p $STACK_DIR
  rm -rf $STACK_DIR/src.new && mkdir $STACK_DIR/src.new
  tar -x -C $STACK_DIR/src.new
  rm -rf $STACK_DIR/src && mv $STACK_DIR/src.new $STACK_DIR/src"

ssh "$HOST" "bash $STACK_DIR/src/deploy/deploy.sh"
