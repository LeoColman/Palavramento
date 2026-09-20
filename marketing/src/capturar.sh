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
  ROUND_DURATION_SECONDS=240 INTERMISSION_DURATION_SECONDS=45 \
    server/build/install/server/bin/server

  ./gradlew :app:installDebug -Ppalavramento.serverUrl=http://10.0.2.2:8080

  # enche a sala, senão o placar tem um jogador só
  REPO=$PWD MINUTES=30 node marketing/src/jogadores-de-teste.js

  # para a tela de lobby com estatísticas: rode antes com rodada curta
  # (ROUND_DURATION_SECONDS=25) e HERO/HERO_EMAIL/HERO_PASSWORD definidos, por uns 10 minutos,
  # e depois entre com essa conta no app.

== Capturas ==

  Coordenadas das peças no Pixel 9a (1080x2424): x = 168 415 664 909, y = 719 970 1219 1467.
  Índice da peça = linha * 4 + coluna.

  1. Lobby            logado, com estatísticas preenchidas
  2. Traçando         marketing/src/tracar.sh e uma captura no meio do gesto
  3. Partida          depois de umas 10 palavras aceitas, com peças verdes
  4. Resultados       assim que a rodada acaba
  5. Placar           aba "Placar" da mesma tela
  6. Histórico        link "Histórico" no lobby

  adb exec-out screencap -p > marketing/screenshots/N-nome.png
  marketing/src/limpar-barra.sh marketing/screenshots/*.png

== Vídeo ==

  RID=$(docker compose exec -T postgres psql -U palavramento -d palavramento -At \
          -c "select id from rounds where status='ACTIVE'")
  docker compose exec -T postgres psql -U palavramento -d palavramento -At -F' ' \
    -c "select replace(replace(replace(path_json,'[',''),']',''),',',' ')
        from round_words where round_id='$RID'
         and json_array_length(path_json::json) between 4 and 7
        order by score desc limit 11" > /tmp/caminhos.txt

  adb shell "rm -f /sdcard/pala.mp4; \
    (screenrecord --size 1080x2424 --bit-rate 12000000 --time-limit 58 /sdcard/pala.mp4 &)"
  marketing/src/tracar.sh < /tmp/caminhos.txt
  adb pull /sdcard/pala.mp4 marketing/src/gravacao-bruta.mp4

  marketing/src/render-video.sh marketing/src/gravacao-bruta.mp4

FIM
