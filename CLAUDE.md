# Palavramento

Clone do Wordament em português, só modo Multiplayer na v1. A especificação completa é o dossiê em
`docs/dossie.md`; em caso de dúvida, ele manda. Decisões estruturais ficam em `docs/adr/`.

## Módulos

- `:domain`: Kotlin/JVM puro (sem Android, sem Ktor). Toda regra de jogo: tiles, grade, caminho,
  solver, pontuação, mutadores, gerador, léxico em memória e DTOs de protocolo (kotlinx.serialization).
- `:server`: Ktor + Koin + Exposed/PostgreSQL. Única fonte de verdade para validade de palavra,
  pontuação, ranking e início/fim de rodada.
- `:app`: Android, Jetpack Compose, Koin, SQLDelight. Nunca decide pontuação.

Não criar módulos novos: o limite é 3.

## Convenções

- Identificadores em inglês. Textos de UI em pt-BR, sempre em `strings.xml`, nunca literais no código.
  Documentação (README, ADR, LICENSES) em pt-BR. Comentários de código em inglês.
- Valores de fio que o dossiê nomeia (`SEM_MUTADOR`, `INVALIDA`, `JA_ENCONTRADA`...) entram como
  `@SerialName`, não como nomes de identificador.
- Cabeçalho em todo arquivo Kotlin:
  ```
  // SPDX-License-Identifier: AGPL-3.0-or-later
  // Copyright (C) 2026 Leonardo Colman Lopes
  ```
- Indentação de 2 espaços, linha máxima de 120.
- Nunca usar travessão (em dash, U+2014) em código, comentário, doc ou commit.
- Testes com Kotest. Toda spec termina em `Test` (o Pitest ignora as outras). Preferir testes de
  propriedade quando a regra é universal.
- Pitest nos três módulos com o plugin do Kotest (ADR 0015). Limite: **90%** em `:domain` e
  `:server`, **50%** em `:app` (metade do módulo é Compose, que só o `androidTest` alcança).
  - Montar fixtures dentro de cada teste, nunca no corpo da spec. Exceção lançada na construção da
    spec não chega ao PIT, e o mutante que quebra o construtor sobrevive.
  - Getter de data class só conta como coberto quando algum teste lê a propriedade; comparar por
    igualdade não basta.
  - Mutante sobrevivente vira teste novo ou exclusão justificada na ADR 0015. Não baixar o limite.
  - Já fora do PIT: o pacote `protocol` (DTOs, coberto por round-trip), a fiação de framework do
    `:server` (`ApplicationKt`, `plugins.*`, `db.*`) e, no `:app`, as telas Compose, os pontos de
    entrada do Android e o código gerado pelo SQLDelight.
- Detekt falha o build. `@Suppress` só com comentário justificando.
- Versões só em `gradle/libs.versions.toml`.

## Comandos

```bash
./gradlew check                 # tudo: testes, detekt, lint, pitest nos três módulos
./gradlew :domain:test          # rápido
./gradlew :domain:pitest        # mutação, limite 90%
./gradlew :server:test          # inclui Testcontainers (precisa de Docker)
./gradlew :server:pitest        # mutação, limite 90%, lento (Postgres por minion)
./gradlew :app:testDebugUnitTest
./gradlew :app:pitest           # mutação, limite 50%
./gradlew :app:assembleDebug
./gradlew detekt --auto-correct # corrige formatação
```
