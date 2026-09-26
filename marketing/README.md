# Media kit

Material para anunciar o Palavramento no Google Ads, na forma que o Google pede: textos dentro do
limite de caractere, imagens no pixel exato, vídeo nas três proporções.

Nada aqui entra no build. O Gradle não vê esta pasta, e ela não conta para o limite de três
módulos.

## Onde está o quê

| Arquivo | O que é |
|---|---|
| [`google-ads.md`](google-ads.md) | A campanha: estrutura, lance, orçamento, e os textos dos três grupos de anúncios |
| [`play-store.md`](play-store.md) | A ficha da Play Store, que é o que a campanha de App usa para segmentar |
| [`ads/`](ads/) | As 20 imagens prontas para subir, nas três proporções que o Google aceita |
| [`ads/video/`](ads/video/) | Os 18 vídeos: seis cortes em três proporções (falta subir ao YouTube) |
| [`screenshots/`](screenshots/) | Capturas do app, para a ficha da loja e para os banners |
| [`src/`](src/) | O que gerou tudo isso |

## O que já está pronto e o que falta

Pronto: os textos, as 20 imagens e 18 vídeos (a campanha aceita até 20 de cada), o ícone e a
imagem de destaque da loja, e as seis capturas de tela.

O app está publicado na Google Play desde 2026-09-25
(`br.com.colman.palavramento`), que era o que impedia criar a campanha.

Falta, e nada disso dá para fazer do repositório:

1. **Vincular a conta do Google Ads à Play Console**, senão a campanha não recebe a conversão de
   instalação e otimiza no escuro.
2. **Subir os três vídeos ao YouTube** (não listado serve) e colar as URLs na campanha.

## Regerar

```bash
node marketing/src/check-copy.js                           # limites de caractere dos textos
marketing/src/render.sh                                    # imagens
marketing/src/render-video.sh                              # vídeos, das gravações em src/gravacoes/
```

As imagens saem de [`src/banner.html`](src/banner.html), renderizado pelo Chrome headless no
tamanho final, sem reamostragem. As cores vêm da paleta do app
(`app/src/main/kotlin/br/com/colman/palavramento/ui/theme/PalavramentoColors.kt`), e o ícone é o
mesmo desenho do `ic_launcher`, então anúncio, ícone e tela são o mesmo produto.

## Refazer as capturas e os vídeos

Precisa do emulador, do servidor local **com a duração de rodada padrão** e da sala cheia.
[`src/capturar.sh`](src/capturar.sh) prepara a barra de status em modo demo, abre o app e imprime o
resto da receita. A duração importa: o servidor local já foi usado com rodada de 240 s por conforto,
e as capturas e o vídeo saíram com 03:49 e 02:53 no cronômetro, que o jogo nunca mostra, num
material que promete "2 minutos".

- [`src/capturar-rodada.py`](src/capturar-rodada.py) tira as candidatas das capturas da loja numa
  rodada, três quadros por momento, para escolher um sem dígito do cronômetro no meio da virada.
- [`src/gravar-rodadas.py`](src/gravar-rodadas.py) grava rodadas inteiras em
  [`src/gravacoes/`](src/gravacoes/), de onde [`src/render-video.sh`](src/render-video.sh) corta os
  vídeos.

E as duas ferramentas que valem por si:

- [`src/jogadores-de-teste.js`](src/jogadores-de-teste.js) enche a sala com oito jogadores que
  jogam de verdade, cada um com uma habilidade diferente, para o placar não ser um jogador só.
  Com `HERO` definido, uma conta nomeada joga junto e acumula o histórico que a tela de lobby
  mostra.
- [`src/tracar.sh`](src/tracar.sh) traça palavras no tabuleiro via adb. Lê caminhos de peças da
  entrada padrão, no mesmo formato do `round_words.path_json` do servidor.

## Cuidado com a marca

O dossiê descreve o Palavramento como clone de Wordament. Isso é vocabulário interno: **Wordament**
é marca da Microsoft e **Boggle** da Hasbro, e qualquer uma delas num anúncio ou na ficha da loja
reprova o anúncio. Nenhum texto deste kit usa as duas.
