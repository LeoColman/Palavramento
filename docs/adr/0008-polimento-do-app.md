# ADR 0008: Polimento do `:app` (fase 6)

**Status:** aceita (fase 6)

## Contexto

A fase 6 pede animações dos tiles, haptics, cronômetro flip e tratamento de rede instável sobre o
cliente que a fase 4 entregou (`docs/adr/0006-arquitetura-do-app.md`), com critério de aceite: "Reconexão
no meio da rodada restaura o estado sem perder palavras". A fase 5 (SQLDelight, login, estatísticas
vitalícias) roda em paralelo em outro agente, então as decisões abaixo evitam tocar `:domain`,
`:server`, `ui/lobby/`, o pacote `data/` e `network/RestApi`/`KtorRestApi`.

## Decisão

### Rede instável: `connectionStatus` separado de `state`, fila de submissões pendente

Antes da fase 6, `MultiplayerSession.run()` jogava `state` de volta para `MatchUiState.Disconnected`
a cada queda de conexão, antes de tentar reconectar. Isso cumpria "reconecta e restaura `alreadyFound`"
mas, entre a queda e a reconexão, a tela de sala inteira (grade, palavras já encontradas, placar)
sumia atrás do indicador de "Conectando..." - o oposto de "restaura o estado sem perder palavras" se
a intenção for o jogador nunca perder de vista o que já tinha.

A fase 6 separa os dois sinais:

- `MultiplayerSession.state: StateFlow<MatchUiState>` agora só avança (Disconnected uma vez, no
  início; depois Lobby/InRound/PostRound conforme as mensagens chegam) e nunca mais volta a
  `Disconnected` numa queda. `RoomScreen` continua mostrando a última tela alcançada.
- `MultiplayerSession.connectionStatus: StateFlow<ConnectionStatus>` (`Connected`/`Reconnecting`) é o
  sinal dedicado para a faixa "Reconectando..." que `RoomScreen` sobrepõe à tela atual
  (`Box` + `Alignment.TopCenter`), sem trocar de tela.

`PendingSubmissionQueue` (`network/PendingSubmissionQueue.kt`), uma classe pura sem coroutines,
guarda `ClientMessage.SubmitWord` feitas enquanto `connectionStatus != Connected`.
`MultiplayerSession.submitWord` enfileira nesse caso (ou se o `send` falhar mesmo com
`connectionStatus == Connected`, quando a queda ainda não foi detectada); `collectUntilDisconnected`
drena a fila (`drain(roundId)`) assim que um `RoundStart` fresco chega após reconectar - só os itens
com o mesmo `roundId` do `RoundStart` são reenviados, o resto (de uma rodada que já terminou) é
descartado. Um reenvio que o servidor já havia aceito antes da queda volta como
`WordRejected(JA_ENCONTRADA)` (dossiê 5.1), que `MatchStateReducer` já trata como rejeição inofensiva
(não mexe em `foundWords`) - nenhuma mudança adicional foi necessária ali.

O relógio já ressincronizava a cada handshake antes da fase 6 (`tryConnectAndHandshake` roda o
handshake de `ClockSync` em toda tentativa de conexão, inclusive reconexões); isso não mudou.

### Fundo/primeiro plano: `RoomViewModel.pause()`/`resume()`, não só `stop()`

`MultiplayerSession.stop()` (pré-existente) só zera uma flag lida no próximo giro do laço de
`run()` - se a corrotina estiver suspensa dentro de `collectUntilDisconnected()` (conexão viva,
aguardando mensagens), `stop()` sozinho não fecha o socket nem cancela essa suspensão. Para
desconectar de verdade ao ir para segundo plano sem vazar a corrotina do laço, `RoomViewModel` agora
guarda o `Job` de `session.run()` e:

- `pause()` cancela esse `Job` e chama `session.disconnect()` (novo método: `running = false` +
  `transport.close()`) numa corrotina separada.
- `resume()`/`start()` relança `session.run()` do zero - mesmo caminho de handshake/`JoinRoom` de
  qualquer reconexão (dossiê 5.3).

`RoomScreen` registra um `LifecycleEventObserver` (`ON_STOP`/`ON_START`) via `DisposableEffect` sobre
`LocalLifecycleOwner`, chamando `pause()`/`resume()`. Isso é adicional ao que `RoomViewModel.onCleared()`
já fazia para quando a tela sai da pilha de navegação (esse caminho continua intocado).

### Animações: `LocalAnimationsEnabled` lido de `ValueAnimator.areAnimatorsEnabled()`

O Compose não respeita sozinho o ajuste "Remover animações" do Android (`Settings.Global.ANIMATOR_DURATION_SCALE`).
`PalavramentoTheme` lê `ValueAnimator.areAnimatorsEnabled()` uma vez e expõe via
`ui/common/MotionPreference.kt` (`LocalAnimationsEnabled`); todo ponto de animação nova da fase 6
(traço do caminho no tabuleiro, flash de aceite/rejeição, giro do tabuleiro, dígitos do cronômetro
flip) passa sua duração base por `animationDurationMillis(base)`, que retorna 0 (salto instantâneo)
quando o ajuste está desligado, em vez de duplicar a leitura do Android em cada composable.

### Gesto: `awaitFirstDown` + `drag`, sem o touch slop de `detectDragGestures`

`detectDragGestures` só chama `onDragStart` depois que o ponteiro atravessa o touch slop, reportando
a posição onde o slop terminou de ser consumido - não onde o dedo realmente tocou. Como o primeiro
tile pode ficar fora desse raio, ele podia ser perdido ou trocado pelo vizinho. `BoardView` agora usa
`awaitEachGesture { awaitFirstDown(); ...; drag(...) }`: `drag()` é o mesmo laço de rastreamento que
`detectDragGestures` usa por baixo, só que sem a etapa de slop antes dele, então o primeiro tile é
registrado exatamente no toque inicial. `PathTracer` não mudou; os três testes instrumentados de gesto
(anexar, desfazer, rejeitar não-adjacente) continuam passando sem alteração.

O caminho traçado também passa a ser desenhado como uma linha sobre os tiles (`Canvas` dentro do
`Box` do tabuleiro), em espaço de tela: `displayIndexOf` (`BoardView.kt`, testado em
`BoardGeometryTest`) é o inverso de `logicalIndexAt` (`:domain`), convertendo cada índice lógico do
caminho de volta para a célula de tela em que ele está desenhado sob a rotação atual.

### Rotação: giro visual antes da troca lógica, sem tocar em `logicalIndexAt`

"Girar" trocava `Rotation` instantaneamente, e `logicalIndexAt` já redesenha a grade toda na nova
orientação no mesmo frame - um salto. `MatchScreen` agora anima um `Animatable<Float>`
(`visualRotationDegrees`) de +90 graus aplicado via `graphicsLayer { rotationZ = ... }` sobre o
`Box` que contém a grade e o `Canvas` do caminho, e só troca o `Rotation` lógico (o que
`logicalIndexAt` usa) depois que essa animação termina, resetando o ângulo visual para 0 no mesmo
instante - visualmente contínuo, porque uma rotação física de 90 graus da grade é, por definição,
equivalente ao remapeamento que `logicalIndexAt` já fazia. Um flag `rotating` ignora cliques
repetidos em "Girar" enquanto uma animação está em andamento, para não perder um giro por causa de
`Animatable.animateTo` sendo interrompido por outro em cima.

### Haptics: toggle próprio em DataStore, fora de `data/`

`settings/SettingsRepository.kt` + `DataStoreSettingsRepository.kt` (pacote novo, não em `data/`,
para não colidir com o trabalho da fase 5 ali) guardam só `hapticsEnabled: Boolean` num DataStore de
Preferences próprio (`"settings"`, separado do `"auth"` de `TokenRepository`). `MatchSettingsSheet`
(`ui/settings/`), aberta pelo ícone de ajustes do cabeçalho da partida, tem o `Switch` e a entrada
"Sobre". `BoardView` usa o toggle para o tique de anexar tile; o aceite/rejeição (já existente desde
a fase 4) passa a checar o mesmo toggle antes de vibrar.

### Cronômetro flip

`ui/common/FlipCountdown.kt` (`countdownDigits`/`isCountdownUrgent` em `TimeFormat.kt`, puras e
testadas) mostra os quatro dígitos de `MM:SS`; cada um gira em `rotationX` (0 -> 90 -> 180, trocando
o dígito exibido na metade) quando muda, sempre a partir de `remainingMs` (que já vinha só de
`ServerClock`, nunca do relógio de parede). Fica vermelho (`colors.rejected`) nos últimos 10 segundos.
A mesma composable, com `digitSize` menor, serve tanto o cronômetro grande da partida quanto
"Próxima partida em" (Espera e Resultados).

### Sobre / licenças

`ui/about/AboutScreen.kt`, alcançável pela entrada "Sobre" da folha de ajustes (nova rota
`Routes.About`), mostra a licença AGPL-3.0-or-later do próprio Palavramento com link para o
código-fonte, a atribuição do dicionário VERO (LGPLv3) e o texto de atribuição da FrequencyWords
exigido por `LICENSES.md` (CC BY-SA 4.0), textualmente igual ao especificado lá.

### Ícone do launcher

Ícone adaptativo vetorial (`res/mipmap-anydpi-v26/ic_launcher.xml` +
`ic_launcher_round.xml`, camadas em `res/drawable/ic_launcher_background.xml`/
`ic_launcher_foreground.xml`): fundo laranja (`PalavramentoColors.tileBackground`), monograma "P" em
tom escuro (`PalavramentoColors.tileText`) desenhado como `path` vetorial (VectorDrawable não
suporta texto). `minSdk` já é 26, então não há fallback legado - só a camada `-v26`.

## Consequências

- `MultiplayerSessionTest` foi atualizado: a asserção antiga de que `state` voltava a `Disconnected`
  numa queda deu lugar à nova de que `state` permanece em `InRound` e `connectionStatus` vira
  `Reconnecting` - mudança de comportamento deliberada desta fase, não uma regressão.
- `SubmissionFeedback.Accepted`/`Rejected` (`state/MatchUiState.kt`) ganharam um campo `path`
  (o servidor já ecoava `path` em `WordAccepted`/`WordRejected`, dossiê 5.1) para o tabuleiro saber
  quais tiles piscar; `MatchStateReducerTest` foi ajustado.
- `:domain` não foi tocado. `ui/lobby/`, `data/`, `network/RestApi`/`KtorRestApi` não foram tocados.
  `di/AppModule.kt`, `di/ViewModelModule.kt`, `app/build.gradle.kts` e `strings.xml` só receberam
  entradas novas, sem reorganizar o que já existia.
- O link de código-fonte em "Sobre" (`about_source_url`) é um placeholder
  (`https://github.com/leonardocolman/palavramento`); precisa ser substituído pelo endereço real do
  repositório antes de publicar o app.
