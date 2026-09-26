#!/usr/bin/env bash
# Cuts the round recordings in gravacoes/ into the video assets of the Google Ads App campaign:
# every cut below, in the three shapes the campaign accepts (9:16, 1:1, 16:9). Google takes up to
# 20 videos per ad group, 10 to 60 seconds each, and learns which ones work, so the cuts differ in
# what they show, not only in size.
#
# The recordings are whole rounds on the Pixel 9a AVD (1080x2424), started as the round starts,
# against a local server with the real 120 s round and 25 s intermission. The real length matters:
# with longer rounds for comfort the countdown shows 02:53, and an ad that says "2 minutos" must
# not show a clock the game can never reach.
#
# The vertical and square cuts are crops around the board. The landscape one cannot be a crop of a
# portrait video, so it composites the recording onto backdrop-16x9.png, in the slot banner.html's
# "backdrop" format leaves empty at exactly 428x960 from (1372, 60). A cut with a card opens on
# banner.html's title card for 2.5 s before the game.
#
# Usage: render-video.sh [chrome-binary]
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
out="$here/../ads/video"
chrome="${1:-google-chrome}"
cards="$(mktemp -d)"
trap 'rm -rf "$cards"' EXIT
mkdir -p "$out"

# name:recording:start:length:card. Times in seconds from the start of the recording, which is
# the start of the round (within a second or two): the round ends at about 120, the leaderboard
# takes over at about 132 and the next round starts at about 145.
#   rodada-1  Dígrafos, rodada-2  I de alto valor (the account ends first), rodada-3  A nos cantos
cuts=(
  "partida-1:rodada-1.mp4:3:30:"
  "partida-2:rodada-2.mp4:3:30:"
  "partida-3:rodada-3.mp4:3:30:"
  "fim-de-rodada:rodada-2.mp4:112:30:"
  "mesma-grade-15s:rodada-3.mp4:45:12.5:mesma-grade"
  "temas-15s:rodada-1.mp4:45:12.5:temas"
)

card_seconds=2.5
encode=(-c:v libx264 -preset slow -crf 20 -pix_fmt yuv420p -r 30 -an -movflags +faststart)

# One title card per shape, rendered at the exact video size.
render_card() {
  local id="$1" shape="$2" width="$3" height="$4"
  "$chrome" --headless --disable-gpu --no-sandbox --hide-scrollbars --force-device-scale-factor=1 \
    --virtual-time-budget=4000 --window-size="$width,$height" \
    --screenshot="$cards/$id-$shape.png" "file://$here/banner.html?card=$id&r=$shape" 2>/dev/null
}

# The game part of each shape, as an ffmpeg filter graph. Inputs are always in the same order:
# 0 is the recording, 1 is the 16:9 backdrop (landscape only), and a title card comes last.
vertical_game="[0:v]crop=1080:1920:0:120,fps=30,setsar=1,format=yuv420p[game]"
square_game="[0:v]crop=1080:1080:0:505,fps=30,setsar=1,format=yuv420p[game]"
wide_game="[0:v]scale=428:-2,fps=30[app];[1:v][app]overlay=1372:60:shortest=1,setsar=1,format=yuv420p[game]"

cut() {
  local source="$1" start="$2" length="$3" card="$4" shape="$5" size="$6" graph="$7" file="$8"
  local inputs=(-ss "$start" -t "$length" -i "$here/gravacoes/$source")
  local card_input=1 last="[game]"
  if [[ "$shape" == "w" ]]; then
    inputs+=(-loop 1 -i "$here/backdrop-16x9.png")
    card_input=2
  fi
  if [[ -n "$card" ]]; then
    inputs+=(-loop 1 -t "$card_seconds" -i "$cards/$card-$shape.png")
    graph+=";[$card_input:v]scale=$size,fps=30,setsar=1,format=yuv420p[card];[card][game]concat=n=2:v=1:a=0[v]"
    last="[v]"
  fi
  ffmpeg -y -loglevel error "${inputs[@]}" -filter_complex "$graph" -map "$last" "${encode[@]}" "$out/$file"
}

for spec in "${cuts[@]}"; do
  IFS=: read -r name source start length card <<< "$spec"
  if [[ -n "$card" ]]; then
    render_card "$card" v 1080 1920
    render_card "$card" s 1080 1080
    render_card "$card" w 1920 1080
  fi
  cut "$source" "$start" "$length" "$card" v 1080:1920 "$vertical_game" "vertical-$name.mp4"
  cut "$source" "$start" "$length" "$card" s 1080:1080 "$square_game" "quadrado-$name.mp4"
  cut "$source" "$start" "$length" "$card" w 1920:1080 "$wide_game" "paisagem-$name.mp4"
done

for file in "$out"/*.mp4; do
  printf '%-34s %s\n' "$(basename "$file")" \
    "$(ffprobe -v error -select_streams v:0 -show_entries stream=width,height \
        -show_entries format=duration -of csv=p=0:s=x "$file" | tr '\n' ' ')"
done
