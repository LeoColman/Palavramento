# ADR 0014: Validação otimista no cliente

**Status:** aceita (decisão do dono do produto e do orquestrador, 2026-09-15)

## Contexto

O servidor roda na Europa; a maior parte dos jogadores joga do Brasil. O tempo de ida e volta
medido entre um cliente no Brasil e `palavramento.colman.com.br` fica perto de 240 ms. Como o
dossiê (§0, §5) fixa que "o servidor é a única fonte de verdade" para validade de palavra e
pontuação, hoje o jogador traça uma palavra, solta o dedo e só ve o feedback (cor do tile, som,
haptics, `Pontos:`/`Palavras:`) depois desses ~240 ms de viagem até o servidor e volta. Numa partida
de 120 segundos em que cada palavra soma poucos pontos, esse atraso é perceptível e prejudica a
sensação de resposta do jogo.

O dono do produto decidiu, em 2026-09-15, que o cliente deve mostrar um veredito instantâneo ao
soltar o caminho, sem esperar o servidor, mantendo o servidor como autoridade final de pontuação e
resultado. Esta ADR documenta como isso foi implementado sem duplicar nenhuma regra de validação.

## Decisão

### O que é enviado: a solução da rodada em `RoundStart`

`ServerMessage.RoundStart` (`domain/.../protocol/ServerMessage.kt`) ganhou um campo
`validWords: List<ValidWord> = emptyList()`, onde `ValidWord(normalized: String, display: String)`
é um DTO novo em `protocol`. É a solução completa da rodada, o mesmo conjunto que o servidor já
calculava na geração da grade e persiste em `round_words` (dossiê §3, §7): nenhum cálculo novo, só
um campo novo carregando um dado que o servidor já tinha.

O campo vai em **todo** `RoundStart` que o servidor manda: entrada no horário, entrada tardia (ADR
0010) e reconexão no meio da rodada (ADR 0005, ADR 0007). `RoomScheduler.toRoundStartMessage`
(`server/.../round/RoomScheduler.kt`) agora exige a lista de `SolvedWord` de
`GeneratedRound.solution`/`RoundState.generated.solution` em todo ponto de construção da mensagem,
justamente para que nenhum caminho esqueça de preencher o campo.

O valor padrão vazio garante compatibilidade nos dois sentidos (ADR 0005 já usava essa técnica para
os campos de reconexão): um servidor antigo nunca envia `validWords`, e um cliente novo que recebe a
lista vazia cai de volta no comportamento de sempre, sem veredito local, espera o servidor. Um
cliente antigo simplesmente ignora o campo (`ignoreUnknownKeys`, ADR 0005).

### A mesma validação dos dois lados, sem duplicar regra

`domain/.../lexicon/ValidWordLexicon.kt` acrescenta `List<ValidWord>.toLexicon(): Lexicon`, que
constrói um `InMemoryLexicon` com `LexiconEntry(display, LexiconEntry.Unranked)` para cada palavra.
O cliente chama exatamente o mesmo `SubmissionValidator.validate(board, lexicon, alreadyFound, path)`
que o servidor chama, só que com esse léxico reduzido à solução da rodada em vez do léxico completo
de ~1 milhão de formas.

O argumento de que os dois lados sempre concordam é `OptimisticValidationTest`
(`domain/src/test/.../submission/OptimisticValidationTest.kt`), um teste de propriedade: para grades
e léxicos pequenos aleatórios, valida-se um caminho contra o léxico construído a partir da solução do
solver (`Solver.solve`) e contra o léxico completo, para qualquer conjunto `alreadyFound`, e o
`SubmissionResult` é sempre igual. A prova é curta. O solver é exaustivo (`SolverTest`: "não perde
nenhuma palavra que uma enumeração ingênua de caminhos encontra"), então uma palavra alcançável por
algum caminho válido na grade está na solução se e somente se está no léxico completo, a única
pergunta que `SubmissionValidator` faz ao léxico. `RejectionReason.NotAWord`, `TooShort` e
`InvalidPath` nunca dependem do léxico; `AlreadyFound` depende só do conjunto `alreadyFound`, checado
depois, na mesma ordem dos dois lados. `WordTier` (comum/especialista) nunca entra na decisão de
`SubmissionValidator`, por isso `ValidWord` não carrega rank de frequência.

### O que o cliente faz ao soltar um caminho

`state/OptimisticSubmission.kt` (`:app`) é uma classe pura, sem coroutines nem Android, chamada por
`network/MultiplayerSession.submitWord`:

1. Se `MatchUiState.InRound.validWords` está vazio (servidor antigo, ou a própria rodada não trouxe
   solução por algum motivo), ou se o relógio sincronizado (`ServerClock`, ADR 0005 §5.3) já passou
   de `endsAt`, não há veredito local: a submissão segue exatamente como antes, direto para o
   servidor.
2. Caso contrário, `SubmissionValidator.validate` roda contra o léxico da própria solução.
   - **Aceita**: a palavra entra em `foundWords`, a pontuação soma em `runningScore`,
     `runningWords` incrementa, `lastFeedback` vira `Accepted`, os mesmos campos que um aceite do
     servidor já mexia, então o flash do tile, o som e o haptic disparam pelo caminho existente
     (`RoomAudioPolicy`, `PlayFeedbackHaptics` em `MatchScreen`), sem código novo ali. O caminho
     entra em `pendingPaths` (um `Set<List<Int>>` novo em `MatchUiState.InRound`) e o
     `SubmitWord` ainda é enviado ao servidor, enfileirado se estiver desconectado
     (`PendingSubmissionQueue`, inalterado).
   - **Rejeitada**: `lastFeedback` vira `Rejected` com o motivo (amarelo para `JA_ENCONTRADA`, igual
     a uma rejeição do servidor) e **nada é enviado**. `OptimisticValidationTest` é o argumento de
     que o servidor rejeitaria do mesmo jeito, então gastar uma viagem de rede na rejeição não
     traria informação nova.

### Reconciliação

`MatchStateReducer` (`state/MatchStateReducer.kt`) passa a checar `pendingPaths` em
`onWordAccepted`/`onWordRejected`, casando pela lista de índices que o servidor sempre ecoa em
`path` (dossiê §5.1):

- `WordAccepted` para um caminho pendente: confirmação silenciosa. `runningScore`/`runningWords`
  passam a ser os valores autoritativos do servidor (já deviam bater com os locais) e o caminho sai
  de `pendingPaths`; `lastFeedback` não é tocado, então nenhum segundo flash/som/haptic dispara para
  a mesma palavra. `WordAccepted` para um caminho que não está em `pendingPaths` (modo de
  contingência, ou qualquer caso inesperado) continua exatamente como antes.
- `WordRejected` para um caminho pendente: caso raro (o argumento de propriedade acima é por que
  deveria ser raro) que desfaz o aceite otimista: remove de `foundWords`, subtrai a pontuação de
  `runningScore`, decrementa `runningWords`, e mostra a rejeição do servidor (mesmo som/haptic de
  rejeição de sempre). `MultiplayerSession` loga um aviso (`Log.w`) antes de reduzir a mensagem,
  só quando o caminho estava pendente. Essa checagem fica ali, não dentro do reducer, para o reducer
  continuar livre de Android.
- Qualquer `RoundStart` novo (rodada nova ou reconexão) reconstrói `MatchUiState.InRound` do zero a
  partir da mensagem, o que já zera `pendingPaths`: numa rodada nova não há nada pendente para
  carregar; numa reconexão, `alreadyFound`/`runningScore`/`runningWords` que o servidor manda já são
  autoritativos (ADR 0005), e uma palavra aceita localmente mas nunca recebida pelo servidor antes da
  queda continua na fila de `PendingSubmissionQueue` (ADR 0008) e é reenviada assim que o
  `RoundStart` fresco confirma que a mesma rodada continua rodando: o mecanismo existente cobre o
  caso, sem precisar de um segundo caminho de reenvio.
- `RoundEnd`/`Leaderboard` continuam inteiramente do servidor, sem mudança.

### Depois de `endsAt`

Se o relógio sincronizado já passou do fim da rodada, a submissão nunca recebe veredito local: vai
direto ao servidor, que a descarta silenciosamente se estiver fora da janela de tolerância (dossiê
§5.2, ADR 0007) ou a processa normalmente se ainda estiver dentro dela. `RoundEnd` continua sendo a
fonte final, autoritativa, do que valeu na rodada.

## Consequências

- Um cliente modificado consegue ler `RoundStart.validWords` e descobrir a solução completa da
  rodada assim que ela começa, em vez de só no fim (dossiê §6.3, `RoundEnd`). Essa exposição é a
  mesma, em espírito, de embarcar o dicionário inteiro no aparelho (dossiê §12, ponto já aceito para
  uma v1 sem proteção anti-trapaça forte), só que agora limitada às palavras de uma única rodada em
  vez do léxico inteiro. O dono do produto aceitou esse custo explicitamente em troca da resposta
  instantânea; o servidor continua validando cada submissão de verdade, então um cliente trapaceando
  não ganha pontos que o servidor não confirme.
- O servidor continua sendo a única fonte de verdade para pontuação, ranking e resultado: nenhuma
  regra de validação mudou nele, `validWords` é só um dado a mais que ele já calculava. Um veredito
  local incorreto (o caso raro que a reconciliação de `WordRejected` cobre) nunca é definitivo. O
  rollback sempre alinha o cliente de volta ao que o servidor decidiu.
- `ValidWord` fica fora do alvo do Pitest (pacote `protocol`, mesma exclusão do ADR 0005), coberto
  pelo teste de round-trip em `ProtocolSerializationTest`. `List<ValidWord>.toLexicon()` fica dentro
  do alvo, coberto pelo teste de propriedade `OptimisticValidationTest`.
- `docs/adr/0005-protocolo.md` (o campo novo em `RoundStart`) e `docs/dossie.md` §2.5 (nota apontando
  para esta ADR, no mesmo padrão das notas das ADRs 0010/0012) foram atualizados.
