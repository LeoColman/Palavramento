#!/usr/bin/env python3
"""Builds the HTML5 ads of the Google Ads App campaign into marketing/ads/html5/.

Twenty .zip files, which is as many as a campaign ad group takes: ten real rounds (rodadas.json,
one per theme) times two modes, "tres" (find three words) and "relogio" (thirty seconds). Each zip
holds a single index.html: jogavel.html with the round, the mode and motor.js written in.

    python3 marketing/src/html5/build.py

Run teste-motor.js first if motor.js or rodadas.json changed: it checks the rules against every
word the real solver found. Then run every zip through Google's validator before uploading:
https://h5validator.appspot.com/adwords/asset
"""
import json
import os
import re
import unicodedata
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'ads', 'html5'))
MODES = ['tres', 'relogio']


def slug(text):
    ascii_text = unicodedata.normalize('NFKD', text).encode('ascii', 'ignore').decode()
    return re.sub(r'[^a-z0-9]+', '-', ascii_text.lower()).strip('-')


# The word each ad traces by itself to show the gesture, chosen by hand: the best-scoring common
# word is often an archaic one ("cousas"), and a demonstration should read as ordinary Portuguese.
# Where the theme has a special tile the word goes through it, since that tile is the theme.
DEMO_WORDS = {
    'Grade padrão': 'REPOUSO',
    'Dígrafos': 'QUINHAO',         # through QU and NH
    'Uma ou outra: A/S': 'COESAO',  # through A/S
    'Uma ou outra: O/R': 'COMPARO',  # through O/R
    'R de alto valor': 'REMOS',
    'T de alto valor': 'FACAM',     # "façam": the cedilla matched by a plain C
    'E de alto valor': 'ARENOSO',
    'O nos cantos': 'ASPECTO',
    'S nos cantos': 'TEATROS',
    'Uma ou outra: E/S': 'CEDILHA',  # through E/S
}


def demo_path(round_):
    wanted = DEMO_WORDS[round_['theme']]
    for normalized, _, _, tier, path in round_['words']:
        if normalized == wanted:
            if tier != 'Common':
                raise SystemExit(f'{wanted} is not a common word in {round_["theme"]}')
            return path
    raise SystemExit(f'{wanted} is not on the {round_["theme"]} board')


def fill(template, marker, value):
    if template.count(marker) != 1:
        raise SystemExit(f'{marker} should appear exactly once in jogavel.html')
    return template.replace(marker, value)


def main():
    with open(os.path.join(HERE, 'jogavel.html'), encoding='utf-8') as f:
        template = f.read()
    with open(os.path.join(HERE, 'motor.js'), encoding='utf-8') as f:
        motor = f.read()
    with open(os.path.join(HERE, 'rodadas.json'), encoding='utf-8') as f:
        rounds = json.load(f)

    os.makedirs(OUT, exist_ok=True)
    for old in os.listdir(OUT):
        if old.endswith('.zip'):
            os.remove(os.path.join(OUT, old))

    for number, round_ in enumerate(rounds, start=1):
        data = {
            'theme': round_['theme'],
            'subtitle': round_['subtitle'],
            'board': round_['board'],
            'maxWords': round_['maxWords'],
            'words': [[w[0], w[1]] for w in round_['words']],
            'hint': demo_path(round_),
        }
        for mode in MODES:
            html = fill(template, '/*RODADA*/null', json.dumps(data, ensure_ascii=False, separators=(',', ':')))
            html = fill(html, "/*MODO*/'tres'", json.dumps(mode))
            html = fill(html, '/*MOTOR*/', motor)
            name = f'{mode}-{number:02d}-{slug(round_["theme"])}.zip'
            with zipfile.ZipFile(os.path.join(OUT, name), 'w', zipfile.ZIP_DEFLATED) as archive:
                archive.writestr('index.html', html)
            size = os.path.getsize(os.path.join(OUT, name))
            print(f'{name:40} {size // 1024:4} KB')


if __name__ == '__main__':
    main()
