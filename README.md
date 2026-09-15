# Palavramento

Clone do Wordament em português. Grade 4×4, rodadas de 2 minutos, todos os jogadores da sala na mesma
grade ao mesmo tempo. A v1 tem só o modo Multiplayer.

Especificação: [`docs/dossie.md`](docs/dossie.md). Decisões estruturais: [`docs/adr/`](docs/adr).

## Módulos

| Módulo | O que tem |
|---|---|
| `:domain` | Kotlin/JVM puro: regras do jogo, solver, pontuação, mutadores, gerador de grade, protocolo |
| `:server` | Ktor + Koin + PostgreSQL. Fonte única de verdade: valida palavras, pontua, conduz as rodadas |
| `:app` | Android com Jetpack Compose. Lobby, partida, resultados e placar |

## Build

Requisitos: JDK 21 (o Gradle baixa o 17 se faltar), Android SDK 36, Docker (testes de integração do
servidor).

```bash
./gradlew check              # testes, detekt, lint e mutação
./gradlew :server:run        # servidor local na porta 8080
./gradlew :app:installDebug  # app num aparelho ou emulador
```

## Licença

AGPL-3.0-or-later. Léxico e lista de frequência têm licenças próprias, descritas em
[`LICENSES.md`](LICENSES.md).
