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

## Monitoramento

O stack sobe também Prometheus e Grafana ao lado do servidor:

- O servidor expõe `GET /metrics` (formato Prometheus) na porta 8080, protegido por bearer token
  (`METRICS_TOKEN`). Sem essa variável configurada a rota responde 404.
- O **Prometheus** raspa `http://server:8080/metrics` a cada 15s, só pela rede interna do stack
  (nunca pela internet, nunca publicado pelo Caddy) e guarda 1 ano de dados em
  `/root/manual-stacks/palavramento/prometheus-data`.
- O **Grafana** é publicado pelo Caddy em `https://${GRAFANA_HOST}` com um dashboard "Palavramento"
  já provisionado (`deploy/grafana/`), em três partes. Login `admin` com a senha de
  `GRAFANA_ADMIN_PASSWORD`; cadastro de usuários e acesso anônimo ficam desligados.
  - **Jogadores**: conectados agora, ativos em 24h/7d/30d, contas por tipo (convidado/cadastrada), e
    taxa e latência das requisições HTTP.
  - **Relógio do cliente**: descompasso entre o relógio do jogador e o do servidor (mediana, p95, p99
    e máximo) e palavras submetidas por segundo. O servidor mede o relógio do jogador uma vez por
    `SubmitWord`, então contar essas medições conta as submissões.
  - **Saúde do servidor**: memória da JVM, CPU, threads, pausas de GC e tempo desde o último
    reinício. Vem dos binders que o plugin do Ktor registra sozinho, sem código nosso.

Para acessar, entre em `https://${GRAFANA_HOST}` com o usuário `admin` e a senha do `.env`. O
Prometheus não é acessível de fora do servidor (por desenho).

Para girar o `METRICS_TOKEN`: gere um valor novo (`openssl rand -hex 32`), atualize-o no `.env` e
rode `deploy/deploy.sh` (ou `deploy/publish.sh`) de novo. O script reescreve o arquivo do token que o
Prometheus lê e reinicia o servidor com o novo valor; o Prometheus relê o arquivo sozinho, sem
precisar reiniciar.

## Publicar na Google Play

Uma versão vai para a loja por tag, e o GitHub Actions faz o resto (ADR 0022):

```bash
# 1. Suba versionCode e versionName em app/build.gradle.kts, e escreva a nota da versao em
#    fastlane/metadata/android/pt-BR/changelogs/<versionCode>.txt
# 2. Commite.
git tag v1.0.1 && git push origin v1.0.1
```

O workflow `Release` roda `./gradlew check` inteiro, monta o `.aab` assinado e publica **ao vivo na
trilha de produção**, junto com a ficha da loja e as capturas. Ele nunca dispara sozinho: só em tag
`v*` ou no botão de `workflow_dispatch`. A tag precisa bater com o `versionName` do build, senão o
fastlane para antes de subir qualquer coisa.

Para rodar da própria máquina, com a chave revelada (`git secret reveal`) e o JSON da conta de
serviço em `fastlane/play-service-account.json`:

```bash
gem install fastlane -v 2.240.1
fastlane android validate    # manda tudo ao Google para conferir, sem publicar nada
fastlane android internal    # sobe para teste interno
fastlane android listing     # so a ficha e as capturas, sem binario
fastlane android release     # producao, ao vivo
```

Os três segredos que o CI precisa estão na ADR 0022. As imagens da ficha não são versionadas em
`fastlane/`: elas são copiadas de [`marketing/`](marketing/) na hora do release, que segue sendo
onde se editam.

## Backup

O stack sobe um serviço `backup` (borgmatic) que roda às 4h e empurra para dois repositórios Borg:
BorgBase e o NAS Curupira. O que importa é o `pg_dump` do Postgres; o diretório cru do banco vai
junto, somente leitura, como última cartada. Retenção: 7 diários, 4 semanais, 12 mensais. Detalhes e
o porquê em [`docs/adr/0021-backup.md`](docs/adr/0021-backup.md).

A senha dos repositórios fica em `BORG_PASSPHRASE`, no `.env` do servidor. **Guarde uma cópia dela
fora do servidor**: sem ela os dois repositórios são lixo cifrado.

```bash
# estado e último backup
docker exec $(docker ps -q -f name=palavramento_backup) borgmatic list

# backup agora, sem esperar o cron
docker exec $(docker ps -q -f name=palavramento_backup) borgmatic create --stats

# restaurar o banco (com o servidor parado)
docker exec $(docker ps -q -f name=palavramento_backup) borgmatic restore --archive latest
```

## Divulgação

`marketing/` tem o media kit para anunciar no Google Ads: textos dentro dos limites do Google,
imagens e vídeo nas proporções que a campanha de App aceita, e a ficha da Play Store. Nada disso
entra no build. Detalhes em [`marketing/README.md`](marketing/README.md).

## Licença

AGPL-3.0-or-later. Léxico e lista de frequência têm licenças próprias, descritas em
[`LICENSES.md`](LICENSES.md).
