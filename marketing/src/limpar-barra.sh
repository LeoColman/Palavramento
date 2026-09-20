#!/usr/bin/env bash
# Paints over the two icons the emulator keeps in the status bar (developer options and the
# "no SIM" shield), which demo mode does not hide and which have no business in a store
# screenshot.
#
# The fill colour is sampled from the status bar of each image, because the match and results
# screens use different backgrounds (navy vs wine) and a hardcoded colour would leave a patch.
#
# Usage: limpar-barra.sh <arquivo.png>...
set -euo pipefail

# Icon strip, measured on the 1080x2424 Pixel 9a AVD.
X=148
W=140
H=112

for file in "$@"; do
  colour="$(magick "$file" -format '%[pixel:p{620,55}]' info:)"
  magick "$file" -fill "$colour" -draw "rectangle $X,0 $((X + W)),$H" "$file"
  printf '%-32s %s\n' "$(basename "$file")" "$colour"
done
