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

`check` inclui teste de mutação (Pitest) nos três módulos, e o limite falha o build: 90% de mutantes
mortos em `:domain` e `:server`, 50% no `:app`, onde metade do código é Compose e só os testes
instrumentados alcançam. Detalhes e exclusões em [ADR 0016](docs/adr/0016-testes-de-mutacao.md).

## APK assinado para distribuir

A chave de assinatura está no repositório cifrada com git-secret ([ADR
0017](docs/adr/0017-assinatura-de-release.md)). Quem tem uma chave GPG autorizada
(`git secret whoknows`) publica assim:

```bash
git secret reveal                  # escreve keystore.properties e app/palavramento-release.jks
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Sem o `reveal` o build continua funcionando, mas o APK de release sai **sem assinatura** e não
instala. O de debug (`:app:assembleDebug`) não depende de nada disso.

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

## Apontando o app para outro servidor

Por padrão o app usa o servidor de produção, `https://palavramento.colman.com.br`. Para jogar contra
um servidor local, defina `palavramento.serverUrl` em `local.properties` (arquivo por máquina, fora do
git) ou com `-P`: `http://10.0.2.2:8080` no emulador, o IP do computador num telefone na mesma rede.

```properties
palavramento.serverUrl=http://192.168.0.10:8080
```

```bash
./gradlew :app:installDebug   # ou: ./gradlew :app:installDebug -Ppalavramento.serverUrl=http://...
```

O build de debug aceita HTTP sem TLS para qualquer host; o de release continua exigindo TLS.

## Deploy

O servidor roda no `ritalee` (Docker Swarm, com o Caddy publicando o HTTPS), em
`/root/manual-stacks/palavramento` (ADR 0013). Para publicar o commit atual:

```bash
deploy/publish.sh          # ou deploy/publish.sh <commit> para voltar a uma versão anterior
```

O script manda o código versionado para o servidor, que constrói a imagem e atualiza o stack. Os
segredos (`POSTGRES_PASSWORD`, `JWT_SECRET`) ficam só no `.env` do servidor; o modelo está em
`deploy/.env.example`.

## Licença

AGPL-3.0-or-later. Léxico e lista de frequência têm licenças próprias, descritas em
[`LICENSES.md`](LICENSES.md).
