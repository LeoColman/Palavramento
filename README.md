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

## Rodando o servidor localmente

O servidor precisa de um PostgreSQL. `docker-compose.yml` na raiz sobe um, com as credenciais que os
valores padrão de `ServerConfig` já esperam:

```bash
docker compose up -d postgres   # sobe o Postgres em localhost:5432
./gradlew :server:run           # aplica as migrações do Flyway e escuta em :8080
curl localhost:8080/health      # -> ok
```

Todo o resto é configurável por variável de ambiente (`ServerConfig.fromEnv`, valores padrão entre
parênteses): `PORT` (8080), `DATABASE_URL`
(`jdbc:postgresql://localhost:5432/palavramento`), `DATABASE_USER`/`DATABASE_PASSWORD`
(`palavramento`/`palavramento`), `JWT_SECRET` (**trocar em produção**), `JWT_ISSUER`, `JWT_AUDIENCE`,
`ACCESS_TOKEN_TTL_SECONDS` (900), `REFRESH_TOKEN_TTL_SECONDS` (2592000), `ROUND_DURATION_SECONDS`
(120), `INTERMISSION_DURATION_SECONDS` (25), `LATE_SUBMISSION_TOLERANCE_MILLIS` (500),
`SUBMIT_RATE_LIMIT_PER_SECOND` (10), `COMMON_CUTOFF`, `LEADERBOARD_SIZE` (20),
`LATE_JOIN_MIN_REMAINING_SECONDS` (10, ver ADR 0010). TLS é responsabilidade
do ambiente de implantação (proxy reverso), não do processo Ktor (ver ADR 0007).

Os testes de integração do servidor (`./gradlew :server:test`) precisam de Docker: cada execução sobe
seu próprio PostgreSQL via Testcontainers (compartilhado entre as specs, não o do `docker-compose`).

## Jogando num aparelho físico

Por padrão o app de debug procura o servidor em `http://10.0.2.2:8080`, que é o computador visto de
dentro do emulador. Num telefone na mesma rede Wi-Fi, aponte para o IP do computador em
`local.properties` (arquivo por máquina, fora do git) ou com `-P`:

```properties
palavramento.serverUrl=http://192.168.0.10:8080
```

```bash
./gradlew :app:installDebug   # ou: ./gradlew :app:installDebug -Ppalavramento.serverUrl=http://...
```

O build de debug aceita HTTP sem TLS para qualquer host; o de release continua exigindo TLS.

## Licença

AGPL-3.0-or-later. Léxico e lista de frequência têm licenças próprias, descritas em
[`LICENSES.md`](LICENSES.md).
