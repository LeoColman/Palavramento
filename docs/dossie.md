# Palavramento — Dossiê de Especificação

**Destinatário:** agente Opus orquestrador (subagentes Sonnet para execução).
**Objetivo:** implementar um clone do Wordament em português, chamado **Palavramento**, com **apenas o modo Multiplayer** na v1, servido por backend próprio.

---

## 0. Restrições de projeto (não negociáveis)

| Item | Decisão |
|---|---|
| Nome / applicationId | Palavramento / `br.com.colman.palavramento` |
| Licença | AGPL-3.0-or-later (aplica-se a app e servidor) |
| Cliente | Android-only, Kotlin + Jetpack Compose |
| Backend | Kotlin/JVM, Ktor |
| Persistência cliente | SQLDelight |
| Persistência servidor | PostgreSQL (SQLDelight ou Exposed — ver §7) |
| DI | Koin (cliente e servidor) |
| Testes | Kotest + testes de propriedade + testes de mutação (Pitest) |
| Lint | Detekt, falha o build |
| Módulos Gradle | no máximo 3 (ver §4) |
| Léxico | Hunspell pt_BR empacotado |
| Escopo v1 | somente Multiplayer (sem Adventure, Daily Challenge, Quick Play) |

Autoridade: **o servidor é a única fonte de verdade**. O cliente nunca decide validade de palavra, pontuação, ranking ou início/fim de rodada.

---

## 1. Mecânica do jogo (derivada do original)

### 1.1 Grade
- 4×4 = 16 tiles. Cada tile tem **uma letra** (ou dígrafo, §1.6) e um **valor em pontos** exibido no canto superior esquerdo.
- Caminho válido: sequência de tiles **adjacentes em 8 direções** (ortogonal + diagonal), sem repetir o mesmo tile na mesma palavra.
- Comprimento mínimo da palavra: **3 letras**.
- A mesma palavra só pontua uma vez por rodada, independentemente do caminho.
- Botão **Girar**: rotaciona a grade 90° apenas visualmente. Não altera índices lógicos nem pontuação. É conveniência de traçado, não mutador.

### 1.2 Pontuação
**Pontuação da palavra = soma dos valores dos tiles do caminho.** Sem bônus de comprimento.

Verificação contra a rodada das capturas (grade `L O A R / M I C T / P V R I / E O S M`, valores `10 2 1 2 / 3 2 4 4 / 5 6 2 2 / 1 2 1 3`):

```
limo = 10+2+3+2 = 17
loâ  = 10+2+1   = 13
via  =  6+2+1   =  9
pilo =  5+2+10+2= 19
moiâ =  3+2+2+1 =  8
moi  =  3+2+2   =  7
                  ---
                   73  → confere com "Points: 73/4193"
```

O denominador (`/4193`, `/272`) é o **máximo teórico da grade**: soma da pontuação de todas as palavras válidas encontráveis e sua contagem. Calculado pelo solver do servidor na geração da grade (§3).

### 1.3 Valores das letras (base)
Derivados da frequência em pt-BR. Tabela base proposta; deve ser um recurso de dados versionado, não constantes espalhadas:

| Valor | Letras |
|---|---|
| 1 | A, E, S, O* |
| 2 | I, O, R, U, N, D |
| 3 | M, T*, L*, C* |
| 4 | C, T, B, G |
| 5 | P, F, H |
| 6 | V, Z |
| 8 | J, X |
| 10 | K, Q, W, Y |

\* A tabela real deve ser calibrada contra a frequência do próprio léxico (§2.3) na fase 1; os valores acima são ponto de partida. Mutadores sobrescrevem valores individuais.

### 1.4 Rodada e cadência
- Duração da rodada: **120 segundos**, cronômetro regressivo.
- Intervalo entre rodadas: **60 segundos** ("Próxima partida em 00:43"), durante o qual a tela de Resultados/Placar fica visível.
  **Alterado para 25 segundos** (decisão do dono do produto, 2026-09-15): padrão de `INTERMISSION_DURATION_SECONDS` no servidor.
- A cadência é **global por sala**: todos os jogadores da mesma sala jogam a mesma grade, começando e terminando no mesmo instante (relógio do servidor).
- Entrar no meio de uma rodada: o jogador aguarda na tela de resultados/espera e entra na próxima. Não há entrada tardia.
  **Regra alterada pela ADR 0010** (decisão do dono do produto, 2026-09-15): entrada tardia passou a ser permitida, com exceção de pouco tempo restante.

### 1.5 Temas / mutadores
Cada rodada tem um tema exibido no cabeçalho, em duas linhas: **nome do mutador** e **restrição da grade**. Ex.: "L de alto valor" / "19 palavras comuns".

Mutadores da v1 (enum fechado, extensível):
- `SEM_MUTADOR` — grade padrão.
- `LETRA_VALIOSA(letra, valor)` — uma letra recebe valor inflado (ex.: L = 10).
- `LETRA_PROIBIDA(letra)` — palavras contendo a letra não pontuam (exibida em cinza).
- `TAMANHO_MINIMO(n)` — mínimo sobe de 3 para n.
- `SO_SUBSTANTIVOS` — **fora da v1** (exige léxico anotado por classe gramatical).

A segunda linha ("19 palavras comuns") é a **restrição de geração**: a grade é gerada até conter pelo menos N palavras da faixa "comum". N é parte do descritor da rodada e deve ser exibido literalmente.

**Conjunto de mutadores alterado pela ADR 0012** (decisão do dono do projeto, 2026-09-15): `LETRA_PROIBIDA` e `TAMANHO_MINIMO` foram removidos (o comprimento mínimo volta a ser sempre 3 letras, sem override); dois mutadores novos entraram no lugar, `DIGRAFOS` e `LETRA_NOS_CANTOS`. Ver a ADR para a definição de cada um, a tabela de dígrafos e a migração dos dados antigos.

### 1.6 Normalização e dígrafos
- Os tiles contêm letras **sem acento**. Palavras acentuadas do léxico casam com o caminho após remoção de diacríticos: `loâ → LOA`, `moiâ → MOIA`, `ç → C`. A forma **exibida** ao jogador é a forma canônica acentuada do léxico.
- Se duas formas do léxico colapsam na mesma forma normalizada (`pais`/`país`), elas são **a mesma entrada de pontuação**; exibir a de maior frequência.
- Tiles de **dígrafo** (`QU`, `ÃO`, `NH`, `LH`) devem ser suportados pelo modelo desde o início (tile = `String`, não `Char`), mesmo que a geração da v1 emita apenas tiles de uma letra. Regra: um tile de dígrafo consome todas as suas letras de uma vez.

### 1.7 Classificação comum / especialista
Cada palavra da grade é rotulada:
- **Comum** — está entre as N palavras mais frequentes do corpus de frequência (§2.3). Exibida em fonte normal.
- **Especialista** — válida no Hunspell mas abaixo do corte. Exibida em *itálico*.

O corte é um parâmetro de configuração do servidor, não hardcoded. Calibrar para que a proporção comum:especialista fique próxima de 1:1 em grades típicas (na rodada de referência: 127 comuns / 145 especialistas).

---

## 2. Léxico

### 2.1 Fonte
Hunspell pt_BR (projeto VERO / LibreOffice), empacotado. **Verificar e documentar a licença** (LGPL/MPL) e sua compatibilidade com AGPL na distribuição — tarefa explícita da fase 0, com o resultado registrado em `LICENSES.md`.

### 2.2 Expansão
Hunspell é `.dic` + `.aff` (afixos). É necessário **expandir para lista plena de formas** em tempo de build (`unmunch` ou expansão própria), gerando:

```
forma_canonica \t forma_normalizada \t frequencia_rank
```

Filtrar: formas com hífen, apóstrofo, abreviaturas, formas com menos de 3 letras.

### 2.3 Frequência
Hunspell não traz frequência. Usar uma lista de frequência pt-BR de licença permissiva (ex.: corpus Leipzig ou OpenSubtitles pt-BR). Palavras válidas ausentes da lista de frequência recebem rank = ∞ (especialista).
**Ponto de risco:** se nenhuma lista de licença aceitável for encontrada, fallback é derivar frequência aproximada por comprimento + frequência de letras. Decidir na fase 0 e documentar.

### 2.4 Estrutura em runtime (servidor)
Trie (DAWG) carregada em memória a partir de um artefato binário pré-compilado no build. O solver percorre a grade em DFS podando pelo prefixo da trie. Requisito de desempenho: **solve completo de uma grade 4×4 em < 50 ms**, medido em teste.

### 2.5 Cliente
O cliente **não** embarca o léxico na v1 — validação é do servidor. O cliente só recebe a lista de palavras da grade no fim da rodada.

**Nota (ADR 0014, decisão do dono do produto, 2026-09-15):** o cliente passou a receber a solução
completa da rodada (`RoundStart.validWords`) desde o início dela, não só no fim, para validar
localmente com o mesmo `SubmissionValidator` do servidor e mostrar o veredito de uma palavra sem
esperar a viagem de ida e volta até o servidor. O servidor continua sendo a única fonte de verdade:
ver a ADR 0014 para o que muda e o que não muda.

---

## 3. Geração de grade

Algoritmo, no servidor, por rodada:

1. Sortear distribuição de letras ponderada pela frequência de letras em pt-BR (não uniforme — evita grades sem vogais).
2. Aplicar mutador (valores de tile).
3. Rodar o solver completo → conjunto de palavras, pontuação máxima, contagem, separação comum/especialista.
4. Aceitar a grade se: `palavras_comuns ≥ N_min` (default 15), `pontuação_máxima ∈ [2500, 6000]`, `palavras_totais ≥ 150`.
5. Caso contrário, regenerar (limite de 200 tentativas; depois relaxar critérios em 10% e repetir).

A grade + solução completa é persistida antes do início da rodada. Rodadas são **pré-geradas** com pelo menos uma de antecedência, para que o início nunca dependa do tempo de geração.

Determinismo: a geração recebe uma seed; mesma seed ⇒ mesma grade. Isso é requisito para os testes e para reprodução de bugs.

---

## 4. Estrutura do repositório (3 módulos)

```
palavramento/
├── domain/       Kotlin/JVM puro, sem Android, sem Ktor
│   ├── Tile, Board, Path, Word, WordTier, Mutator
│   ├── Solver (DFS + trie), ScoreCalculator, BoardGenerator
│   └── Protocolo (DTOs kotlinx.serialization compartilhados)
├── server/       Ktor + Koin + PostgreSQL. Depende de :domain
└── app/          Android + Compose + Koin + SQLDelight. Depende de :domain
```

Regra: **toda lógica de regra de jogo vive em `:domain`**, que é compartilhado. O cliente usa o solver apenas para renderização pós-rodada, nunca para decidir pontuação.

---

## 5. Protocolo cliente-servidor

WebSocket em `/ws/multiplayer`, mensagens kotlinx.serialization com discriminador de tipo. REST apenas para auth e histórico.

### 5.1 Servidor → cliente

| Mensagem | Payload |
|---|---|
| `LobbyState` | `nextRoundStartsAt` (epoch ms), `playersWaiting` |
| `RoundStart` | `roundId`, `board` (16 tiles: letra + valor), `mutator`, `themeTitle`, `themeSubtitle`, `maxScore`, `maxWords`, `startsAt`, `endsAt` |
| `WordAccepted` | `word`, `score`, `runningScore`, `runningWords` |
| `WordRejected` | `reason` (INVALIDA, JA_ENCONTRADA, CAMINHO_INVALIDO, CURTA, BLOQUEADA_POR_MUTADOR) |
| `RoundEnd` | stats do jogador (§6.2), lista completa de palavras da grade rotulada e ordenada por pontuação, marcando as encontradas |
| `Leaderboard` | lista ordenada `rank, nome, score, words`, mais `percentil` do jogador |

### 5.2 Cliente → servidor

| Mensagem | Payload |
|---|---|
| `JoinRoom` | `languageCode` (fixo `pt-BR` na v1) |
| `SubmitWord` | `roundId`, `path: List<Int>` (índices 0..15, em ordem), `clientTimestamp` |
| `LeaveRoom` | — |

O cliente envia **o caminho, não a palavra**. O servidor reconstrói a string, valida adjacência, ausência de reuso, comprimento, mutador, léxico e duplicidade, e só então pontua. Submissões após `endsAt` (com tolerância de 500 ms para latência) são descartadas.

### 5.3 Robustez
- Reconexão: o cliente reenvia `JoinRoom` com o token; o servidor reenvia o estado da rodada corrente incluindo palavras já aceitas.
- Rate limit: máximo 10 `SubmitWord`/segundo por conexão.
- Relógio: o cliente sincroniza offset com o servidor no handshake e **nunca** usa o relógio local para o cronômetro.

---

## 6. Telas

### 6.1 Lobby (equivale à tela 1)
- Cabeçalho: nível + XP, nome do jogador (ou "Convidado"), avatar.
- Painel **Estatísticas** (vitalícias): pontuação total, palavras totais, melhor pontuação de partida, melhor palavra, partidas completas, pontuação média, contagem média de palavras, pontuação média por palavra, melhor colocação, partidas jogadas. Para convidado, o painel aparece esmaecido com convite ao login.
- Seletor de idioma — presente mas travado em Português na v1.
- Botão **Jogar** → entra na sala e aguarda a próxima rodada.

### 6.2 Partida (tela 2)
- Cabeçalho: voltar, ajustes, título e subtítulo do tema.
- Cronômetro regressivo grande (formato `MM:SS` com dígitos estilo flip).
- Faixa: `Pontos: X/max` e `Palavras: Y/max`.
- Grade 4×4, tiles quadrados com valor no canto superior esquerdo.
- **Traçado por arrasto contínuo**: `pointerInput` com `detectDragGestures`; ao entrar no raio de um tile adjacente ao último, anexa; ao voltar sobre o penúltimo, remove o último (undo natural). Soltar submete.
- Área abaixo da grade exibe a palavra em construção com sua pontuação parcial, e as últimas palavras aceitas/rejeitadas com feedback (cor + haptics).
- Botão **Girar**.

### 6.3 Resultados (tela 3)
Aba 1, com contador "Próxima partida em MM:SS" no topo:
- Miniatura da grade.
- Painel de estatísticas da rodada:
  - `Pontos: 73/4193`
  - `Palavras: 6/272`
  - `Segundos por palavra` = (instante da última palavra aceita − início) / nº palavras, 1 casa decimal.
  - `Comprimento médio` das palavras encontradas.
  - `Pontos bônus` — reservado para mutadores que concedem bônus (0 quando não há). Não entra em `Pontos`.
  - `Pontos médios` = pontos / palavras.
  - `XP` ganho na rodada.
- Três colunas: **Todas / Encontradas**, **Não encontradas — Comuns**, **Não encontradas — Especialistas**, cada uma ordenada por pontuação decrescente, com a pontuação à esquerda. Especialistas em itálico, comuns em fonte normal. Colunas com rolagem independente.

### 6.4 Placar (tela 4)
- Faixa fixa com a posição do próprio jogador: rank, nome, pontuação, palavras, percentil.
- Percentil = `(N − rank) / (N − 1) × 100`, arredondado para baixo; `0%` quando `N = 1`.
- Tabela "Jogadores Top" com rank, nome, pontuação, palavras; a linha do próprio jogador destacada.

### 6.5 Estilo
Paleta escura, azul para a partida e vinho para os resultados, tiles laranja — ver capturas de referência. Suporte a tema claro **fora do escopo**. Textos 100% em pt-BR, em `strings.xml`, sem strings literais na UI.

---

## 7. Dados

### Servidor (PostgreSQL)
```
players(id, display_name, is_guest, auth_provider, created_at)
rounds(id, room_id, seed, board_json, mutator, max_score, max_words, starts_at, ends_at)
round_words(round_id, word, score, tier, path_json)     -- solução pré-calculada
submissions(id, round_id, player_id, word, score, accepted_at)
round_results(round_id, player_id, score, words, rank, xp)
player_stats(player_id, total_score, total_words, best_game_score, best_word, games_played, best_rank)
```
`player_stats` é mantido incrementalmente ao fim de cada rodada, em transação junto com `round_results`.

Acesso a dados: **Exposed** no servidor (SQLDelight tem suporte a Postgres ainda imaturo). Se o agente preferir SQLDelight por simetria com o cliente, deve justificar em ADR e provar o driver em spike antes.

### Cliente (SQLDelight)
Cache de: perfil, estatísticas vitalícias, histórico das últimas 50 rodadas com a grade e as palavras. Permite revisitar resultados offline. Nenhuma criptografia adicional.

---

## 8. Autenticação
- **Convidado** por padrão: id anônimo persistente gerado no primeiro uso, guardado em DataStore; sem estatísticas vitalícias no servidor além do necessário para a rodada.
- Login opcional (e-mail/senha ou OIDC) que promove o convidado, migrando o histórico.
- Token JWT de curta duração + refresh. Tudo sobre TLS.

---

## 9. XP e níveis
- XP por rodada = `floor(pontos / 5) + 5 × (palavras encontradas ≥ 10 ? 1 : 0)`. Fórmula é parâmetro de configuração.
- Curva de nível: `xp_para_nivel(n) = 100 × n^1.5`.
- **"Get Double XP!"** (anúncio recompensado) está **fora da v1** — deixar a coluna e o cálculo prontos, sem SDK de anúncios.

---

## 10. Testes

Obrigatório, com Kotest:
- **Solver**: testes de propriedade (Kotest property) — toda palavra retornada tem caminho válido de adjacência sem reuso; nenhuma palavra válida alcançável é omitida (verificado contra implementação ingênua de referência em grades 3×3).
- **Pontuação**: a rodada de referência das capturas vira um teste de regressão fixo (grade, 6 palavras, 73 pontos, média 12.2, comprimento médio 3.5).
- **Geração**: dada uma seed, a grade é determinística; toda grade aceita satisfaz os critérios da §3.
- **Normalização**: `loâ`, `moiâ`, `ação`, `pêssego` casam com caminhos sem acento; `ç → c`.
- **Protocolo**: serialização round-trip de todas as mensagens.
- **Servidor**: testes de integração com Testcontainers (Postgres) cobrindo ciclo completo de rodada, reconexão no meio da rodada, submissão após o fim, submissão duplicada.
- **Mutação**: Pitest sobre `:domain`, mínimo **80% de mutantes mortos**, falhando o build abaixo disso.
- **UI**: testes de Compose para o gesto de traçado (anexar, desfazer ao retroceder, rejeitar não adjacente).

Detekt com configuração estrita, sem supressões sem comentário justificando.

---

## 11. Fases de execução (para os subagentes)

| Fase | Entrega | Critério de aceite |
|---|---|---|
| 0 | Auditoria de licenças (Hunspell, lista de frequência), `LICENSES.md`, esqueleto Gradle 3 módulos, Detekt, CI | `./gradlew check` verde em repositório vazio de lógica |
| 1 | Pipeline do léxico: expansão Hunspell → artefato binário de trie + ranks de frequência | Trie carrega em < 1 s; consultas verificadas contra amostra de 1000 palavras |
| 2 | `:domain` — modelo, solver, pontuação, mutadores, gerador | Solve < 50 ms; testes de propriedade e o teste de regressão da §10 passando; Pitest ≥ 80% |
| 3 | `:server` — Ktor, Koin, Postgres, ciclo de rodadas, WebSocket, placar | Testes de integração cobrindo os cenários da §10; duas conexões simuladas jogando a mesma rodada |
| 4 | `:app` — Compose: lobby, partida, resultados, placar; gesto de traçado; sincronização de relógio | Partida completa ponta a ponta contra servidor local |
| 5 | SQLDelight local, auth de convidado + login, estatísticas vitalícias | Histórico visível offline; promoção de convidado preserva histórico |
| 6 | Polimento: animações dos tiles, haptics, cronômetro flip, tratamento de rede instável | Reconexão no meio da rodada restaura o estado sem perder palavras |

Cada fase abre PR próprio com ADR quando houver decisão estrutural.

---

## 12. Pontos em aberto (decidir na fase 0, documentar)

1. **Licença da lista de frequência** — bloqueante para a separação comum/especialista (§2.3).
2. **Semântica exata de "N palavras comuns"** no subtítulo do tema: adotada aqui como restrição de geração (mínimo garantido). Se o original significar outra coisa, isso é uma divergência deliberada e aceitável.
3. **"Pontos bônus"** aparece como 30 nas capturas sem entrar no total de 73. A origem é desconhecida; a spec o define como campo separado alimentado apenas por mutadores. Não inventar comportamento além disso.
4. **Salas/matchmaking**: a v1 usa uma sala global única. Segmentação por nível ou região só quando houver volume.
5. **Moderação de nomes de exibição** — necessária antes de qualquer lançamento público; fora da v1 técnica, mas registrar como dívida.

---

## 13. Não-objetivos da v1
Adventure, Daily Challenge, Quick Play, anúncios, compras, chat, amigos, outros idiomas, tema claro, iOS, grades diferentes de 4×4.
