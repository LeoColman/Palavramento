#!/usr/bin/env bash
# Renders marketing/src/banner.html into the Google Ads image assets in marketing/ads/.
#
# Headless Chrome is pointed at the exact window size Google asks for, so the PNG comes
# out at native resolution and never goes through a resampling step.
#
# Usage: marketing/src/render.sh [chrome-binary]
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
out="$here/../ads"
chrome="${1:-google-chrome}"
mkdir -p "$out"

# query:width:height:filename. The ad images are the CREATIVES of banner.html, 20 of them, which is
# as many as a Google Ads App campaign takes across its three ratios.
targets=(
  "c=wide-dois-minutos-grade:1200:628:paisagem-01-dois-minutos-grade.png"
  "c=wide-dois-minutos-partida:1200:628:paisagem-02-dois-minutos-partida.png"
  "c=wide-placar:1200:628:paisagem-03-placar.png"
  "c=wide-temas:1200:628:paisagem-04-temas.png"
  "c=wide-gratis:1200:628:paisagem-05-gratis.png"
  "c=wide-quantas-partida:1200:628:paisagem-06-quantas-partida.png"
  "c=wide-que-eles-temas:1200:628:paisagem-07-que-eles-temas.png"
  "c=square-mesma-grade:1200:1200:quadrado-01-mesma-grade.png"
  "c=square-placar:1200:1200:quadrado-02-placar.png"
  "c=square-temas:1200:1200:quadrado-03-temas.png"
  "c=square-que-eles-partida:1200:1200:quadrado-04-que-eles-partida.png"
  "c=square-gratis:1200:1200:quadrado-05-gratis.png"
  "c=square-portugues:1200:1200:quadrado-06-portugues.png"
  "c=square-resultados:1200:1200:quadrado-07-resultados.png"
  "c=tall-que-eles-grade:1200:1500:retrato-01-que-eles-grade.png"
  "c=tall-que-eles-partida:1200:1500:retrato-02-que-eles-partida.png"
  "c=tall-placar:1200:1500:retrato-03-placar.png"
  "c=tall-resultados:1200:1500:retrato-04-resultados.png"
  "c=tall-temas:1200:1500:retrato-05-temas.png"
  "c=tall-gratis:1200:1500:retrato-06-gratis.png"
  "f=icon:512:512:play-icone-512x512.png"
  "f=feature:1024:500:play-destaque-1024x500.png"
)

for target in "${targets[@]}"; do
  IFS=: read -r query width height name <<< "$target"
  "$chrome" \
    --headless \
    --disable-gpu \
    --no-sandbox \
    --hide-scrollbars \
    --force-device-scale-factor=1 \
    --default-background-color=00000000 \
    --virtual-time-budget=4000 \
    --window-size="$width,$height" \
    --screenshot="$out/$name" \
    "file://$here/banner.html?$query" 2>/dev/null
  # The Play Store icon has to be a 32-bit PNG; Chrome writes an opaque 24-bit one. The alpha
  # channel it gains here is fully opaque, so nothing about the image changes.
  if [[ "$query" == "f=icon" ]]; then
    magick "$out/$name" -alpha on -background none -alpha set PNG32:"$out/$name"
  fi
  printf '%-40s %s\n' "$name" "$(identify -format '%wx%h %[channels] %b' "$out/$name")"
done
