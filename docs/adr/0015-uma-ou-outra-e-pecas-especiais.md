# ADR 0015: Mutador "Uma ou outra" e peças especiais mais jogáveis

**Status:** aceita (decisão do dono do produto, 2026-09-18)

## Contexto

Dois pedidos do dono do produto, 2026-09-18:

1. Um mutador novo: "Uma letra separada por / 'A/F' que vale 20 pontos e pode ser usada em palavras
   tanto com A quanto com F."
2. "Vamos tornar esses jogos um pouco mais divertidos": o dono viu rodadas de `DIGRAFOS` com o tile
   de dígrafo num canto e sem vogal ao redor, de modo que nenhuma palavra conseguia usá-lo. O mesmo
   problema podia acontecer, em tese, com `LETRA_VALIOSA` (a cópia inflada isolada), `LETRA_NOS_CANTOS`
   (um canto sem vizinho jogável) e, agora, com o próprio tile de "uma ou outra".

Esta ADR fecha os dois pedidos juntos porque o segundo é, na prática, o critério de aceite que a peça
nova do primeiro também precisa.

## Decisão

### 1. Modelo do tile: `letters` pode ter alternativas separadas por `/`

`Tile.letters` (`domain/.../board/Tile.kt`) continua sendo um único campo `String`, sem mudança de
forma no fio nem no `board_json` persistido: uma rodada já gravada em produção continua legível sem
migração, porque o parser só ganhou uma validação nova (aceitar `/` como separador), nunca perdeu
nenhuma capacidade de decodificar o que já existia. Um tile comum (`"A"`) ou de dígrafo (`"QU"`)
continua com uma única opção; um tile de alternativas (`"A/F"`) tem duas. `Tile.options: List<String>`
é a lista de alternativas (`letters.split('/')`), computada uma vez e cacheada (`by lazy`), não
recalculada a cada passo do solver: `Tile.options` não é uma propriedade de construtor, então o
serializador `kotlinx.serialization` gerado para `Tile` nunca a inclui, e um peer antigo (app ou
servidor) que não sabe da ADR simplesmente vê um `letters` com um caractere `/` dentro, sem erro de
decodificação de tipo (`ignoreUnknownKeys`, ADR 0005, nem entra em jogo aqui: o campo em si não é
novo, só o conteúdo que ele pode carregar).

Validação: cada opção separada por `/` deve ser não vazia e A-Z (dossier 1.6, sem diacrítico). `/A`,
`A/`, `A//F` são rejeitados na construção do `Tile`.

### 2. Mutador `UMA_OU_OUTRA` / `Mutator.OneOrOther(first, second)`

Título pt-BR: `"Uma ou outra: $first/$second"` (ex.: "Uma ou outra: A/F"), no mesmo padrão dos outros
títulos (`MutatorTheme.title`).

### 3. Colocação: um tile, num dos quatro centros, vogal + consoante ponderada

O gerador (`BoardGenerator.applyOneOrOther`) desenha **um** tile `"$first/$second"` valendo
**20 pontos** (`BoardGenerator.OneOrOtherValue`, constante nomeada) numa das quatro posições centrais
do tabuleiro 4x4 (índices `5, 6, 9, 10`: as únicas com 8 vizinhos, dossier 1.1). `centersOf(size)`
generaliza isso para outros tamanhos ("qualquer posição sem borda"; vazio para tabuleiros 2x2 ou
menores, caso em que o gerador cai de volta para qualquer posição, situação que só aparece em teste).
A escolha entre os centros usa o `Random` com seed já em curso, então a geração continua determinística.

`first` é sorteado de `A, E, I, O` (as quatro vogais). `second` é sorteado de um pool de consoantes
comuns em pt-BR, ponderado pela frequência de uso real (coluna "% ponderado" de
`docs/calibracao-letras.md`, a mesma medição que já embasa `letter-weights.json`), arredondada para
pesos inteiros pequenos e expandida em entradas repetidas (`RoundDescriptorPicker.consonantPool`):

| Letra | Peso | Letra | Peso |
|---|---|---|---|
| S | 8 | C | 4 |
| R | 7 | D | 4 |
| N | 5 | L | 3 |
| M | 5 | P | 3 |
| T | 5 | F | 1 |

O seletor de rodada (`RoundDescriptorPicker`) ganhou um quinto desfecho igualmente provável, então os
cinco mutadores (`NoMutator`, `ValuableLetter`, `Digraphs`, `LetterInCorners`, `OneOrOther`) têm 1/5 de
chance cada.

### 4. Solver: cada opção é um ramo de busca separado

O DFS (`Solver.search`) não concatena mais `tile.letters` direto: para cada tile visitado, tenta
**cada** `Tile.options` como uma continuação distinta a partir do mesmo nó do léxico, sem duplicar a
marcação de "visitado" (a mesma peça física só é usada uma vez por palavra, não importa qual opção).
Um buffer por profundidade (`chosen: Array<String?>`) grava qual opção foi escolhida em cada passo do
ramo atual; a palavra normalizada de uma entrada encontrada é montada a partir desse buffer, não mais
de `tiles[it].letters` (que, para um tile de alternativas, incluiria o `/` literal). Um tile comum ou
de dígrafo, com uma única opção, se comporta exatamente como antes: nenhuma mudança de resultado para
tabuleiros sem tile de alternativas.

### 5. `Path.spellings`: um caminho pode soletrar mais de uma palavra

`Path.spell` (concatenação literal) continua existindo, para tabuleiros sem tile de alternativas.
`Path.spellings(board)` é nova: o produto cartesiano das opções de cada tile do caminho, na ordem em
que aparecem (tile mais cedo varia mais devagar, o mesmo padrão de um loop aninhado). Um tabuleiro só
carrega um punhado de tiles de alternativas (o gerador nunca coloca mais de um por rodada), então esse
produto nunca é grande na prática; nada aqui impõe um limite explícito.

### 6. `SubmissionValidator`: qual das soletrações vale

Ordem de checagem inalterada em espírito (`InvalidPath` → comprimento → léxico → duplicidade), mas
agora sobre o conjunto de soletrações:

1. `InvalidPath`: como antes.
2. `TooShort`: nenhuma soletração alcança o mínimo de 3 letras.
3. `NotAWord`: nenhuma soletração (das que passaram no comprimento) está no léxico.
4. Entre as soletrações que são palavras do léxico, aceita a **primeira, na ordem das opções**, que
   ainda não foi encontrada. Se todas já foram, `AlreadyFound`.

Consequência de jogo: traçar o mesmo caminho duas vezes pode legitimamente pontuar duas palavras
diferentes, uma por opção (ex.: "CASA" na primeira passada, "CFSA" numa segunda, se ambas forem
palavras válidas).

`SubmissionResult.Accepted` ganhou um campo, `normalized`, a forma exata que o validador aceitou (além
de `word`, a forma de exibição, e `score`). Antes, o único jeito de saber qual forma normalizada
pontuou era re-soletrar o caminho (`Path.spell`) - o que, com mais de uma soletração possível, é
ambíguo e podia gravar a palavra errada como "já encontrada". `RoundState.submit` (servidor) e
`OptimisticSubmission.decide` (app) foram corrigidos para usar `result.normalized` diretamente. Esta
era uma correção necessária, não cosmética: sem ela, o servidor gravaria sempre a mesma forma
(a soletração literal do `Path.spell`, que para um tile de alternativas nem é uma palavra real, já que
inclui o `/`), quebrando o próprio mutador.

### 7. Regra de aceite nova no gerador: toda peça especial precisa aparecer na solução

`SpecialTileCriteria` (`domain/.../generator/SpecialTileCriteria.kt`), parâmetro de
`GenerationCriteria` (campo `specialTiles`), soma-se aos critérios existentes (`commonMin`,
`totalWordsMin`, `maxScoreRange`) e é relaxada junto deles pela mesma regra de relaxamento
(`GenerationCriteria.relaxed()`), então a geração continua garantidamente terminando. Limiares,
todos parâmetros com valor padrão:

- `minWordsPerTile` (padrão **5**): cada tile de dígrafo, o tile de letra valiosa e o tile de
  alternativas (contagem total, somando as duas opções) precisam aparecer no caminho de pelo menos
  essa quantidade de palavras da solução.
- `minWordsPerCorner` (padrão **2**): cada canto de `LETRA_NOS_CANTOS` precisa aparecer em pelo menos
  essa quantidade de palavras.
- `minWordsPerOption` (padrão **2**): o tile de alternativas precisa ter **cada** uma de suas duas
  opções usada por pelo menos essa quantidade de palavras (não basta a opção A aparecer 5 vezes e a
  opção F nunca).
- Para `Dígrafos`, além da contagem, pelo menos uma das palavras que usam aquele tile precisa ser
  Comum (dossier 1.7): um dígrafo só alcançável por uma palavra rara continua tecnicamente jogável,
  mas na prática ninguém encontra.

`BoardGenerator.generate` calcula, a cada tentativa, quais posições o mutador escreveu
(`BoardDraw.specialPositions`) e verifica a solução do solver contra elas antes de aceitar o
tabuleiro; senão, redesenha, exatamente como já acontecia para os critérios antigos.

Caso de borda documentado: quando o limiar relaxa até 0 e a peça específica realmente não tem nenhuma
palavra (léxico sintético de teste, por exemplo), a checagem "pelo menos uma Comum" de `Dígrafos`
teria uma verdade vazia perversa (`emptyList().any { }` é sempre `false`, o que nunca relaxaria) -
tratada explicitamente como satisfeita quando não há nenhuma palavra usando o tile, para não travar o
relaxamento indefinidamente.

### 8. Colocação de dígrafos revista: nunca em canto, nunca vizinho de outro dígrafo

Pedido concreto do dono do produto, motivado pelo bug que ele observou. `BoardGenerator.digraphPositions`
escolhe as posições dos tiles de dígrafo com uma busca por backtracking (não gulosa: uma escolha gulosa
pode se prender numa combinação ruim e nunca achar uma válida que existe) sobre os candidatos não-canto,
embaralhados pelo `Random` com seed em curso, exigindo que nenhum par de dígrafos escolhidos seja
adjacente (8 direções). No tabuleiro 4x4, o conjunto máximo de posições não-canto mutuamente
não-adjacentes é **4** (confirmado por busca exaustiva, não só por inspeção manual - o padrão não é o
óbvio "todo cantinho de bloco 2x2", existe um arranjo em diagonal que também funciona), então
`Digraphs.count` continua podendo ser 2, 3 ou 4 (o dossier original já previa 2 a 4; nenhuma medição
deste trabalho indicou necessidade de reduzir).

Consequência para tabuleiros já em produção: nenhuma. A regra de colocação só se aplica à *geração* de
tabuleiros novos; um `board_json` já persistido continua sendo lido do jeito que foi gerado, mesmo que
tenha um dígrafo num canto (rodadas antigas, geradas antes desta ADR).

## Medição com o léxico real

`RealLexiconCalibrationTest` (`:server`) roda os cinco mutadores, 10 seeds cada, contra o léxico
completo, checando os critérios de geração (incluindo os novos de `SpecialTileCriteria`, nos limiares
padrão) e imprimindo tentativas/tabuleiro e ms/tabuleiro:

| Mutador | Tentativas/tabuleiro (média) | ms/tabuleiro (média) |
|---|---|---|
| `NoMutator` | 3 | 0 |
| `ValuableLetter('L', 10)` | 2 | 0 |
| `Digraphs(3)` | 8 | 1 |
| `LetterInCorners('O')` | 5 | 1 |
| `OneOrOther('A', 'F')` | 4 | 6 |

Todos os cinco ficam bem abaixo do limite de preocupação (100 tentativas/tabuleiro em média): a regra
nova de peça especial não pesou a geração de forma perceptível com o léxico real (~2,3 milhões de
entradas normalizadas). Nenhum ajuste de pool ou colocação foi necessário além da própria regra de
colocação de dígrafos (item 8) e do pool de consoantes ponderado (item 3), já desenhados para não
depender de sorte.

## Consequências

- `Tile`, `Mutator`, `SubmissionResult` e o solver ganharam comportamento novo, mas nenhuma forma de
  fio nova: uma rodada gravada antes desta ADR (sem `/` em nenhum `letters`, sem `UMA_OU_OUTRA` em
  `mutator_json`) continua sendo lida e reproduzida sem migração. Não há `V*__` do Flyway associado a
  esta ADR (ao contrário da ADR 0012), porque nada precisou ser reescrito.
- Um cliente (app) que ainda não tem esta mudança recebe um `board_json`/`RoundStart.board` com um
  tile `"A/F"` e simplesmente exibe a string como está (o app já renderiza `tile.letters` sem
  interpretar o conteúdo); o mutador em si (`UMA_OU_OUTRA`) quebraria a decodificação desse cliente
  antigo por discriminador desconhecido, o mesmo risco de compatibilidade que a ADR 0012 já aceitou
  para `DIGRAFOS`/`LETRA_NOS_CANTOS` (dossier: sem distribuição pública ainda, aceitável).
- `docs/adr/0012-mutadores.md` foi atualizado (colocação de dígrafos). `docs/dossie.md` §1.5 ganhou uma
  nota apontando para esta ADR, no mesmo padrão das notas das ADRs 0010/0012.
- `SpecialTileCriteria` fica dentro do alvo do Pitest (não é DTO), coberta por testes de propriedade
  com léxico sintético e limiares injetados (`domain/.../generator/BoardGeneratorTest.kt`), e por
  `RealLexiconCalibrationTest` (`:server`) com o léxico real.
