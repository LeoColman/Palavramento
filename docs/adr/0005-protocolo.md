# ADR 0005: Protocolo cliente-servidor

**Status:** aceita (fase 2)

## Contexto

O dossiê (§5) define o formato geral do protocolo `/ws/multiplayer`: WebSocket, mensagens
`kotlinx.serialization` com discriminador de tipo, e duas tabelas de payload (servidor -> cliente,
cliente -> servidor). Três pontos da prosa do dossiê, fora das tabelas, exigem campos que as tabelas
não listam, e a fase 2 (`:domain`) precisou fechar esse gap para poder escrever os DTOs e os testes
de round-trip do §10.

## Decisão

### Configuração de serialização

`PalavramentoJson` (`domain/.../protocol/PalavramentoJson.kt`) é o único `Json` usado para todas as
mensagens: `classDiscriminator = "type"`, `ignoreUnknownKeys = true` (um peer numa versão de
protocolo diferente não derruba a conexão) e `encodeDefaults = true` (um campo novo com valor padrão
nunca desaparece silenciosamente na serialização).

### Sincronização de relógio (adição)

O §5.3 exige que "o cliente sincroniza offset com o servidor no handshake e nunca usa o relógio
local para o cronômetro", mas a tabela do §5 não lista nenhuma mensagem para isso. Adicionado o par:

- `ClientMessage.ClockSync(clientSentAt: Long)`
- `ServerMessage.ClockSyncResponse(clientSentAt: Long, serverTime: Long)`

O cliente calcula o offset a partir do round-trip: envia `clientSentAt`, recebe de volta o mesmo
valor mais `serverTime`, e usa a diferença entre `serverTime` e o ponto médio do round-trip como
offset do relógio local. A fase 3 decide quando disparar essa troca (handshake da conexão, ou antes
de cada `RoundStart`).

### Token de reconexão em `JoinRoom` (adição)

O §5.3 diz que, na reconexão, "o cliente reenvia `JoinRoom` com o token", mas a tabela do §5.2 só
lista `languageCode`. Adicionado `sessionToken: String? = null` em `JoinRoom`, nulo para uma sessão
de convidado nova e preenchido para identificar a sessão existente numa reconexão.

### Estado de reconexão em `RoundStart` (adição)

O §5.3 exige que, ao reconectar no meio de uma rodada, o servidor "reenvia o estado da rodada
corrente incluindo palavras já aceitas". Em vez de criar uma mensagem de resync separada, `RoundStart`
ganhou três campos com valor padrão:

- `alreadyFound: List<FoundWord> = emptyList()`
- `runningScore: Int = 0`
- `runningWords: Int = 0`

Reaproveitar `RoundStart` (em vez de uma `RoundResync` nova) significa que um cliente que entra no
horário e um cliente que reconecta no meio passam pelo mesmo caminho de código do lado do cliente;
os três campos ficam vazios/zerados no caso comum e `encodeDefaults = true` garante que eles sempre
aparecem no JSON, mesmo vazios.

### `RejectionReason` como tipo de domínio, não só de protocolo

Os motivos de rejeição (`INVALIDA`, `JA_ENCONTRADA`, `CAMINHO_INVALIDO`, `CURTA`,
`BLOQUEADA_POR_MUTADOR`) vivem em `br.com.colman.palavramento.domain.submission.RejectionReason`,
não em `protocol`: é o mesmo enum que `SubmissionValidator` retorna e que `WordRejected` serializa,
para que validação e protocolo nunca divirjam. Os tokens de fio exigidos pelo dossiê entram via
`@SerialName` em cada constante; os identificadores Kotlin (`NotAWord`, `AlreadyFound`,
`InvalidPath`, `TooShort`, `BlockedByMutator`) seguem a convenção do projeto de nomes em inglês.

### Exclusão do pacote `protocol` do Pitest

`domain/build.gradle.kts` exclui `br.com.colman.palavramento.domain.protocol.*` do alvo do Pitest.
São DTOs simples (sem branching além do que o compilador gera para `data class`), cobertos por
testes de round-trip de serialização; mutantes em código gerado (`equals`/`hashCode`/`copy`,
inicializadores de campo com valor padrão) inflariam artificialmente a meta de 80% sem indicar nada
sobre a qualidade real dos testes.

## Consequências

- O protocolo tem 4 campos e 1 par de mensagens além do que o dossiê lista literalmente; todos
  documentados aqui, nenhum inventa comportamento de jogo novo (dossiê §12.3 pede cautela nesse
  sentido).
- A fase 3 (`:server`) decide o mecanismo real de sessão por trás de `sessionToken` (JWT, id
  opaco, etc.) e o momento exato de disparar `ClockSync`; esta ADR só fixa o formato de fio.
