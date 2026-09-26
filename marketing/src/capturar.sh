#!/usr/bin/env bash
# Prepares the emulator for marketing captures and prints the rest of the recipe.
#
# The parts a script can own (demo status bar, launching the app, the gesture coordinates)
# it does. The parts that need a live round (which word to trace, when to let the round end)
# it prints, because they depend on what the server generated.
#
# Requires: the AVD booted, the local server running, and marketing/src/jogadores-de-teste.js
# filling the room. See the "Antes" section below.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$here/../.."

demo() { adb shell am broadcast -a com.android.systemui.demo "$@" > /dev/null; }

echo "== barra de status em modo demo =="
adb shell settings put global sysui_demo_allowed 1
demo -e command enter
demo -e command clock -e hhmm 1000
demo -e command battery -e level 100 -e plugged false
demo -e command network -e wifi show -e level 4 -e fully true -e mobile hide
demo -e command notifications -e visible false
demo -e command status -e volume hide -e bluetooth hide -e location hide -e alarm hide \
     -e sync hide -e tty hide -e eri hide -e mute hide -e zen hide -e cast hide -e hotspot hide

echo "== abrindo o app =="
adb shell monkey -p br.com.colman.palavramento -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1

cat <<'FIM'

== Antes (em outros terminais) ==

  docker compose up -d postgres
  ./gradlew :server:installDist
  server/build/install/server/bin/server

  Sem ROUND_DURATION_SECONDS nem INTERMISSION_DURATION_SECONDS: os padrões são os de produção
  (120 s de rodada, 25 s de intermissão), e é isso que o cronômetro precisa mostrar. Com rodada
  mais longa, por conforto, as capturas saem com 03:49 ou 02:53 no relógio, que o jogo nunca atinge,
  num material que promete "2 minutos".

  ./gradlew :app:installDebug -Ppalavramento.serverUrl=http://10.0.2.2:8080

  # enche a sala, senão o placar tem um jogador só
  REPO=$PWD MINUTES=40 node marketing/src/jogadores-de-teste.js

  Depois entre na sala pelo "Jogar" e confira a tela antes de gravar ou capturar. Uma tela parada
  (o app esquecido no Histórico, por exemplo) grava sem erro nenhum.

== Capturas da loja ==

  python3 marketing/src/capturar-rodada.py      # candidatas de uma rodada, três quadros por momento

  Escolha as sem dígito do cronômetro no meio da virada, copie para marketing/screenshots/ e rode
  marketing/src/limpar-barra.sh nelas. Os banners leem direto de screenshots/: depois,
  marketing/src/render.sh.

  O lobby (1) e o histórico (6) precisam de uma conta com histórico de verdade, que a sala de bots
  não produz: médias vitalícias e rodadas antigas saem ruins numa base local cheia de rodadas de bot.

== Vídeo ==

  python3 marketing/src/gravar-rodadas.py 3     # três rodadas inteiras em marketing/src/gravacoes/
  marketing/src/render-video.sh                 # os cortes, nas três proporções

FIM
