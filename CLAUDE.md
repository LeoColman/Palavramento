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
- Detekt falha o build. `@Suppress` só com comentário justificando.
- Versões só em `gradle/libs.versions.toml`.

## Comandos

```bash
./gradlew check                 # tudo: testes, detekt, lint, pitest (:domain)
./gradlew :domain:test          # rápido
./gradlew :domain:pitest        # mutação, limite 80%
./gradlew :server:test          # inclui Testcontainers (precisa de Docker)
./gradlew :app:assembleDebug
./gradlew detekt --auto-correct # corrige formatação
```
