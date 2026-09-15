# ADR 0010: Entrada tardia em rodada em andamento

**Status:** aceita (decisão do dono do produto, 2026-09-15)

## Contexto

O dossiê (§1.4) fixava a regra original: "Entrar no meio de uma rodada: o jogador aguarda na tela
de resultados/espera e entra na próxima. Não há entrada tardia." A ADR 0007 implementou exatamente
essa regra: ao ativar uma rodada, `RoomScheduler` tira uma foto de quem está conectado e só esse
conjunto vira participante; quem entra depois espera a próxima rodada.

O dono do produto decidiu, em 2026-09-15, reverter essa regra: um jogador que entra durante uma
rodada ativa deve poder jogar essa mesma rodada, com o tempo que resta, em vez de esperar. Esta ADR
documenta a nova regra, a exceção de pouco tempo restante e como o servidor passa a medir o
desempenho de quem entra tarde.

## Decisão

### Regra de entrada tardia

Um jogador que envia `JoinRoom` enquanto uma rodada está ativa, e que ainda não é participante dela,
vira participante imediatamente e recebe `RoundStart` daquela rodada com o `startsAt`/`endsAt` reais
(o cliente mostra o tempo restante reduzido, calculado a partir deles como sempre) e `alreadyFound`
vazio. As submissões desse jogador são então aceitas normalmente até `endsAt` mais a tolerância de
submissão tardia (dossiê §5.2, ADR 0007), exatamente como as de qualquer outro participante.

### Exceção: pouco tempo restante

Se restar menos que `LATE_JOIN_MIN_REMAINING_SECONDS` (novo campo `ServerConfig.lateJoinMinRemaining`,
padrão 10 segundos, variável de ambiente listada no `README.md`) no momento do `JoinRoom`, o jogador
mantém o comportamento antigo: recebe `LobbyState` e entra na próxima rodada. Entrar com exatamente
o limite de tempo restante conta como permitido (fronteira inclusiva: `RoundTiming.canLateJoin`,
testada em `RoundTimingTest` e em `RoomSchedulerTest`).

Reconexão de um participante já existente não muda: continua recebendo `alreadyFound`,
`runningScore` e `runningWords` (ADR 0005, ADR 0007), sem passar pela regra de entrada tardia, que
só se aplica a quem ainda não é participante da rodada corrente.

### Instante de entrada por jogador

`RoundState` passa a registrar, por jogador, o instante em que ele efetivamente começou a jogar a
rodada: o maior entre o `startsAt` da rodada e o instante do `join`, no relógio do servidor. Quem
estava conectado quando a rodada começou mantém o próprio `startsAt` da rodada como instante de
entrada, igual a antes desta ADR.

Esse instante alimenta `RoundStatsCalculator.compute` em `RoundFinalizer.finalize`: o
`secondsPerWord` de quem entrou tarde é medido a partir da própria entrada, nunca do início da
rodada, para não inflar artificialmente essa métrica com o tempo em que o jogador ainda nem estava
na sala. Pontuação, palavras, XP, posição no ranking, percentil, `round_results` e `player_stats`
funcionam exatamente como para qualquer outro participante: nenhum desses depende do instante de
entrada, só o `secondsPerWord`.

### Persistência

O instante de entrada é gravado em `round_results.entered_at` (coluna nova, migração Flyway
`V2__round_results_entered_at.sql`, nunca alterando `V1__init.sql`). A persistência foi necessária
porque `/players/me/rounds` recalcula `RoundStats` sob demanda a partir de `submissions` (ADR 0007)
muito depois do `RoundState` em memória já ter desaparecido; manter o instante de entrada só em
memória quebraria esse endpoint para todo jogador que tivesse entrado tarde, não apenas num reinício
raro no meio da rodada. As linhas gravadas antes desta ADR foram preenchidas, na própria migração,
com o `starts_at` da rodada correspondente: o único valor correto para participantes que só podiam
entrar no início, já que a entrada tardia ainda não existia.

A reconstrução de estado após um reinício no meio de uma rodada (ADR 0007: participantes com
submissões são restaurados a partir de `submissions`) não recupera o instante de entrada original de
quem tinha entrado tarde antes do reinício: `RoomScheduler.activate` aproxima esse caso específico
usando o `startsAt` da rodada, a mesma forma de dívida aceita que a ADR 0007 já registra para o crash
durante a finalização. O impacto fica restrito ao `secondsPerWord` exibido depois (nunca à
pontuação, ao XP ou à posição no ranking), e é um caso raro (reinício do processo bem no meio de uma
rodada que já tinha entradas tardias em curso) que não justifica mais complexidade para a v1.

### Sem mudança de protocolo

Nenhum campo novo foi necessário em `:domain`: `RoundStart` já carregava `startsAt`/`endsAt` reais e
`alreadyFound`/`runningScore`/`runningWords` com valor padrão vazio/zero (ADR 0005), que é
exatamente o que a entrada tardia precisa mandar para o cliente. O instante de entrada em si nunca
trafega pelo protocolo: é um detalhe de como o servidor calcula `secondsPerWord`, não um dado que o
cliente precisa ver.

## Consequências

- Quem entra tarde compete com menos tempo de jogo que quem estava desde o início; o placar
  (`Leaderboard`, `round_results`) não normaliza pontuação por tempo jogado, igual já era o caso
  antes desta ADR para qualquer outra diferença de desempenho entre jogadores.
- `docs/dossie.md` §1.4 e `docs/adr/0007-servidor.md` foram atualizados para apontar para esta ADR
  em vez de descrever a regra antiga como vigente.
- A reidratação pós-reinício de um instante de entrada tardio é uma aproximação aceita, não
  recuperada com exatidão; documentado acima e em `RoundState`.
