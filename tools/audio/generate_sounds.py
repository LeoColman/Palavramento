#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 Leonardo Colman Lopes
"""Generates Palavramento's game audio (docs/adr/0011-audio.md).

Everything here is original and synthesized with the Python standard library only (wave, struct,
math, random with a fixed seed): no third-party samples, no external audio libraries, so there is
nothing to license-audit for these files (see LICENSES.md).

Produces:
  - bg_music: a short, seamlessly looping background track (a pentatonic arpeggio over a simple
    bass line) that plays during a round.
  - sfx_accepted: bright, rising - a new word was accepted.
  - sfx_rejected: low, short - the submitted path was not a valid word.
  - sfx_already_found: neutral, softer - a word the player had already found this round.

Writes 16-bit mono WAV first, then, if `ffmpeg` is on PATH, encodes each to OGG Vorbis into
app/src/main/res/raw/ (smaller, and Android's MediaPlayer/SoundPool both play OGG natively) and
removes the WAV; otherwise the WAV itself is what ends up in app/src/main/res/raw/ (still 16-bit
mono, a modest sample rate, task brief: "ship 16-bit mono WAV at a modest sample rate" as the
fallback). Either way, every asset stays well under the ~1MB budget.

Run: python3 tools/audio/generate_sounds.py
"""
from __future__ import annotations

import math
import random
import shutil
import struct
import subprocess
import wave
from pathlib import Path

SAMPLE_RATE = 22_050
SEED = 20260915  # the day this feature was requested (task brief), fixed for reproducibility

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
RAW_DIR = REPO_ROOT / "app" / "src" / "main" / "res" / "raw"

# Equal-temperament semitone offsets from middle C (C4 = 261.626 Hz), the pentatonic scale used
# throughout (C major pentatonic: C D E G A), across three octaves.
C3, D3, E3, G3, A3 = -12, -10, -8, -5, -3
C4, D4, E4, G4, A4 = 0, 2, 4, 7, 9
C5, D5, E5, G5, A5 = 12, 14, 16, 19, 21
C6 = 24


def note_hz(semitones_from_c4: float) -> float:
  return 261.626 * (2.0 ** (semitones_from_c4 / 12.0))


def envelope(index: int, slot_len: int, attack_len: int, sustain_len: int, decay_rate: float) -> float:
  """Attack ramp, exponential decay, then silence for the rest of the slot (rhythmic separation,
  and a guarantee that every slot starts and ends at ~0 amplitude, which is what makes the whole
  loop buffer click-free at the wrap-around point)."""
  if index < attack_len:
    return index / max(1, attack_len)
  if index < sustain_len:
    progress = (index - attack_len) / max(1, sustain_len - attack_len)
    return math.exp(-decay_rate * progress)
  return 0.0


def synth_note(freq_hz: float, slot_len: int, volume: float, brightness: float = 0.0) -> list[float]:
  """One note as float samples in [-1, 1]. `brightness` mixes in a bit of the 2nd harmonic for a
  more chiptune-like, bell-ish timbre (used by the melody layer, not the bass)."""
  attack_len = max(1, int(slot_len * 0.02))
  sustain_len = int(slot_len * 0.85)
  samples = []
  for i in range(slot_len):
    t = i / SAMPLE_RATE
    tone = math.sin(2 * math.pi * freq_hz * t)
    if brightness > 0:
      tone = (1 - brightness) * tone + brightness * math.sin(2 * math.pi * freq_hz * 2 * t)
    samples.append(tone * envelope(i, slot_len, attack_len, sustain_len, decay_rate=4.5) * volume)
  return samples


def layer(total_samples: int, offsets: list[float], volume: float, brightness: float = 0.0) -> list[float]:
  """Lays `offsets` (semitone offsets, one note each) evenly across `total_samples`, using exact
  sample-index boundaries (not fixed float durations) so the layer always sums to exactly
  `total_samples`, matching every other layer sample for sample."""
  out: list[float] = []
  boundaries = [round(i * total_samples / len(offsets)) for i in range(len(offsets) + 1)]
  for note_index, semitones in enumerate(offsets):
    slot_len = boundaries[note_index + 1] - boundaries[note_index]
    out.extend(synth_note(note_hz(semitones), slot_len, volume, brightness))
  return out


def mix(*layers: list[float]) -> list[float]:
  length = len(layers[0])
  for other in layers:
    assert len(other) == length, "layers must be the exact same length to tile seamlessly"
  return [sum(values) for values in zip(*layers)]


def normalize(samples: list[float], peak: float = 0.9) -> list[float]:
  current_peak = max(abs(s) for s in samples) or 1.0
  scale = peak / current_peak
  return [s * scale for s in samples]


def to_int16(samples: list[float]) -> bytes:
  return struct.pack("<%dh" % len(samples), *(max(-32768, min(32767, round(s * 32767))) for s in samples))


def write_wav(path: Path, samples: list[float]) -> None:
  with wave.open(str(path), "wb") as wav_file:
    wav_file.setnchannels(1)
    wav_file.setsampwidth(2)
    wav_file.setframerate(SAMPLE_RATE)
    wav_file.writeframes(to_int16(samples))


def generate_music() -> list[float]:
  """A short, seamless loop: a bright pentatonic arpeggio over a simple bass line (task brief: "a
  short pentatonic arpeggio with a bass line, a few seconds, seamless loop point")."""
  bpm = 132
  samples_per_beat = int(SAMPLE_RATE * 60.0 / bpm)
  total_samples = samples_per_beat * 8  # two 4/4 bars

  # 16 eighth notes: a rise to C6 and back down to C5, landing where the next loop iteration
  # begins - a melodic seam, not just a technical one.
  melody = [C5, E5, G5, E5, D5, G5, A5, G5, C5, E5, G5, C6, A5, G5, E5, C5]
  # 4 quarter-of-the-loop bass notes (2 beats each): a simple I-then-V-ish pentatonic line.
  bass = [C3, C3, G3, A3]

  melody_layer = layer(total_samples, melody, volume=0.28, brightness=0.25)
  bass_layer = layer(total_samples, bass, volume=0.32)
  return normalize(mix(melody_layer, bass_layer))


def generate_accepted() -> list[float]:
  """Bright, rising: a quick three-note ascent (task brief: "one for an accepted word (bright, rising)")."""
  notes = [C5, E5, G5]
  slot_len = int(SAMPLE_RATE * 0.09)
  samples: list[float] = []
  for semitones in notes:
    samples.extend(synth_note(note_hz(semitones), slot_len, volume=0.6, brightness=0.35))
  return normalize(samples)


def generate_rejected() -> list[float]:
  """Low, short: a brief low thud with a touch of noise for texture (task brief: "one for a
  rejected word (low, short)")."""
  random.seed(SEED)
  duration_s = 0.16
  slot_len = int(SAMPLE_RATE * duration_s)
  attack_len = max(1, int(slot_len * 0.02))
  sustain_len = int(slot_len * 0.8)
  freq_hz = note_hz(C3 - 5)  # a fifth below C3
  samples = []
  for i in range(slot_len):
    t = i / SAMPLE_RATE
    tone = math.sin(2 * math.pi * freq_hz * t)
    noise = random.uniform(-1.0, 1.0)
    value = 0.85 * tone + 0.15 * noise
    samples.append(value * envelope(i, slot_len, attack_len, sustain_len, decay_rate=5.5) * 0.6)
  return normalize(samples)


def generate_already_found() -> list[float]:
  """Neutral, softer: a single soft mid-range blip, no sweep, quieter than the other two (task
  brief: "neutral, softer; it matches the new yellow flash")."""
  duration_s = 0.18
  slot_len = int(SAMPLE_RATE * duration_s)
  attack_len = max(1, int(slot_len * 0.15))  # gentler attack than the percussive effects
  sustain_len = int(slot_len * 0.9)
  freq_hz = note_hz(E4)
  samples = []
  for i in range(slot_len):
    t = i / SAMPLE_RATE
    tone = math.sin(2 * math.pi * freq_hz * t)
    samples.append(tone * envelope(i, slot_len, attack_len, sustain_len, decay_rate=3.0) * 0.35)
  return normalize(samples, peak=0.5)


def encode_or_copy(wav_path: Path, resource_name: str) -> Path:
  RAW_DIR.mkdir(parents=True, exist_ok=True)
  ffmpeg = shutil.which("ffmpeg")
  if ffmpeg is None:
    destination = RAW_DIR / f"{resource_name}.wav"
    shutil.copyfile(wav_path, destination)
    return destination

  destination = RAW_DIR / f"{resource_name}.ogg"
  subprocess.run(
    [ffmpeg, "-y", "-loglevel", "error", "-i", str(wav_path), "-c:a", "libvorbis", "-qscale:a", "2", str(destination)],
    check=True,
  )
  return destination


def main() -> None:
  random.seed(SEED)
  tracks = {
    "bg_music": generate_music(),
    "sfx_accepted": generate_accepted(),
    "sfx_rejected": generate_rejected(),
    "sfx_already_found": generate_already_found(),
  }

  scratch_dir = Path(__file__).resolve().parent / "_build"
  scratch_dir.mkdir(exist_ok=True)
  try:
    total_bytes = 0
    for name, samples in tracks.items():
      wav_path = scratch_dir / f"{name}.wav"
      write_wav(wav_path, samples)
      destination = encode_or_copy(wav_path, name)
      total_bytes += destination.stat().st_size
      print(f"{destination.relative_to(REPO_ROOT)}: {destination.stat().st_size} bytes")
    print(f"total: {total_bytes} bytes")
  finally:
    shutil.rmtree(scratch_dir, ignore_errors=True)


if __name__ == "__main__":
  main()
