#!/usr/bin/env python3
"""Takes the candidate store screenshots over one round: traced word, match, results, leaderboard.

Same setup as gravar-rodadas.py (app in the room, local server at its default round length, the
room filled by jogadores-de-teste.js). Writes candidates to a directory, three frames per moment,
0.35 s apart: the countdown flips a digit every second and a still taken mid-flip shows it half
drawn, so the pick is made by eye from the three.

    python3 marketing/src/capturar-rodada.py [directory]   # default /tmp/palavramento-capturas

    primeira-1-tracando-*.png   the round's first word, fully traced, finger still down: the first
                                word, because the feedback line keeps showing the previous word
                                until another replaces it (2-tracando)
    jogo-N-aceita-*.png         right after each later word is accepted, tiles green (3-partida)
    resultados-*.png            a few seconds into the intermission (4-resultados)
    placar-*.png                right after the tab switches to Placar (5-placar)

Words are the round's common ones, best first: a store shot reads better with "promove" than with
an expert word. Afterwards, copy the picks to marketing/screenshots/ and run limpar-barra.sh on them.
"""
import os
import shutil
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, '..', '..'))


def sql(query):
    return subprocess.run(['docker', 'compose', 'exec', '-T', 'postgres', 'psql', '-U', 'palavramento', '-d',
                           'palavramento', '-At', '-F', '|', '-c', query],
                          capture_output=True, text=True, cwd=REPO).stdout.strip()


def log(message):
    print(time.strftime('%H:%M:%S'), message, flush=True)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else '/tmp/palavramento-capturas'
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out)

    def shots(name):
        for i in range(3):
            with open(os.path.join(out, f'{name}-{i + 1}.png'), 'wb') as f:
                f.write(subprocess.run(['adb', 'exec-out', 'screencap', '-p'], capture_output=True).stdout)
            time.sleep(0.35)

    def trace(paths, capture):
        env = dict(os.environ, CAPTURE=capture, CAPTURE_DIR=out, FRAMES='3')
        subprocess.run([os.path.join(HERE, 'tracar.sh')], input='\n'.join(paths) + '\n', text=True, env=env,
                       capture_output=True)

    log('esperando o início de uma rodada')
    while True:
        round_id = sql("select id from rounds where status='ACTIVE' and starts_at > now() - interval '3 seconds' limit 1")
        if round_id:
            break
        time.sleep(0.5)
    rows = sql("select p from (select distinct on (path_json) "
               "replace(replace(replace(path_json,'[',''),']',''),',',' ') p, score from round_words "
               f"where round_id='{round_id}' and tier='Common' and json_array_length(path_json::json) between 4 and 7 "
               "order by path_json, score desc) t order by score desc limit 9").splitlines()
    paths = [r.strip() for r in rows if r.strip() and all(c.isdigit() or c == ' ' for c in r.strip())]

    time.sleep(2)
    trace(paths[:1], 'primeira')
    log('primeira palavra')
    trace(paths[1:], 'jogo')
    log(f'mais {len(paths) - 1} palavras')

    while sql(f"select status from rounds where id='{round_id}'") != 'FINISHED':
        time.sleep(1)
    time.sleep(3)
    shots('resultados')
    # The tab switches to Placar halfway through the 25 s intermission.
    time.sleep(7.5)
    shots('placar')
    log(f'pronto: {out}')


if __name__ == '__main__':
    main()
