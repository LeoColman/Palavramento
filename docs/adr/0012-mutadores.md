# ADR 0012: Revisão dos mutadores (dígrafos, letra nos cantos)

**Status:** aceita (decisão do dono do projeto, 2026-09-15)

## Contexto

Pedido do dono do projeto (2026-09-15): "Vamos tirar o modificador 'mínimo de letras' e 'letra
proibida'. Vamos manter dígrafo, letra de alto valor, letra nos cantos (e.g. O nos cantos)." O
dossiê (§1.5) definia quatro mutadores de v1: `SEM_MUTADOR`, `LETRA_VALIOSA`, `LETRA_PROIBIDA` e
`TAMANHO_MINIMO`; "dígrafo" e "letra nos cantos" nunca tinham sido implementados, só citados como
ideia de tema (§1.5, exemplo "L de alto valor"). Esta ADR fecha o conjunto definitivo de quatro
mutadores para o `Mutator` (`:domain`) e documenta a migração dos dados existentes.

## Decisão

### Removidos: `LETRA_PROIBIDA` e `TAMANHO_MINIMO`

`Mutator.ForbiddenLetter`/`Mutator.MinimumLength` saem do sealed interface por completo, junto com
tudo que só existia por causa deles:

- `RejectionReason.BlockedByMutator` (`BLOQUEADA_POR_MUTADOR`) e a checagem correspondente em
  `SubmissionValidator` (a ordem de checagem cai de cinco passos para quatro: caminho, comprimento,
  léxico, duplicidade).
- A poda de tiles bloqueados em `Solver` (`blocked`/`forbiddenLetter`): a busca volta a percorrer
  todos os tiles, sem a etapa extra de marcar e pular tiles com a letra proibida.
- `Mutator.blocks(word)` e `Mutator.minimumLength()`: o comprimento mínimo volta a ser sempre
  `DefaultMinimumLength` (3 letras, dossiê §1.1), sem override por mutador. `Solver` e
  `SubmissionValidator` passam a comparar direto contra essa constante.
- No app: a renderização cinza de tile proibido (`BoardTile`'s `isForbidden`,
  `ResultsScreen`'s `MiniBoard`) e a cor `PalavramentoColors.tileForbidden`, e a string
  `match_reason_blocked_by_mutator`.

Nenhum outro comportamento de pontuação, geração ou validação muda: um mutador nunca bloqueia uma
palavra de pontuar, e a única regra de comprimento mínimo que existe agora é a fixa de 3 letras.

### Mantidos e novos: o conjunto final de quatro

1. `SEM_MUTADOR` (`Mutator.NoMutator`): sem mudança. Título "Grade padrão".
2. `LETRA_VALIOSA` (`Mutator.ValuableLetter(letter, value)`): título "L de alto valor". **Só uma
   cópia da letra fica valiosa** (correção pedida pelo dono do projeto em 2026-09-15; antes todas as
   cópias recebiam o valor inflado): o gerador sorteia uma das cópias, ou transforma um tile
   aleatório na letra quando o sorteio não produziu nenhuma, e grava o valor nesse tile. Solver,
   validação e app passam a ler sempre o valor do próprio tile; `Mutator.effectiveValueOf` deixou de
   existir, e `Solver.solve`/`SubmissionValidator.validate` não recebem mais o mutador. Rodadas
   antigas continuam coerentes, porque o valor de cada tile já estava gravado em `board_json`.
3. **Novo** `DIGRAFOS` (`Mutator.Digraphs(count)`): a grade contém `count` tiles de dígrafo (dossiê
   §1.6 já previa o modelo, tile = `String`; o solver já percorre tiles de múltiplas letras como uma
   unidade, sem mudança nenhuma no `Solver` para isso funcionar). Título fixo "Dígrafos",
   independente de `count`. O gerador (`BoardGenerator.drawBoard`) desenha a grade normalmente e
   então sobrescreve `count` posições (mesmo `Random` com seed, então a geração continua
   determinística) com um tile de dígrafo sorteado de `DigraphTable`. O seletor de rodada
   (`RoundDescriptorPicker`) escolhe `count` entre 2 e 4.
   **Colocação revista pela ADR 0015** (dono do produto, 2026-09-18): as `count` posições não são
   mais um sorteio uniforme entre todas as 16 - nunca caem num canto e nunca ficam adjacentes a outro
   dígrafo (`BoardGenerator.digraphPositions`), para que todo dígrafo sempre tenha um vizinho comum
   por onde uma palavra possa entrar ou sair dele. Ver a ADR 0015 para o algoritmo de colocação (uma
   busca por backtracking, não gulosa) e para a regra de aceite nova que garante que cada dígrafo
   realmente aparece na solução da grade.
4. **Novo** `LETRA_NOS_CANTOS` (`Mutator.LetterInCorners(letter)`): os quatro tiles de canto
   (índices `0`, `size-1`, `size*(size-1)`, `size*size-1`) viram `letter`, no valor normal da letra
   (sem inflar, ao contrário de `LETRA_VALIOSA`). Título "`letter` nos cantos" (ex.: "O nos
   cantos"). O seletor escolhe a letra entre `A, E, I, O, S, R` (vogais e duas consoantes comuns,
   para os cantos sempre renderizarem algo pronunciável). O gerador sobrescreve os quatro cantos
   depois do sorteio normal da grade, sempre pelo mesmo `Random`.
5. O seletor de rodada mantinha quatro desfechos igualmente prováveis: `NoMutator` / `ValuableLetter`
   / `Digraphs` / `LetterInCorners`. As opções de `commonMin` não mudam. **A ADR 0015 acrescentou um
   quinto mutador, `UMA_OU_OUTRA`, e o seletor passou a cinco desfechos igualmente prováveis.**

### Tabela de dígrafos (`domain/src/main/resources/digraphs.json`)

Sete candidatos pt-BR: `QU`, `NH`, `LH`, `CH`, `RR`, `SS`, `GU`. Cada entrada tem `letters` (duas
letras normalizadas A-Z) e um `weight` de sorteio, na mesma técnica de soma cumulativa que
`LetterWeightTable`/`LetterValueTable` já usam. Pesos (versão 1, ponto de partida, não medido contra
o corpus real; ver `docs/calibracao-letras.md` para o precedente de como a tabela de letras foi
calibrada depois):

| Dígrafo | Peso |
|---|---|
| QU | 18 |
| CH | 16 |
| SS | 15 |
| RR | 13 |
| NH | 11 |
| GU | 9 |
| LH | 8 |

**Valor do tile de dígrafo = soma dos valores base de cada letra** (`LetterValueTable`), calculado
no momento da geração, não guardado no JSON: `QU` vale `8 + 3 = 11` na tabela de valores atual
(`letter-values.json` versão 2), `CH` vale `3 + 4 = 7`, etc. Isso significa que recalibrar
`letter-values.json` no futuro recalibra os tiles de dígrafo automaticamente, sem tocar
`digraphs.json`.

### Exclusão de `ÃO`

`ÃO` foi cogitado (é um dígrafo nasal comum em pt-BR: "então", "pão", "ação") mas ficou fora dos
candidatos. Um tile só guarda letras normalizadas A-Z, sem diacrítico (dossiê §1.6): um tile "ÃO"
teria que virar `"AO"`. Isso tem dois problemas: perde o til que o jogador esperaria ver no tile (os
outros dígrafos já são diacrítico-livres na forma normalizada, então não sofrem essa perda), e
`"AO"` como dois caracteres literais também casa com palavras que não têm nada a ver com o dígrafo
nasal, como "caos" (`C-A-O-S`, onde o "AO" é só uma sequência A depois O, não o dígrafo). Um tile de
dígrafo `AO` faria esse casamento parecer intencional para o jogador ("esse tile forma CAOS?"),
quando na verdade é uma coincidência de normalização. Os sete candidatos escolhidos não têm esse
problema: nenhum é ambíguo com uma sequência comum de letras soltas do jeito que `AO` é.

## Migração de dados legados

O banco local já tinha por volta de 200 rodadas finalizadas com `rounds.mutator_json` igual a
`{"type":"TAMANHO_MINIMO",...}` ou `{"type":"LETRA_PROIBIDA",...}`, e o cache SQLDelight do celular
guarda blobs `RoundHistoryEntry` que podem conter o mesmo. Depois desta mudança, `Mutator` não tem
mais esses casos no sealed interface, então decodificar esse JSON lança
`SerializationException` (discriminador desconhecido).

### Servidor

Migração Flyway `V3__rewrite_removed_mutators.sql` (nunca edita `V1`/`V2`, ADR 0003) reescreve
`mutator_json` para `{"type":"SEM_MUTADOR"}` onde o valor antigo era um dos dois removidos, via
`LIKE` sobre o discriminador (não sensível à ordem dos campos no JSON). `theme_title`/
`theme_subtitle` são colunas separadas (`V1__init.sql`) e não são tocadas: o histórico continua
mostrando o título original ("Letra X proibida", "Mínimo de N letras") mesmo com o mutador ativo já
em `SEM_MUTADOR`. `MutatorMigrationTest` (`server/.../server/db/`) sobe seu próprio container
Testcontainers (não o compartilhado por `testDatabase()`, que já estaria migrado até `V3` na hora em
que a maioria das specs roda), migra só até `V2`, planta uma rodada finalizada e uma pendente com
`mutator_json` legado à mão, aplica `V3` e prova que `RoundRepository.findById`/`findPending` (a
recuperação pós-reinício, ADR 0007) e `/players/me/rounds` voltam a funcionar sem lançar exceção.

### App

`HistoryRepository.toDomainOrNull` (antes `toDomain`, sem tratamento de erro) captura
`SerializationException` na decodificação de cada linha cacheada: loga um aviso (`Log.w`) com o
`roundId` e devolve `null`, que `rounds()`/`round()` filtram (`mapNotNull`/`?.let`) em vez de deixar
a exceção derrubar o Flow inteiro. A próxima sincronização bem-sucedida (`SyncService`, ADR 0009)
substitui naturalmente essa linha por uma entrada decodificável, então o efeito é temporário: só a
tela de Histórico perde aquela rodada específica até a próxima sincronização, não todas.
`HistoryRepositoryTest` ganhou um teste que planta um blob de JSON legado direto na tabela SQLDelight
(construído reescrevendo o discriminador de uma entrada válida, para não manter um JSON gigante
hardcoded) e prova que ele é pulado sem derrubar a leitura das outras rodadas cacheadas.

## Consequências

- O protocolo (`Mutator`, `RejectionReason`) não é mais compatível com um peer (app antigo, ou
  servidor antigo) que ainda esperasse `LETRA_PROIBIDA`/`TAMANHO_MINIMO`/`BLOQUEADA_POR_MUTADOR`:
  `ignoreUnknownKeys` (ADR 0005) só cobre campo desconhecido, não discriminador de tipo desconhecido.
  Como o app não tem distribuição pública ainda (dossiê: v1 em desenvolvimento), isso é aceitável sem
  uma janela de compatibilidade dupla.
- `docs/adr/0005-protocolo.md` foi atualizado para não listar mais `BlockedByMutator` entre os
  identificadores de `RejectionReason`. `docs/dossie.md` §1.5 ganhou uma nota apontando para esta
  ADR, no mesmo padrão que a nota da ADR 0010 em §1.4: o texto original do dossiê fica registrado
  como histórico, não reescrito.
- `docs/calibracao-letras.md` tinha uma frase citando `LETRA_PROIBIDA` como exemplo de mutador que
  força renormalizar os pesos de sorteio; ajustada para não citar um mutador que não existe mais,
  sem mudar a conclusão sobre renormalização (que continua válida para qualquer motivo de grade sem
  vogal suficiente).
- `:domain` ganhou um recurso novo (`digraphs.json`) e uma classe nova (`DigraphTable`), no mesmo
  padrão de `LetterWeightTable`/`LetterValueTable`: versionado, carregado uma vez (`by lazy`), com
  round-trip de JSON testado separadamente do resto do gerador.
