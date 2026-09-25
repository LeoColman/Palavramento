#!/usr/bin/env bash
# Traces words on the emulator's 4x4 board, for screenshots and screen recordings.
#
# Reads one path per line on stdin, each a list of tile indices (index = row * 4 + col),
# which is exactly the shape of `round_words.path_json` in the server's database:
#
#   psql ... -At -c "select replace(replace(replace(path_json,'[',''),']',''),',',' ')
#                    from round_words where round_id='...' order by score desc limit 10" \
#     | marketing/src/tracar.sh
#
# Two details make the difference between this working and silently doing nothing:
#
#   1. Every leg is split into small steps. `input motionevent MOVE` delivers only the point
#      you name, so jumping straight from one tile centre to the next lets the gesture
#      detector miss the tiles in between.
#   2. The whole run goes to the device as one script and executes there. Driving it from the
#      host means an `adb` round trip per event, and under `screenrecord` the emulator falls far
#      enough behind that every gesture after the first is dropped. Same gestures, run on the
#      device: all of them land.
#   3. A still of a word being traced has to be taken from inside the gesture, not raced against
#      it from the host. With CAPTURE set, the device takes the screenshot itself at the one
#      moment the path is complete and the finger is still down. Timing a `screencap` by hand is
#      how the marketing shots ended up with half-drawn words.
#
# Coordinates are for the Pixel 9a AVD at 1080x2424. Re-measure if the AVD changes.
set -euo pipefail

XS=(168 415 664 909)
YS=(719 970 1219 1467)
STEPS="${STEPS:-6}"
DELAY="${DELAY:-0.05}"     # between pointer events, so the drag reads as a drag
# Between words. The accepted-word feedback swallows input for ~2.5s, and its tile flash runs on
# past that: at 3s the next word started over the previous one's animation, which is how two words
# ended up on top of each other in a still. Stills are not in a hurry.
PAUSE="${PAUSE:-6}"
SETTLE="${SETTLE:-1.2}"    # after the last tile: its highlight animates in, and a capture mid
                           # animation catches half-drawn glyphs
AFTER="${AFTER:-0.5}"      # after lifting: long enough for the accepted-word flash to be at full
HOLD="${HOLD:-0}"          # seconds to keep the finger down once the path is complete
CAPTURE="${CAPTURE:-}"     # when set, the device captures each completed path to /sdcard/$CAPTURE-N.png
REMOTE=/data/local/tmp/palavramento-tracar.sh

script="#!/system/bin/sh"$'\n'
word=0
while read -r -a path; do
  [[ ${#path[@]} -eq 0 ]] && continue
  prev_x=""; prev_y=""
  for index in "${path[@]}"; do
    x=${XS[$((index % 4))]}; y=${YS[$((index / 4))]}
    if [[ -z "$prev_x" ]]; then
      script+="input motionevent DOWN $x $y; sleep $DELAY"$'\n'
    else
      for step in $(seq 1 "$STEPS"); do
        ix=$(( prev_x + (x - prev_x) * step / STEPS ))
        iy=$(( prev_y + (y - prev_y) * step / STEPS ))
        script+="input motionevent MOVE $ix $iy; sleep $DELAY"$'\n'
      done
    fi
    prev_x=$x; prev_y=$y
  done
  # The path is drawn and the finger is still down: this is the frame worth photographing.
  word=$((word + 1))
  script+="sleep $SETTLE"$'\n'
  [[ -n "$CAPTURE" ]] && script+="screencap -p /sdcard/$CAPTURE-$word-tracando.png"$'\n'
  [[ "$HOLD" != "0" ]] && script+="sleep $HOLD"$'\n'
  script+="input motionevent UP $prev_x $prev_y"$'\n'
  # The other frame worth having: the word is in, its tiles are flashing and the score has moved.
  if [[ -n "$CAPTURE" ]]; then
    script+="sleep $AFTER"$'\n'
    script+="screencap -p /sdcard/$CAPTURE-$word-aceita.png"$'\n'
  fi
  script+="sleep $PAUSE"$'\n'
done

printf '%s' "$script" | adb shell "cat > $REMOTE"
adb shell "chmod 755 $REMOTE && sh $REMOTE"

if [[ -n "$CAPTURE" ]]; then
  mkdir -p "${CAPTURE_DIR:-.}"
  for remote in $(adb shell "ls /sdcard/$CAPTURE-*.png" | tr -d '\r'); do
    adb pull "$remote" "${CAPTURE_DIR:-.}/$(basename "$remote")" > /dev/null
    adb shell "rm -f $remote"
  done
  echo "capturas em ${CAPTURE_DIR:-.}/$CAPTURE-*.png"
fi
