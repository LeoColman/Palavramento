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

# format:width:height:variant:filename
targets=(
  "square:1200:1200:a:quadrado-1200x1200-grade.png"
  "wide:1200:628:a:paisagem-1200x628-grade.png"
  "tall:1200:1500:a:retrato-1200x1500-grade.png"
  "wide:1200:628:b:paisagem-1200x628-tela.png"
  "tall:1200:1500:b:retrato-1200x1500-tela.png"
  "icon:512:512:a:play-icone-512x512.png"
  "feature:1024:500:a:play-destaque-1024x500.png"
)

for target in "${targets[@]}"; do
  IFS=: read -r format width height variant name <<< "$target"
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
    "file://$here/banner.html?f=$format&v=$variant" 2>/dev/null
  # The Play Store icon has to be a 32-bit PNG; Chrome writes an opaque 24-bit one. The alpha
  # channel it gains here is fully opaque, so nothing about the image changes.
  if [[ "$format" == "icon" ]]; then
    magick "$out/$name" -alpha on -background none -alpha set PNG32:"$out/$name"
  fi
  printf '%-40s %s\n' "$name" "$(identify -format '%wx%h %[channels] %b' "$out/$name")"
done
