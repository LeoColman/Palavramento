#!/usr/bin/env python3
"""Records whole rounds on the emulator into marketing/src/gravacoes/, the source of the ad videos.

Needs the same setup as capturar.sh: the AVD booted with the app in the room, the local server
running with its default round length (120 s round, 25 s intermission, the production values: a
longer round shows a countdown the real game never reaches), and jogadores-de-teste.js filling the
room. Check that the app is really in the room before starting: a static screen records without
complaint and looks fine until you watch it.

    python3 marketing/src/gravar-rodadas.py [rounds]    # default 3

Each round is recorded from its first second for 150 s (the round, the results and the
leaderboard), by the emulator itself at a fixed 30 fps, while tracar.sh plays 14 common words at a
human pace. Output: gravacoes/rodada-N.mp4 (H.264) and rodada-N.txt with the round's theme.
"""
import os
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(HERE, 'gravacoes')
RECORD_SECONDS = 150
WORDS = 14


def sql(query):
    return subprocess.run(['docker', 'compose', 'exec', '-T', 'postgres', 'psql', '-U', 'palavramento', '-d',
                           'palavramento', '-At', '-F', '|', '-c', query],
                          capture_output=True, text=True, cwd=REPO).stdout.strip()


def log(message):
    print(time.strftime('%H:%M:%S'), message, flush=True)


def paths_for(round_id, limit):
    """The round's common words, best first, as tile paths in tracar.sh's input format."""
    rows = sql("select p from (select distinct on (path_json) "
               "replace(replace(replace(path_json,'[',''),']',''),',',' ') p, score from round_words "
               f"where round_id='{round_id}' and tier='Common' and json_array_length(path_json::json) between 4 and 7 "
               f"order by path_json, score desc) t order by score desc limit {limit}").splitlines()
    return [r.strip() for r in rows if r.strip() and all(c.isdigit() or c == ' ' for c in r.strip())]


def wait_for_new_round(seen):
    while True:
        row = sql("select id, theme_title from rounds where status='ACTIVE' "
                  "and starts_at > now() - interval '4 seconds' limit 1")
        if row and row.split('|')[0] not in seen:
            return row.split('|', 1)
        time.sleep(0.5)


def main():
    rounds = int(sys.argv[1]) if len(sys.argv) > 1 else 3
    os.makedirs(OUT, exist_ok=True)
    seen = set()
    with tempfile.TemporaryDirectory() as raw_dir:
        for n in range(1, rounds + 1):
            log(f'rodada {n}: esperando o início de uma rodada')
            round_id, theme = wait_for_new_round(seen)
            seen.add(round_id)
            raw = os.path.join(raw_dir, f'rodada-{n}.webm')
            # Recorded by the emulator, at a fixed frame rate. Android's own screenrecord only emits a
            # frame when the screen changes, which once hid for a while that the app was sitting on a
            # static screen instead of the room.
            subprocess.run(['adb', 'emu', 'screenrecord', 'start', '--time-limit', str(RECORD_SECONDS),
                            '--fps', '30', raw], capture_output=True)
            started = time.time()
            log(f'rodada {n}: gravando, tema "{theme}"')

            time.sleep(3)
            env = dict(os.environ, PAUSE='4', SETTLE='0.3')
            subprocess.run([os.path.join(HERE, 'tracar.sh')], input='\n'.join(paths_for(round_id, WORDS)) + '\n',
                           text=True, env=env, capture_output=True)

            while time.time() < started + RECORD_SECONDS + 2:
                time.sleep(1)
            subprocess.run(['adb', 'emu', 'screenrecord', 'stop'], capture_output=True)
            time.sleep(4)
            target = os.path.join(OUT, f'rodada-{n}.mp4')
            subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', raw, '-c:v', 'libx264', '-preset', 'slow',
                            '-crf', '24', '-pix_fmt', 'yuv420p', '-r', '30', '-an', '-movflags', '+faststart', target],
                           check=True)
            with open(os.path.join(OUT, f'rodada-{n}.txt'), 'w') as f:
                f.write(theme + '\n')
            log(f'rodada {n}: {target} ({os.path.getsize(target) // 1024} KB)')


if __name__ == '__main__':
    main()
