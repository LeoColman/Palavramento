# ADR 0009: Persistência local (SQLDelight) e autenticação no `:app`

**Status:** aceita (fase 5)

## Contexto

A fase 5 (dossiê §7, §8, §9, fase 5 da tabela do §11) pede cache local (perfil, estatísticas
vitalícias, últimas 50 rodadas), convidado persistente com login/registro opcional que promove ou
migra o histórico, e refresh de token. A fase 4 (`:app`) já deixava `data/TokenRepository` mínimo de
propósito ("get/save/clear... para a fase 5 estender sem remodelar quem já depende dele", ADR 0006), e
a fase 6, rodando em paralelo, evitou tocar `data/`, `ui/lobby/` e `network/RestApi`/`KtorRestApi`
exatamente para não colidir com este trabalho (ADR 0008). Esta ADR registra as decisões que o dossiê
deixa em aberto para o cliente.

Quatro achados de um teste ponta a ponta do orquestrador num dispositivo real, nas mesmas telas desta
fase, foram corrigidos junto (dossiê §12: dívida técnica registrada, não comportamento novo
inventado): falha silenciosa no bootstrap do convidado, cronômetro em 00:00 durante "Reconectando...",
colisão de texto nas colunas de Resultados e o rótulo de percentil do Placar.

## Decisão

### Esquema local (SQLDelight 2.3.2, dossiê §7)

Três tabelas, todas em `app/src/main/sqldelight/br/com/colman/palavramento/data/`, banco único
`palavramento.db`:

- `Profile` e `LifetimeStatsCache`: uma linha cada (`id = 0` fixo, `INSERT OR REPLACE`), sempre para
  o jogador atualmente conectado (convidado ou registrado). Colunas simples (`TEXT`/`INTEGER`/`REAL`)
  espelham quase 1:1 `PlayerProfile`/`LifetimeStats` (`Rest.kt`); `isGuest` é um `INTEGER` 0/1 comum,
  não `INTEGER AS Boolean`. Um `ColumnAdapter` só para essa coluna não vale a cerimônia extra em todo
  `Database(...)` de produção e de teste.
- `RoundHistory`: `roundId` (chave primária), `startsAt` (para ordenar/aparar) e `payload`, o
  `RoundHistoryEntry` inteiro serializado em JSON via `PalavramentoJson` (o mesmo `Json` do protocolo:
  `RoundHistoryEntry`, `Tile`, `Mutator`, `RoundStats` e `LabelledWord` já são `@Serializable` para o
  fio). Decompor essas quatro formas em colunas próprias duplicaria o formato do protocolo sem ganho
  de consulta, já que a tela de Histórico sempre lê a entrada inteira.

`HistoryRepository.upsertAll` faz `INSERT OR REPLACE` de cada entrada dentro de uma transação e então
`trimToNewest(MaxCachedRounds = 50)`, que apaga tudo fora das 50 linhas mais novas por `startsAt`.
Isso cobre o "trim to 50" tanto para uma resposta do servidor já limitada a 50 quanto, no teste de
repositório, para uma lista maior inserida de uma vez.

### AGP 9 + SQLDelight 2.3.x (workaround já usado no projeto de referência, Petals)

SQLDelight 2.3.x só suporta a variant API legada do AGP (`sqldelight/sqldelight#5940`, `#6078`); a
raiz do projeto já desativava `android.newDsl`/`android.builtInKotlin` para isso (`gradle.properties`,
fase 4). O `sqldelight {}` do `app/build.gradle.kts` usa `schemaOutputDirectory` +
`verifyMigrations = true` (mesmo padrão do Petals), e um bloco `afterEvaluate` amarra manualmente a
saída de `generate<Variant>DatabaseInterface` em `compile<Variant>Kotlin`, porque KGP 2.x não inclui
fontes geradas registradas via a API Java legada na compilação Kotlin.

Diferença deliberada do Petals: nenhum `SupportSQLiteOpenHelper.Factory` customizado, sem
`com.github.requery:sqlite-android` e sem depender do repositório JitPack que ele exige. O esquema
usa só tipos e operações básicas de SQLite, bem dentro do que o SQLite empacotado no framework já
suporta a partir do `minSdk` 26; `AndroidSqliteDriver(Database.Schema, context, "palavramento.db")`
usa a fábrica padrão.

### Terceiro módulo Koin: `PersistenceModule`

`AndroidSqliteDriver` abre um `SQLiteOpenHelper` de verdade, que não existe fora de um runtime
Android: o mesmo problema que já levou `ViewModelModule` a ficar fora do `AppModuleTest` (ADR 0006,
um view model com I/O real no `init` quebra `checkModules()` puro-JVM). Em vez de esticar essa
exceção, `AndroidSqliteDriver`/`Database`/os repositórios SQLDelight/`AuthController`/`SyncService` e
a `factory` de `MultiplayerSession` (que agora depende de `AuthController` para o token) foram para
um módulo novo, `PersistenceModule` (`di/PersistenceModule.kt`), carregado em
`PalavramentoApplication` junto com `AppModule`/`ViewModelModule`, mas nunca alvo de `checkModules()`.

### Política de sincronização (dossiê §7: "cache é o que a tela lê, funciona offline")

`SyncService.sync()` busca `/players/me`, `/players/me/stats` (só para registrado, convidado recebe
403, ADR 0007) e `/players/me/rounds`, cada chamada por trás de `AuthController.callAuthenticated`
(token válido, mais retry em 401) e envolvida em `runCatching` **independente uma da outra**: uma
falha de rede em qualquer uma não desfaz o que as outras já gravaram, e o cache antigo nunca é apagado
antes de uma escrita nova bem-sucedida, só sobrescrito campo a campo quando a resposta chega. É esse
comportamento, nunca limpar no caminho de erro, que faz "Histórico visível offline" valer tanto para
"nunca sincronizou" quanto para "sincronizou uma vez e depois ficou offline" (`SyncServiceTest`).

Dois gatilhos, ambos no lado do `:app` (dossiê tarefa 2: "após cada `RoundEnd` e quando o lobby
abre"):

- **Lobby**: `LobbyViewModel.refresh()` roda no `init` e de novo a cada `ON_RESUME` do `LobbyScreen`
  (`DisposableEffect` + `LifecycleEventObserver`, mesmo padrão que `RoomScreen` já usava para
  pausa/retomada, ADR 0008). Cobre tanto a abertura inicial quanto o retorno de Login/Histórico.
- **Sala**: `RoomViewModel` observa `MultiplayerSession.state` e chama `syncService.sync()` na
  primeira vez que um `roundId` novo aparece como `MatchUiState.PostRound` (um `RoundEnd` de
  verdade), não a cada atualização de `Leaderboard` sobre a mesma rodada (que só copia o `PostRound`
  existente, `MatchStateReducer.onLeaderboard`). Um `lastSyncedRoundId` evita ressincronizar à toa.

### Refresh de token e recuperação (dossiê §8)

`AuthController.validAccessToken()` é proativo: se `accessTokenExpiresAt` está a menos de 60 s (mesma
ordem de grandeza de folga usada alhures no projeto) do agora injetável (`nowMs`, mesmo padrão de
`ServerClock`), chama `/auth/refresh` antes de qualquer coisa. `callAuthenticated` cobre o caso
reativo: se a chamada em si volta 401 (relógio dessincronizado, token revogado entre a checagem e o
uso), atualiza e tenta de novo **uma vez**. `MultiplayerSession.accessTokenProvider` (antes lendo
`TokenRepository` direto) agora chama `authController.validAccessToken()`, então o handshake de
`JoinRoom` (dossiê §5.3) ganha o mesmo refresh proativo sem precisar de um caminho de retry próprio:
uma reconexão que falha por token expirado já tenta de novo no próximo turno do laço de backoff
existente (ADR 0006), com um token fresco.

Quando o refresh token é rejeitado (`/auth/refresh` responde 401): para um convidado,
`AuthController` cria um convidado novo, limpando o cache antes, já que o histórico local era de
outra identidade; para um jogador registrado, limpa a sessão e devolve `null`. Quem chama
(`LobbyViewModel`, `MultiplayerSession`) simplesmente fica sem token válido, e a tela de lobby mostra
o estado de erro com "Tentar novamente" (mesmo caminho de uma falha de rede comum), que aqui funciona
como o "peça para logar de novo" do brief: sem uma tela de login forçada dedicada, mas sem inventar um
fluxo de sessão paralelo, já que "Entrar" já está sempre visível para um convidado (o que a conta
acabou de virar). `Throwable.responseStatus()` distingue rejeição
(`ClientRequestException`/`ResponseException` com `HttpStatusCode`) de falha de rede via
`expectSuccess = true`, ligado explicitamente em `HttpClientFactory` (não confiar no default da
versão do Ktor).

### Promoção e migração no cliente (ADR 0007 decide o servidor; isto é só o lado do app)

`AuthController.register`/`login` sempre mandam o token do convidado atual, se houver
(`currentGuestToken`), deixando o servidor decidir promoção vs. migração (ADR 0007). Em qualquer
sucesso, o cliente limpa `ProfileRepository`/`HistoryRepository` antes de retornar: a identidade pode
ter mudado de dono (login migrando para uma conta existente) ou de natureza (convidado virando
registrado), então mostrar o cache antigo por um instante antes do próximo `sync()` misturaria dado de
uma identidade com o rótulo de outra. `LoginScreen` fecha e volta ao Lobby em caso de sucesso, cujo
`ON_RESUME` já dispara o `refresh()`/`sync()` que repopula tudo do zero.

### Nível 1, não Nível 0, para XP zero (bug de exibição real, corrigido no servidor)

`LevelCurve` (`:domain`) é 0-indexada de propósito: `xpForLevel(0) == 0`, `levelForXp(0) == 0`, a
contagem de quantos limiares `100 * n^1.5` já foram cruzados, e os testes existentes
(`LevelCurveTest`) dependem exatamente disso. O cabeçalho do lobby (dossiê §6.1), porém, mostra um
nível como a maioria dos jogos mostra: 1-indexado, "Nível 1" para um jogador que nunca cruzou limiar
nenhum. `PlayerRoutes.kt` (`:server`) agora soma 1 só na fronteira REST: `level = thresholdsCrossed +
1`, `xpForNextLevel = xpForLevel(level)`. Um convidado novo responde `level = 1`, `totalXp = 0`,
`xpForNextLevel = 100`, em vez do `level = 0` anterior. `LevelCurve` em si não mudou: a curva
matemática do dossiê §9 continua 0-indexada, só a exibição ganhou o deslocamento de 1.
`PlayerRoutesTest` foi atualizado para o novo valor esperado.

### Insets de tela cheia (targetSdk 36, bug visual real)

Nenhuma tela aplicava `WindowInsets`; com `targetSdk` 36 o conteúdo já desenha embaixo das barras do
sistema por padrão, e o cabeçalho do lobby (entre outros) ficava atrás da barra de status num
aparelho real. Todo `Composable` de tela de nível superior (Lobby, Waiting, Match,
Results/Leaderboard, History, Login, About, além dos estados "Conectando"/"Erro de conexão" da Sala)
ganhou `.windowInsetsPadding(WindowInsets.safeDrawing)` no container raiz, sem `Scaffold`, para não
reescrever a estrutura de cada tela só por causa disso.

### Achados extras do orquestrador, mesmas telas

- **Bootstrap silencioso**: `LobbyViewModel.refresh()` agora loga (`Log.w`) toda falha de bootstrap
  ou sincronização e expõe `LobbyUiState.loadError`; a tela mostra uma mensagem em pt-BR com botão
  "Tentar novamente" em vez de deixar "Jogar" levar a um "Conectando..." que nunca resolve.
- **Cronômetro em 00:00 ao reconectar**: `MultiplayerSession.run()` não zera mais `clock` a cada
  queda; `tryConnectAndHandshake` só sobrescreve quando o novo handshake de fato produz um offset,
  então o relógio anterior (e a contagem regressiva) sobrevive à janela de "Reconectando...".
- **Erro de conexão em vez de spinner infinito**: `MultiplayerSession` expõe `connectionAttempts`
  (tentativas seguidas desde o último sucesso); `RoomScreen` troca o indicador de "Conectando..." por
  uma tela de erro com "Tentar novamente" depois de `ConnectionErrorThreshold = 3` falhas seguidas na
  primeira conexão. Cobre tanto "servidor inalcançável" quanto "não foi possível obter um token"
  (quando `accessTokenProvider` devolve `null`), sem um segundo texto de erro dedicado a cada causa.
- **Colisão de texto nas colunas de Resultados**: `ResultsScreen`'s `WordColumn` agora reserva uma
  largura fixa para a pontuação (`ScoreSlotWidth`) e deixa a palavra truncar com reticências
  (`TextOverflow.Ellipsis`, `maxLines = 1`) em vez de sobrepor.
- **Rótulo de percentil**: o percentil do dossiê §6.4 é a fração de jogadores batidos
  (`(N - rank) / (N - 1) * 100`), não uma faixa "Top N%". O texto virou "Percentil %1$d" em
  `strings.xml`, sem mudar o cálculo em `:domain`.

## Consequências

- `AppModuleTest` continua cobrindo só `AppModule`; `PersistenceModule` (como `ViewModelModule`) não
  é verificável fora de um runtime Android e não tem `checkModules()`, coberto por
  `AuthControllerTest`/`SyncServiceTest`/os testes de repositório em vez disso.
- `TokenRepository` não ganhou métodos novos (a interface do brief da fase 4 já bastava);
  `AuthController` é a camada nova por cima dela. `RestApi`/`KtorRestApi` ganharam `register`, `login`
  e `roundHistory`, todos seguindo o padrão existente (devolver o corpo de sucesso ou lançar), sem um
  tipo de resultado paralelo por chamada.
- `:domain` não foi tocado; a curva de nível continua 0-indexada, de propósito. O único ajuste de
  regra ficou inteiramente em `:server` (`PlayerRoutes.kt`), mais o teste correspondente.
- `docs/adr/0006-arquitetura-do-app.md` já previa este trabalho ("SQLDelight... e o fluxo completo de
  login/promoção ficam para a fase 5"); nenhuma decisão de arquitetura da fase 4/6 foi revertida, só
  estendida.
