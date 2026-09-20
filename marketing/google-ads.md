# Campanha de App no Google Ads

Tudo que o Google Ads pede para uma campanha de App (instalações) do Palavramento, pronto para
copiar e colar. Os limites de caractere de cada texto deste arquivo já foram conferidos; o
conferidor está em [`src/check-copy.js`](src/check-copy.js).

## Antes de criar a campanha

1. **A campanha de App exige o app publicado no Google Play.** Ela não aceita APK avulso nem link
   para site: você escolhe um app da Play Console e é isso. Enquanto o Palavramento não tiver ficha
   publicada, não há campanha para criar. A ficha está escrita em [`play-store.md`](play-store.md).
2. **A campanha de App não aceita palavra-chave.** A segmentação é automática e o Google decide onde
   exibir lendo a ficha da Play Store. Ou seja: o trabalho de palavra-chave acontece no título, na
   descrição curta e na descrição completa da ficha, não aqui. A lista está em
   [`play-store.md`](play-store.md).
3. **Não escrever "Wordament" em lugar nenhum.** É marca da Microsoft. O dossiê descreve o
   Palavramento como um clone, mas isso é vocabulário interno de projeto: num anúncio ou numa ficha
   de loja vira uso de marca de terceiro e o anúncio é reprovado.
4. **Conferir a conversão.** A campanha otimiza para instalação (`first_open`), que o Google Play
   reporta sozinho depois que a conta do Ads é vinculada à Play Console. Sem esse vínculo a campanha
   roda cega.

## O objetivo real desta campanha

O Palavramento não tem anúncio, não tem compra dentro do app e não tem assinatura. Uma instalação
não gera receita, então não existe ROI para calcular e não adianta perguntar "quanto vale um
usuário". O que a campanha compra é **liquidez de sala**: a v1 só tem multijogador, e uma sala com
um jogador só não é o jogo. Isso muda duas decisões:

- **Concentrar, não espalhar.** É melhor gastar o mês inteiro em algumas janelas curtas (por
  exemplo, fim de tarde e noite, de quinta a domingo) do que diluir o mesmo dinheiro em 30 dias. Dez
  pessoas na mesma sala às 20h valem mais que as mesmas dez espalhadas pela semana.
- **A métrica de sucesso é jogadores simultâneos por rodada, não instalações.** O servidor já expõe
  isso: `GET /metrics` (Prometheus, protegido por `METRICS_TOKEN`). Olhe a série de jogadores por
  rodada durante a campanha, não só o relatório do Ads.

## Estrutura

Uma campanha, três grupos de anúncios. O Google testa combinações de texto e imagem dentro de cada
grupo, então cada grupo carrega um argumento diferente e o sistema descobre qual pega.

| Campo | Valor |
|---|---|
| Tipo | Aplicativo > Promover instalações do app |
| Plataforma | Android |
| App | `br.com.colman.palavramento` |
| Local | Brasil |
| Idioma | Português |
| Estratégia de lance | CPI desejado (custo por instalação) |
| Orçamento diário | no mínimo 50x o CPI desejado |
| Grupos de anúncios | Ao vivo, Sem anúncios, Desafio |

Sobre o orçamento: a recomendação do próprio Google é orçamento diário de pelo menos 50 vezes o CPI
desejado, senão a campanha fica limitada e nunca sai da fase de aprendizado. Se o CPI desejado for
R$ 2,00, o orçamento diário precisa ser R$ 100,00 ou mais. Como não há receita para ancorar o CPI,
escolha o caminho inverso: decida quanto pode gastar por dia nas janelas que escolheu e divida por
50 para achar o CPI desejado.

Depois de publicar, não mexa: mudança de mais de 20% no lance ou no orçamento reabre a fase de
aprendizado. Espere de 7 a 14 dias antes de avaliar.

## Textos

Limites do Google: título com até 30 caracteres, descrição com até 90. Até 5 de cada por grupo de
anúncios, e o Google recomenda mandar os 5. O texto **não é traduzido** na veiculação, então o
idioma da campanha precisa bater com o idioma escrito aqui.

### Grupo "Ao vivo" - simultaneidade e placar

Títulos:

<!-- limite: 30 -->
| Texto | Car. |
|---|---|
| Todos na mesma grade 4x4 | 24 |
| 2 minutos. Uma grade só. | 24 |
| Placar ao vivo toda rodada | 26 |
| Ache as palavras antes deles | 28 |
| Nova rodada a cada 2 min | 24 |

Descrições:

<!-- limite: 90 -->
| Texto | Car. |
|---|---|
| Grade 4x4, dois minutos, todo mundo na mesma grade ao mesmo tempo. Grátis, sem anúncios. | 88 |
| Mesma grade, mesmo cronômetro, placar na hora. Só depende de quem acha mais palavra. | 84 |
| Entre quando quiser: a próxima rodada começa em menos de um minuto. | 67 |
| Trace a palavra com o dedo e some pontos. Quem achar mais palavra sobe no placar. | 81 |
| No fim da rodada você vê todas as palavras que existiam e quais deixou passar. | 78 |

### Grupo "Sem anúncios" - gratuito, sem pegadinha, código aberto

Títulos:

<!-- limite: 30 -->
| Texto | Car. |
|---|---|
| Sem anúncios, sem compras | 25 |
| Jogo de palavras grátis | 23 |
| Caça-palavras sem anúncio | 25 |
| Nada de anúncio no meio | 23 |
| Código aberto e gratuito | 24 |

Descrições:

<!-- limite: 90 -->
| Texto | Car. |
|---|---|
| Sem anúncios, sem compras, sem rastreamento. Código aberto sob licença AGPL. | 76 |
| Só pede internet. Nenhuma outra permissão, nenhum anúncio no meio da rodada. | 76 |
| Caça-palavras multijogador de graça do começo ao fim. Não existe versão paga. | 77 |
| Grade 4x4, dois minutos, placar ao vivo. Nenhum anúncio interrompe a partida. | 77 |
| Feito em português do Brasil, com dicionário pt-BR de verdade. | 62 |

Tudo aqui é verificável no repositório, o que importa porque o Google reprova promessa que a ficha
da loja contradiz: o `:app` não declara nenhuma biblioteca de anúncio, faturamento ou telemetria, e
`AndroidManifest.xml` pede só `android.permission.INTERNET`.

### Grupo "Desafio" - habilidade e vocabulário

Títulos:

<!-- limite: 30 -->
| Texto | Car. |
|---|---|
| Prove que é bom de palavra | 26 |
| Palavras contra o relógio | 25 |
| Quantas você acha em 2 min? | 27 |
| Jogo de palavras em pt-BR | 25 |
| Suba de nível achando palavra | 29 |

Descrições:

<!-- limite: 90 -->
| Texto | Car. |
|---|---|
| Cada rodada tem um tema que muda as regras: dígrafos, letra valiosa, peça de duas letras. | 89 |
| Suba de nível, guarde seu histórico e veja sua média de pontos por partida. | 75 |
| Dicionário de português brasileiro de verdade: acentos, dígrafos e tudo mais. | 77 |
| Veja quantos pontos a grade tinha e quanto você tirou dela. Depois tente de novo. | 81 |
| Duas grades nunca são iguais: o servidor gera e resolve cada uma na hora. | 73 |

## Imagens

Especificação do Google para campanha de App: `.jpg` ou `.png`, no máximo 5 MB, até 20 imagens de
cada proporção.

| Proporção | Recomendado | Mínimo | Arquivo em [`ads/`](ads/) |
|---|---|---|---|
| Horizontal 1.91:1 | 1200x628 | 600x314 | `paisagem-1200x628-grade.png`, `paisagem-1200x628-tela.png` |
| Quadrada 1:1 | 1200x1200 | 200x200 | `quadrado-1200x1200-grade.png` |
| Vertical 4:5 | 1200x1500 | 320x400 | `retrato-1200x1500-grade.png`, `retrato-1200x1500-tela.png` |

As variantes `-grade` desenham o tabuleiro; as `-tela` usam uma captura real do app. Mandar as duas
dá ao Google material para comparar tabuleiro estilizado contra produto real.

Todas saem de [`src/banner.html`](src/banner.html), renderizado por
[`src/render.sh`](src/render.sh) com Chrome headless no tamanho exato, sem reamostragem. As cores
são as do app (`ui/theme/PalavramentoColors.kt`), então o anúncio e a tela que ele abre parecem o
mesmo produto. Para mudar um texto ou gerar outra variante, edite o HTML e rode:

```bash
marketing/src/render.sh
```

Uma restrição de política que o layout já respeita: nada nas imagens imita botão clicável. O Google
reprova imagem com falso elemento de interface.

## Vídeo

A campanha aceita até 20 vídeos de 10 a 60 segundos em 16:9, 9:16 e 1:1, e **eles precisam estar no
YouTube** antes de entrar na campanha. Se você não mandar nenhum, o Google monta um vídeo sozinho a
partir dos outros recursos, e o resultado costuma ser pior que uma captura de tela do jogo rodando.

Os cortes prontos estão em [`ads/video/`](ads/video/), gerados a partir de uma captura de tela real
do emulador com [`src/render-video.sh`](src/render-video.sh). Falta subir ao YouTube (como "não
listado" já serve) e colar as URLs na campanha.

## Capturas

[`screenshots/`](screenshots/) tem as capturas cruas do emulador (Pixel 9a, 1080x2424), com a barra
de status em modo demo: relógio fixo em 10:00, bateria cheia, sem notificação. Servem para a ficha
da Play Store e são a fonte das variantes `-tela` dos banners.

Para refazer: [`src/capturar.sh`](src/capturar.sh) descreve a receita, que precisa do servidor
local e dos jogadores de teste ligados.
