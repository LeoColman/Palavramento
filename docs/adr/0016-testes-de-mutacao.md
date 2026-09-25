# ADR 0016: Testes de mutação nos três módulos

**Status:** aceita. Os portões de build foram substituídos pela ADR 0023 (2026-09-25): a mutação saiu
do `check` e roda toda semana. Limites, exclusões e o resto desta ADR continuam valendo.

## Contexto

A ADR 0001 colocou o Pitest só em `:domain`, com limite de 80% de mutantes mortos, e o dossiê §10
registra o mesmo. Na prática o `:domain` passou de 80% logo cedo e ficou em 93%, enquanto `:server` e
`:app` cresceram sem nenhuma medida de qualidade de teste: cobertura de linha não distingue um teste
que afirma alguma coisa de um que só executa o código.

A decisão agora é estender a mutação aos três módulos e apertar o limite.

## Decisão

Portões de build, todos dependência de `check`:

| Módulo | Limite | Por quê |
|--------|--------|---------|
| `:domain` | **90%** | JVM puro, tudo testável, já estava em 93% |
| `:server` | **90%** | JVM puro. Testcontainers cobre repositórios e rotas de ponta a ponta |
| `:app` | **50%** | Metade do módulo é Compose e API de Android que só o `androidTest` alcança |

Cada módulo roda o PIT pela linha de comando numa tarefa `JavaExec`, como a ADR 0001 já fazia: o
plugin `info.solidsoft.pitest` continua sem aplicar no Gradle 9.

### `:server`

- O source set `lexiconCompiler` entra junto no alvo: ele não vai no jar, mas tem teste próprio e é
  o código que decide o que é palavra.
- `--timeoutConst=60000`: cada minion sobe o seu próprio Postgres pelo Testcontainers, e o padrão de
  4s do PIT mata minion saudável antes de ele terminar de migrar o banco.
- Minion com `-Xmx1g`, não com os 3 GB que a tarefa `test` usa. O PIT roda `--threads` minions ao
  mesmo tempo, cada um com o seu container, e o `-Xmx` é multiplicado pelo número de threads em cima
  dos 4 GB do daemon do Gradle. A primeira versão desta ADR pedia 3 GB por minion e o OOM killer
  derrubou o build.
- Duas specs ficam fora da **rodada** do PIT (`--excludedTestClasses`), e continuam rodando no `test`:
  - `LexiconLoaderTest` recomputa a regra de colapso sobre os 2M+ registros de `forms.tsv` e precisa
    dos 3 GB. `LexiconLoaderArtifactTest`, escrita para isto, cobre o `LexiconLoader` sem `forms.tsv`.
  - `RealLexiconCalibrationTest` afirma orçamento de tempo de parede ("solve 4x4 em menos de 50 ms").
    Sob a instrumentação do PIT uma asserção de tempo mata mutante porque o minion ficou lento, o que
    é pontuação em que não dá para confiar. Os filtros e parsers que ela toca têm specs próprias.
- Fora do alvo, com motivo: `ApplicationKt` (bootstrap do `embeddedServer`, os testes entram pelo
  `Application.module` via test host), `plugins.*` e `db.DatabaseFactory` (fiação de install/DI/Hikari:
  todo mutante ou muda um default de framework que nenhuma asserção vê, ou quebra tudo de uma vez),
  `db.tables.*` (declarações de coluna do Exposed, sem ramo para mutar, e o teste de migração já
  confere o schema que elas produzem).

### `:app`

O PIT muta a saída Kotlin da variante `debug` e roda contra o classpath da própria `testDebugUnitTest`,
que é de onde vem o `android.jar` mockável. Ficam fora do alvo:

- As telas Compose, o tema e o nav host. O compilador do Compose reescreve cada `@Composable` em
  bookkeeping de grupo e skip cujos ramos um teste de JVM não alcança. O que é testável nelas (o
  gesto de traçado, o countdown, o desenho do tile) está no `src/androidTest`, que roda em aparelho e
  é invisível para o PIT.
- `MainActivity`, `PalavramentoApplication` e `AndroidGameAudio`: pontos de entrada do Android sem
  comportamento a afirmar do lado da JVM.
- O código gerado pelo SQLDelight em `br.com.colman.palavramento.data` (`Database*`, `*Queries*` e os
  três tipos de linha). Os repositórios escritos à mão no mesmo pacote continuam no alvo.

O limite de 50% é metade do dos módulos JVM justamente porque o que sobra no alvo ainda é o que
decide alguma coisa: o reducer, os view models, a sessão, as filas e os repositórios.

## Consequências

- `./gradlew check` fica bem mais lento: a mutação do `:server` roda a suíte de testes uma vez por
  mutante coberto, e parte dela fala com Postgres de verdade. Para o ciclo curto use
  `./gradlew :domain:test`, `:server:test` ou `:app:testDebugUnitTest`.
- Mutante sobrevivente vira decisão: ou o teste que faltava, ou uma exclusão com justificativa neste
  ADR. Não existe terceira opção de "abaixa o limite".
- O dossiê §10 dizia 80% só em `:domain`; foi atualizado para refletir os três portões.
