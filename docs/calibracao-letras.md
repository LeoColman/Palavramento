# Calibração de letras (fase 1)

O dossiê (§1.3) propõe uma tabela de valores por letra como ponto de partida e pede que ela seja
calibrada contra a frequência do próprio léxico na fase 1. Este documento traz essa calibração:
frequência de letras sobre o léxico compilado (`docs/adr/0004-artefato-do-lexico.md`), uma tabela de
valores pronta para colar e pesos propostos para a geração de grade (dossiê §3.1).

Este documento não cria nenhum recurso de código: a fase 2 (dono do gerador e do modelo de tile) é
quem decide onde e como versionar a tabela final escolhida.

## Como recalcular

```bash
./gradlew :server:letterFrequencyReport
```

Roda o expansor Hunspell completo (o mesmo pipeline de `compileLexicon`) e imprime a tabela de
frequência em Markdown no stdout. Não é cacheado nem faz parte de `check`: é um relatório para ler,
não um artefato de build. Leva a mesma vintena de segundos que `compileLexicon` leva, porque refaz a
mesma expansão.

## Duas frequências, dois usos

- **Ocorrências por forma** ("% formas"): cada entrada normalizada do léxico (depois do colapso de
  formas como `pais`/`país`, dossiê §1.6) conta 1 vez por letra que contém, independente de ser comum
  ou raríssima. Isso mede quantas *palavras jogáveis* usam a letra: é a pergunta certa para o **valor
  em pontos** da letra (lógica clássica de Scrabble: quanto menos palavras dá para formar com ela,
  mais vale).
- **Ponderado por frequência de corpus** ("% ponderado"): as mesmas ocorrências, mas cada entrada
  ranqueada (dossiê §2.3) é multiplicada pela contagem bruta dela em `pt_br_50k.txt` (não pelo rank).
  Entradas especialistas (fora da lista) não entram aqui. Isso aproxima quantas vezes a letra
  apareceria de fato numa partida: é a pergunta certa para o **peso de sorteio** de letras na geração
  de grade (dossiê §3.1: "ponderada pela frequência de letras em pt-BR").

## Frequências medidas

Léxico compilado em 2026-09-14 (ver ADR 0004 para os números de forms/entradas/nós):

| Letra | Ocorrencias (formas) | % formas | Ocorrencias (ponderado por frequencia) | % ponderado |
|---|---|---|---|---|
| A | 4014487 | 15.179% | 212047175 | 13.458% |
| E | 2691773 | 10.178% | 197197873 | 12.515% |
| S | 2476509 | 9.364% | 122965199 | 7.804% |
| I | 2428811 | 9.184% | 97141315 | 6.165% |
| O | 2301502 | 8.702% | 175746532 | 11.154% |
| R | 2087111 | 7.892% | 107224875 | 6.805% |
| N | 1472877 | 5.569% | 78673751 | 4.993% |
| T | 1191675 | 4.506% | 74376966 | 4.720% |
| D | 1168896 | 4.420% | 55723616 | 3.537% |
| M | 1134315 | 4.289% | 77685465 | 4.930% |
| C | 1105869 | 4.181% | 65938769 | 4.185% |
| L | 840161 | 3.177% | 41432667 | 2.630% |
| U | 678357 | 2.565% | 73893759 | 4.690% |
| P | 515435 | 1.949% | 43874895 | 2.785% |
| H | 414932 | 1.569% | 19139352 | 1.215% |
| B | 377808 | 1.429% | 17597547 | 1.117% |
| G | 370744 | 1.402% | 19983007 | 1.268% |
| V | 315281 | 1.192% | 34294269 | 2.176% |
| F | 304980 | 1.153% | 17817918 | 1.131% |
| Z | 303311 | 1.147% | 7704907 | 0.489% |
| J | 90637 | 0.343% | 4212386 | 0.267% |
| Q | 90056 | 0.341% | 27546164 | 1.748% |
| X | 70384 | 0.266% | 3217960 | 0.204% |
| Y | 471 | 0.002% | 98644 | 0.006% |
| K | 238 | 0.001% | 61381 | 0.004% |
| W | 189 | 0.001% | 71385 | 0.005% |

## Tabela de valores proposta (pronta para colar)

Mesmo espírito da tabela do dossiê §1.3 (valores 1 a 10, letra mais rara vale mais), mas com uma
única faixa por letra (a do dossiê repetia O nas faixas 1 e 2, e C/T/L nas faixas 3 e 4, marcadas lá
como aproximação a calibrar). Faixas cortadas pela distribuição real de "% formas" acima:

| Valor | Letras |
|---|---|
| 1 | A, E, I, O, S |
| 2 | C, D, M, N, R, T |
| 3 | L, U |
| 4 | H, P |
| 5 | B, F, G, V |
| 6 | Z |
| 8 | J, Q, X |
| 10 | K, W, Y |

Notas sobre os pontos menos óbvios:

- **I entra na faixa 1** (9,18% das formas), não na 2 como no rascunho do dossiê: é a quarta letra
  mais comum do léxico, mais frequente que R.
- **Z sobe sozinho para a faixa 6** em vez de ficar com B/F/G/V (faixa 5), apesar de "% formas" dele
  (1,147%) ser quase idêntico ao de F (1,153%): o "% ponderado" de Z é 0,489%, bem abaixo do de
  B/F/G/V (1,1% a 2,2%), ou seja, aparece em palavras bem menos usadas na prática apesar de existir em
  formas dicionarizadas numa quantidade parecida. Essa é uma decisão de julgamento; dá para nivelar Z
  com a faixa 5 se preferirem uma régua mais simples.
- **Q soma com J/X na faixa 8**: sozinho pelo "% formas" pareceria mais raro (Q é sempre seguido de
  U em português), mas em uso real ("% ponderado" 1,748%) é bem mais comum que J/X porque aparece em
  palavras frequentes ("que", "quando", "quem"). Mantido na faixa 8 porque como *dicionário* (base do
  valor de pontos) ele é raro; o efeito de uso real já está capturado nos pesos de sorteio abaixo, não
  no valor da letra.
- **K, W, Y** são praticamente inexistentes no léxico nativo (nomes próprios e estrangeirismos foram
  excluídos pelo filtro de maiúsculas, dossiê §2.2): ficam na faixa máxima, confirmando o placeholder
  do dossiê.

## Pesos de sorteio propostos para a geração de grade

Dossiê §3.1 pede a distribuição de letras do sorteio ponderada pela frequência de letras em pt-BR, não
uniforme. Proposta: usar a coluna "% ponderado" acima diretamente como peso relativo de cada letra
(por exemplo, `A` sorteada com peso `13.458`, `K` com peso `0.004`). Como U aparece quase sempre depois
de Q, o peso ponderado de U (4,690%) já reflete isso; nenhum ajuste especial para dígrafos é proposto
aqui; QU/NH/LH/ÃO como tiles de dígrafo (dossiê §1.6) são modelo e geração da fase 2, fora do escopo
deste documento.

Se um mutador proibir uma letra (`LETRA_PROIBIDA`, dossiê §1.5) ou a grade continuar sem vogal
suficiente, os pesos remanescentes devem ser renormalizados antes do sorteio; isso é responsabilidade
do gerador (fase 2), não muda os pesos base aqui.
