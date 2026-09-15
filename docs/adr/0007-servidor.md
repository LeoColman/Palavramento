# ADR 0007: Servidor (Ktor, Koin, PostgreSQL, ciclo de rodadas)

**Status:** aceita (fase 3)

## Contexto

A fase 3 (dossiê §3, §5, §6.3, §6.4, §7, §8, §9, §11, §12.4) pede o servidor completo: Ktor + Koin +
Exposed/PostgreSQL, ciclo de rodadas de uma sala global, WebSocket `/ws/multiplayer`, placar e
autenticação. O dossiê fixa a mecânica de jogo e o protocolo (ADR 0005); esta ADR registra as
decisões de implementação que o dossiê deixa em aberto.

## Decisão

### Pilha

Ktor 3.5 (Netty), Koin isolado por `Application` (`install(KoinIsolated)`, não `startKoin` global: o
contexto global do Koin é um singleton por JVM e quebraria testes que sobem várias
`testApplication` na mesma JVM), Exposed 1.x DSL (pacotes `org.jetbrains.exposed.v1.core` e
`org.jetbrains.exposed.v1.jdbc`, renomeados na 1.x), HikariCP, Flyway, PostgreSQL, `at.favre.lib:bcrypt`.

### Ciclo de rodadas (`RoomScheduler`, dossiê §1.4, §12.4)

Uma sala global (`GlobalRoomId = "global"`). O agendador mantém sempre duas rodadas prontas: a
corrente e a próxima, ambas persistidas antes de começar (dossiê §3). Ao ativar uma rodada, o
agendador tira uma foto de quem está conectado (`ConnectionRegistry`) e marca todos como
participantes; só esse conjunto recebe `RoundStart` na ativação e só esse conjunto pode reconectar
durante a rodada. **A regra de quem entra depois de a rodada já estar ativa mudou: ver ADR 0010**
(entrada tardia, decisão do dono do produto de 2026-09-15) para a regra vigente e a exceção de pouco
tempo restante; esta ADR só continua descrevendo o estado no momento da ativação em si.

`RoomScheduler` recebe um `GameClock` injetável. Em produção é `SystemGameClock` (relógio real); a
espera até o próximo instante (`waitUntil`) usa `kotlinx.coroutines.delay` real de qualquer forma, então
um `GameClock` falso não serve para acelerar o laço ao vivo, só para testes de aritmética pura
(`RoundTiming`, testado com `MutableGameClock`) e para testes que ativam uma rodada diretamente via
os métodos de teste `activateForTesting`/`finishForTesting` (visibilidade `internal`, usados só por
`:server:test`) sem rodar o laço de tempo real: assim o teste do limite de tolerância de submissão
tardia e o teste de "aguardar a próxima rodada" não competem com o relógio de verdade.

A janela de fim de rodada foi estendida: o agendador só chama `finish()` (e zera a rodada corrente)
em `endsAt + lateSubmissionTolerance`, não em `endsAt`. Sem isso, uma submissão que chega dentro da
tolerância (mas depois de `endsAt`) quase sempre encontraria a rodada já finalizada e nula, porque a
transição de estado é praticamente instantânea comparada à rede.

**Recuperação de reinício** (dossiê fase 3: "persistir a cada aceite para que um reinício não perca
palavras aceitas"): ao iniciar, `RoomScheduler` busca as rodadas ainda não finalizadas da sala
(`RoundRepository.findPending`, ordenadas por início). Se a mais antiga já estava `ACTIVE` (o
processo caiu no meio dela), o estado em memória é reconstruído a partir de `submissions`: cada
jogador que tinha palavras aceitas volta a ser participante com o conjunto encontrado restaurado.
Como toda conexão WebSocket morre com o processo, essa reidratação só importa quando o jogador
reconecta depois do reinício, o que passa pelo mesmo caminho de `join()` de qualquer reconexão.
Não há recuperação para o caso raro de o processo cair durante a própria finalização (entre `endsAt +
tolerância` e a escrita de `round_results`); é dívida aceita, não um cenário coberto pelos testes.

### Autenticação WebSocket (dossiê §5)

A conexão é anônima até `JoinRoom(sessionToken = <access token>)`. Token ausente ou inválido fecha o
socket com `CloseReason.Codes.VIOLATED_POLICY`. `ClockSync` é respondido a qualquer momento, mesmo
antes do `JoinRoom` (dossiê §5.3: "o cliente sincroniza... antes de entrar"). Qualquer outra mensagem
antes de um `JoinRoom` válido também fecha com violação de política; o dossiê só descreve o caso do
token, mas a mesma regra ("a conexão é anônima até `JoinRoom`") se aplica por extensão.

Se o mesmo jogador abre uma segunda conexão, a mais nova vence: `ConnectionRegistry.register` fecha a
conexão antiga substituída com o código `4000` ("Replaced by a newer connection"). `LeaveRoom`
apenas desregistra a conexão; os resultados de uma rodada já jogada continuam contando (dossiê fase
3 task), porque `RoundState` nunca remove um jogador de `participantIds` nem apaga seu progresso.

### Limite de taxa e tolerância (dossiê §5.2, fase 3 task)

`RateLimiter` é uma janela fixa de 1 segundo por conexão (10 submissões/s por padrão), reiniciada
quando o relógio injetado cruza o limite da janela. Excesso é descartado silenciosamente e logado
(`INFO`), nunca gera `WordRejected`. Submissões fora de `[startsAt, endsAt + tolerância]` também são
descartadas silenciosamente (retorno `null` de `RoomScheduler.submitWord`), distinto de uma rejeição
de validação (que sempre gera `WordRejected` com um motivo).

### Convidados e estatísticas vitalícias (dossiê §8)

`players.is_guest` marca a conta. `round_results` e `submissions` são gravados para convidados e
registrados igualmente (necessário para o placar da rodada e para a promoção manter histórico).
`player_stats` só é escrita para jogadores registrados: `RoundFinalizer` pula a atualização
incremental quando `player.isGuest`. `/players/me/stats` responde `403` para convidados.
`/players/me` sempre responde (convidado ou não); para convidado, nível e XP totais vêm zerados, já
que não há `player_stats` para consultar.

Nome padrão do convidado: `"Convidado " + 4 primeiros caracteres do id em maiúsculas` (dossiê: "nome
curto para o placar ficar legível"). Um `displayName` explícito no corpo de `POST /auth/guest` é
aceito como está, sem moderação. **Moderação de nomes de exibição (dossiê §12.5) é dívida técnica
explícita**, fora do escopo desta fase, igual ao dossiê já registra.

### Promoção (`/auth/register`) e migração (`/auth/login`)

Promoção (dossiê §8: "login... promove o convidado, migrando o histórico"): quando `/auth/register`
é chamado com o access token de um convidado, o mesmo `player.id` é atualizado no lugar
(`is_guest = false`, e-mail e senha preenchidos): nada muda de dono, então nada precisa ser
migrado. `player_stats` é então **construído do zero** a partir de todo o `round_results` existente
daquele jogador (`AuthService.rebuildPlayerStats`), porque rodadas jogadas como convidado, antes da
promoção, nunca tinham gerado uma linha em `player_stats`.

Migração de login (dossiê §8, chamado com o token de um convidado diferente da conta de destino):
**regra de conflito**: para cada rodada que o convidado jogou, se a conta de destino **já tem** um
resultado na mesma rodada, o resultado do convidado é descartado (`round_results` e `submissions`
daquela rodada apagados) e o da conta de destino prevalece; caso contrário, o resultado do convidado
(`round_results` e `submissions`) é reatribuído para a conta de destino. Ao final, a linha do
convidado em `players` é apagada (depois de remover seus `refresh_tokens`, senão a constraint de
chave estrangeira de `refresh_tokens.player_id` barra o `DELETE`) e `player_stats` da conta de
destino é reconstruído do zero, agora refletindo o histórico combinado. Essa regra prioriza a conta
"real" (com senha) sobre a sessão anônima quando os dois jogaram a mesma rodada; não há tentativa de
mesclar pontuações de uma mesma rodada.

Refresh token: rotativo. Cada `/auth/refresh` bem-sucedido revoga o token apresentado e grava o hash
do substituto (`replaced_by_hash`), então apresentar de novo um token já revogado é detectável como
reuso (ex.: token roubado e reproduzido depois que o cliente legítimo já rotacionou); a resposta
nesse caso revoga **toda** a cadeia daquele jogador, não só o token reusado.

### Persistência (ADR 0003 confirmada)

Todo timestamp usa `TIMESTAMPTZ`/`OffsetDateTime` (não `TIMESTAMP` sem fuso), para o valor não
depender do fuso da sessão do banco. Ids são texto (UUID gerado em Kotlin), não `EntityID`/DAO do
Exposed, batendo com os ids `String` que o protocolo já usa (`Rest.kt`, `ServerMessage.kt`). O
esquema completo (`rounds`, `round_words`, `submissions`, `round_results`, `player_stats`, `players`,
`refresh_tokens`) está em `server/src/main/resources/db/migration/V1__init.sql`, com comentário em
cada tabela explicando as colunas que vão além da lista literal do dossiê §7 (`theme_title`,
`theme_subtitle`, `common_min`, `status` em `rounds`; `double_xp` em `round_results`;
`best_word_score`, `games_completed`, `total_xp` em `player_stats`).

`round_results` guarda só `score`, `words`, `rank`, `xp`, `double_xp`, exatamente a lista do dossiê
mais a coluna de XP em dobro (§9: "deixar a coluna pronta", nunca setada em v1). O resto de
`RoundStats` (segundos por palavra, comprimento médio, pontos bônus, pontos médios) é recalculado sob
demanda a partir de `submissions` (`RoundStatsCalculator.compute`), em vez de duplicado: uma fonte só
de verdade para o tempo de cada palavra.

### Testes (`kotest-extensions-testcontainers`)

O catálogo já citava `io.kotest:kotest-extensions-testcontainers`, mas essa biblioteca nunca publicou
uma versão acompanhando a linha principal do Kotest: a última não-milestone é `4.2.0` (2020),
compilada contra uma API de listener do Kotest 4.x incompatível com `kotest-runner-junit5` 6.2.5. A
versão usada é `6.0.0.M4` (milestone mais recente, `JdbcDatabaseContainerExtension`/
`ContainerExtension`, API atual), fixada separadamente no catálogo (`kotest-extensions-testcontainers
= "6.0.0.M4"`) em vez de reusar a versão do Kotest; verificado com um teste de fumaça antes do
resto da suíte ser escrito.

`JdbcDatabaseContainerExtension` com seu modo padrão (`ContainerLifecycleMode.Project`) sobe **um**
PostgreSQL para toda a execução de `:server:test` (dossiê fase 3 task: "compartilhar um container
por spec ou por projeto"). Como as specs compartilham o mesmo banco, cada teste usa um `roomId`
aleatório (`testRoomId()`) para `RoomScheduler` não colidir com o agendador de outra spec rodando em
paralelo na mesma sala, e e-mails/salas com sufixo aleatório evitam colisão de dados únicos entre
specs. O léxico real (~80 MB) é carregado uma vez por JVM de teste (`TestLexicon`, `by lazy`), nunca
por spec.

## Consequências

- Dois designs de recuperação de reinício ficam parciais por decisão consciente (round ativo:
  coberto; crash durante a finalização: não coberto), aceitável para v1, registrado aqui para não
  ser redescoberto como bug.
- A extensão de teste do Testcontainers está em uma versão milestone (`6.0.0.M4`), não uma release
  estável; se o Kotest publicar uma versão 6.x estável dela, vale reavaliar o pin.
- Moderação de nome de exibição (dossiê §12.5) permanece dívida técnica, sem mitigação nesta fase.
