# ADR 0006: Arquitetura do `:app`

**Status:** aceita (fase 4)

## Contexto

A fase 4 entrega o cliente Android (Compose) completo: lobby, sala (espera/partida/resultados/
placar), traçado por arrasto e sincronização de relógio, consumindo o protocolo que a fase 2
(`:domain`) já define e que a fase 3 (`:server`) implementa em paralelo. O dossiê (§6) descreve as
telas e a cadência servidor-autoritativa; o brief da fase pede lógica de rede, relógio, gesto e
redução de estado em classes Kotlin puras testáveis na JVM, com Compose por cima.

## Decisão

### Gerência de estado: um `StateFlow<MatchUiState>` por sala, reduzido a partir do fio

`br.com.colman.palavramento.state.MatchUiState` é um `sealed interface` com quatro casos:
`Disconnected`, `Lobby` (dossiê 6.1, tela de espera), `InRound` (dossiê 6.2) e `PostRound` (dossiê
6.3/6.4, resultados + placar). `MatchStateReducer.reduce(state, message)` é uma função pura que dobra
um `ServerMessage` no estado anterior; não depende de coroutines, Android ou Koin, e é o que os
testes de `LobbyState -> RoundStart -> WordAccepted/WordRejected -> RoundEnd -> Leaderboard` exercitam
diretamente.

Duas decisões dentro do reducer não vêm literalmente do protocolo:

- `RoundEnd` não carrega `maxScore`/`maxWords`/`board`/`mutator` (só `RoundStart` os tem). Em vez de
  pedir um campo novo ao protocolo, `onRoundEnd` copia esses quatro valores do `InRound` de onde a
  rodada acabou de sair, direto no reducer. Isso mantém `:domain` intocado (fora do escopo da fase) e
  ainda assim dá à tela de Resultados o denominador "73/4193" do dossiê.
- Uma `LobbyState` recebida enquanto o estado é `PostRound` não vira um novo `Lobby`: ela só atualiza
  `nextRoundStartsAt`/`playersWaiting` dentro do `PostRound` existente. É assim que "Próxima partida
  em MM:SS" continua vivo em cima da tela de Resultados/Placar sem uma mensagem dedicada.

Uma única tela (`RoomScreen`) despacha para `WaitingScreen`/`MatchScreen`/
`ResultsAndLeaderboardScreen` conforme o `MatchUiState` atual, em vez de rotas de navegação
separadas: as transições entre elas são automáticas (o servidor decide), não uma ação do jogador, e
usar um `when` sobre um `StateFlow` evita disputa entre a próxima mensagem do socket e uma chamada de
`navController.navigate`.

### Rede: `MultiplayerTransport` como fronteira, `MultiplayerSession` como orquestrador

`MultiplayerTransport` é uma interface pequena (`connect`, `incoming(): Flow<ServerMessage>`, `send`,
`close`) implementada por `KtorMultiplayerTransport` (Ktor + engine OkHttp + WebSockets,
`PalavramentoJson` para (de)serializar) e, nos testes, por um fake em memória
(`FakeMultiplayerTransport`). `MultiplayerSession` conhece o fluxo do dossiê 5.3 mas não conhece Ktor:
conecta, roda o handshake de relógio, envia `JoinRoom`, dobra cada mensagem no `MatchStateReducer` e
reconecta com `ReconnectBackoff` (exponencial, capado) ao cair - reenviando o handshake e o
`JoinRoom` inteiros, para que uma reconexão e uma entrada nova percorram exatamente o mesmo caminho,
como o dossiê pede.

Isso é o que faz "o manuseio de mensagens do WS contra um transporte fake" (item 8 do brief) e "o
reducer" serem dois testes JVM diferentes: o reducer testa a lógica de transição isolada; o
`MultiplayerSessionTest` testa que a sessão de fato manda as mensagens certas, na ordem certa,
inclusive depois de uma queda de conexão simulada (`FakeMultiplayerTransport.dropConnection()`).

### Relógio: `ServerClock` puro, ancorado em `elapsedRealtime`, nunca no relógio de parede

`ClockSyncEstimator` guarda a amostra de menor round-trip entre as poucas trocas de `ClockSync` do
handshake (offset = tempo do servidor - ponto médio do round-trip, como o brief pede
explicitamente). `ServerClock(offsetMs, elapsedRealtimeMs: () -> Long).nowMs()` soma esse offset à
leitura injetada de `elapsedRealtime`. A função é injetada (não é uma chamada direta a
`android.os.SystemClock`) só para que a classe não dependa do Android e seja testável na JVM; em
produção, `AppModule` a amarra em `SystemClock.elapsedRealtime()`. Os composables de cronômetro
(`rememberRemainingMs`) leem `ServerClock`, nunca `System.currentTimeMillis()`.

Um detalhe deliberado: `ClientMessage.ClockSync(clientSentAt)` carrega um valor de
`elapsedRealtime()`, não um timestamp de parede. O servidor só ecoa esse valor de volta
(`ClockSyncResponse.clientSentAt`), então ele funciona como um nonce de correlação para casar
pergunta e resposta - o cliente nunca precisa (nem tenta) interpretar `clientSentAt` como um
instante de verdade.

### Gesto: `PathTracer` como máquina de estados pura, Compose só traduz coordenadas

`PathTracer` (em `br.com.colman.palavramento.game`) não importa nada de Compose ou Android: recebe um
`Board` (de `:domain`, só pela adjacência) e um índice lógico por vez (`onTileEntered`), e decide
anexar, desfazer (reentrar no penúltimo tile) ou ignorar. `BoardView` traduz um toque em pixels para
um índice lógico (considerando `Rotation`/`logicalIndexAt` de `:domain`) e delega inteiramente a
decisão ao tracer; a UI nunca reimplementa a regra de adjacência.

O raio de acerto (40% do tile a partir do centro, conforme o brief) é geometria pura sobre o tamanho
medido do `Box` do tabuleiro (`displayIndexAt`), sem depender de posições individuais por tile - a
grade usa `Row`/`Column` com `weight(1f)` uniforme, então a mesma divisão por 4 vale tanto para o
layout quanto para o teste de acerto.

### Testes: pirâmide - reducer/relógio/gesto na JVM, o gesto de novo em Compose instrumentado

`PathTracer`, `ClockSyncEstimator`/`ServerClock`, `ReconnectBackoff` e `MatchStateReducer` têm testes
Kotest na JVM (`app/src/test`), incluindo testes de propriedade para o tracer (nenhuma sequência de
toques produz um caminho inválido; reentrar no penúltimo tile sempre desfaz exatamente um passo).
`MultiplayerSessionTest` cobre o handshake e a reconexão contra `FakeMultiplayerTransport`.
`AppModuleTest` roda `checkModules()` do Koin - mas só sobre `AppModule` (rede/persistência), não
sobre `ViewModelModule`: `LobbyViewModel`/`RoomViewModel` disparam I/O real no `init`, e
`checkModules()` os instancia de verdade fora de qualquer runtime Android, o que na prática abria uma
conexão HTTP real contra `10.0.2.2:8080` durante os testes de unidade (detectado porque poluía o
stderr de testes completamente não relacionados). Separar os módulos resolve isso sem mudar o padrão
usual de ViewModel carregando dados no `init`.

`app/src/androidTest` tem `PathTracerGestureTest` (`br.com.colman.kotest.FunSpec`,
`runAndroidComposeUiTest<ComponentActivity>`) cobrindo anexar/desfazer/rejeitar-não-adjacente
arrastando de verdade sobre `BoardView`, sem ViewModel nem Koin envolvidos. Rodou verde num
dispositivo gerenciado Gradle (`pixel6Api34`, imagem `aosp-atd` API 34) depois de um problema
descoberto durante a fase: a AVD `Pixel_9a` deste ambiente roda uma imagem de sistema muito nova
(preview API 37.1) cujo `InputManager.getInstance()` privado sumiu, e o Espresso usado por
`ui-test-junit4` ainda o procura por reflexão - toda interação de toque derrubava a instrumentação
antes de qualquer asserção rodar. `aosp-atd`/API 34 (o mesmo padrão do projeto de referência, Petals)
não tem esse problema.

## Consequências

- `:domain` não foi tocado; os quatro campos que `PostRound` carrega além do que `RoundEnd` manda
  (`board`, `mutator`, `maxScore`, `maxWords`) são um detalhe de estado do cliente, não do protocolo.
- SQLDelight (dossiê 7, cache local) e o fluxo completo de login/promoção (dossiê 8) ficam para a
  fase 5, como o brief pede; `TokenRepository` já é uma interface pequena para não amarrar essa
  extensão depois.
- A UI está inteiramente em `br.com.colman.palavramento.ui`, sem strings literais (tudo em
  `strings.xml`, com plurals onde fazia sentido) e sem cores hardcoded (paleta nomeada em
  `ui/theme/PalavramentoColors.kt`).
