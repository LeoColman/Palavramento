#!/usr/bin/env bash
# Cuts one portrait screen recording into the three shapes a Google Ads App campaign accepts:
# 9:16, 1:1 and 16:9.
#
# The recording is 1080x2424 (Pixel 9a). The vertical and square cuts are crops around the
# board. The landscape one cannot be a crop of a portrait video, so it composites the
# recording onto backdrop-16x9.png, in the slot that banner.html's "backdrop" format leaves
# empty at exactly 428x960 from (1372, 60).
#
# Usage: render-video.sh <gravacao.mp4>
set -euo pipefail

source="${1:?uso: render-video.sh <gravacao.mp4>}"
# Google accepts 10 to 60 seconds; 30 is the length that still shows a full run of words
# without asking anyone to sit through a whole round.
start="${START:-2}"
length="${LENGTH:-30}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
out="$here/../ads/video"
mkdir -p "$out"

# Board centre on this AVD: the 4x4 runs from y=606 to y=1582.
ffmpeg -y -loglevel error -ss "$start" -t "$length" -i "$source" \
  -vf "crop=1080:1920:0:120" -c:v libx264 -preset slow -crf 20 -pix_fmt yuv420p -an \
  "$out/vertical-9x16.mp4"

ffmpeg -y -loglevel error -ss "$start" -t "$length" -i "$source" \
  -vf "crop=1080:1080:0:505" -c:v libx264 -preset slow -crf 20 -pix_fmt yuv420p -an \
  "$out/quadrado-1x1.mp4"

ffmpeg -y -loglevel error -loop 1 -i "$here/backdrop-16x9.png" -ss "$start" -t "$length" -i "$source" \
  -filter_complex "[1:v]scale=428:-2[app];[0:v][app]overlay=1372:60:shortest=1[v]" \
  -map "[v]" -c:v libx264 -preset slow -crf 20 -pix_fmt yuv420p -an \
  "$out/paisagem-16x9.mp4"

for file in "$out"/*.mp4; do
  printf '%-24s %s\n' "$(basename "$file")" \
    "$(ffprobe -v error -select_streams v:0 -show_entries stream=width,height \
        -show_entries format=duration -of csv=p=0:s=x "$file" | tr '\n' ' ')"
done
