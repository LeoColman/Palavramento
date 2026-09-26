#!/usr/bin/env python3
"""Pulls real rounds out of the local server's database into rodadas.json, for the HTML5 ads.

The playable ads replay rounds the server actually generated: the board as the generator made it,
tile values with the theme already applied, and the whole solution the solver computed. The ad
then accepts and scores words exactly as the game does, because it is the game's own data. One
round per theme below, the one with the most common words, so whoever plays the ad finds something.

Needs the local Postgres with rounds in it (any session of capturar.sh leaves plenty):

    docker compose up -d postgres
    python3 marketing/src/html5/extrair-rodadas.py
"""
import json
import os
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, '..', '..', '..'))

THEMES = [
    'Grade padrão',
    'Dígrafos',
    'Uma ou outra: A/S',
    'Uma ou outra: O/R',
    'R de alto valor',
    'T de alto valor',
    'E de alto valor',
    'O nos cantos',
    'S nos cantos',
    'Uma ou outra: E/S',
]


def sql(query):
    result = subprocess.run(['docker', 'compose', 'exec', '-T', 'postgres', 'psql', '-U', 'palavramento', '-d',
                             'palavramento', '-At', '-c', query], capture_output=True, text=True, cwd=REPO, check=True)
    return result.stdout.strip()


def quote(text):
    return "'" + text.replace("'", "''") + "'"


def main():
    rounds = []
    for theme in THEMES:
        best = sql(f"""
            select r.id from rounds r join round_words w on w.round_id = r.id
            where r.theme_title = {quote(theme)} and r.status = 'FINISHED' and w.tier = 'Common'
            group by r.id order by count(*) desc, r.id limit 1""")
        if not best:
            raise SystemExit(f'nenhuma rodada com o tema {theme}')
        row = json.loads(sql(f"""
            select json_build_object('theme', theme_title, 'subtitle', theme_subtitle,
                                     'board', board_json::json, 'maxScore', max_score, 'maxWords', max_words)
            from rounds where id = {quote(best)}"""))
        words = json.loads(sql(f"""
            select coalesce(json_agg(json_build_array(normalized, word, score, tier, path_json::json)
                                     order by score desc, normalized), '[]'::json)
            from round_words where round_id = {quote(best)}"""))
        row['words'] = words
        rounds.append(row)
        common = sum(1 for w in words if w[3] == 'Common')
        print(f'{theme:20} {len(words):4} palavras, {common:3} comuns')

    with open(os.path.join(HERE, 'rodadas.json'), 'w') as f:
        json.dump(rounds, f, ensure_ascii=False, separators=(',', ':'))
        f.write('\n')


if __name__ == '__main__':
    main()
