# ADR 0013: Deploy do servidor no ritalee

**Status:** aceita (2026-09-15)

## Contexto

O servidor rodava só na máquina de desenvolvimento, e o app de debug apontava para o IP dela na rede
local. O dono do projeto pediu para publicar o servidor em `palavramento.colman.com.br`, no
`ritalee`, junto dos outros serviços em `/root/manual-stacks`. O DNS já aponta para o ritalee.

O ritalee roda Docker Swarm com um nó só e o `caddy-docker-proxy` na rede overlay `caddy`: cada
serviço entra nessa rede e declara, em `deploy.labels`, `caddy: <domínio>` e
`caddy.reverse_proxy: "{{upstreams <porta>}}"`. O Caddy emite o certificado sozinho. Os stacks em
`manual-stacks` (randompicker, semudando-mailer) seguem o mesmo roteiro: código na pasta do stack,
`.env` com os segredos, `deploy.sh` que constrói a imagem com uma tag por data e roda
`docker stack deploy --resolve-image never`.

## Decisão

Seguir o mesmo roteiro, com os arquivos versionados em `deploy/`:

- **`deploy/publish.sh`** (roda na máquina de quem publica): `git archive` do commit pedido, enviado
  por SSH para `/root/manual-stacks/palavramento/src` (trocado por inteiro a cada publicação), e em
  seguida `deploy/deploy.sh` no servidor. O repositório no GitHub é privado; mandar o código pelo SSH
  evita uma chave de deploy só para o servidor clonar. Só vão arquivos versionados, nunca `build/` nem
  `local.properties`.
- **`deploy/deploy.sh`** (roda no servidor): valida o `.env`, constrói `palavramento-server:<data>` e
  atualiza o stack `palavramento`.
- **`deploy/Dockerfile`**: estágio de build com JDK 21 rodando
  `./gradlew -Ppalavramento.skipApp=true :server:installDist`. A imagem não tem Android SDK, então
  `settings.gradle.kts` deixa o módulo `:app` de fora quando essa propriedade vem ligada. O cache do
  Gradle fica num cache mount do BuildKit, reaproveitado entre deploys. O estágio final é o JRE 21 com
  a distribuição instalada e `JAVA_OPTS=-Xmx1g`.
- **`deploy/docker-compose.yml`**: o servidor (porta 8080, redes `default` e `caddy`, labels do Caddy)
  e o Postgres 16 com os dados em `/root/manual-stacks/palavramento/pgdata`. **Uma réplica só do
  servidor**: as rodadas em andamento vivem em memória (ADR 0007), então duas instâncias teriam duas
  salas diferentes.
- **Segredos**: `POSTGRES_PASSWORD` e `JWT_SECRET` ficam só no `.env` do servidor (permissão 600),
  gerados lá mesmo com `openssl rand`. O modelo sem valores está em `deploy/.env.example`.
- **App**: a URL padrão passa a ser `https://palavramento.colman.com.br` (o WebSocket vira `wss://`).
  `palavramento.serverUrl` em `local.properties` continua apontando builds de desenvolvimento para um
  servidor local.

## Consequências

- Um deploy derruba a instância antiga antes de subir a nova (padrão do Swarm com uma réplica): a
  rodada em andamento se perde e os jogadores reconectam na seguinte. Palavras já aceitas ficam no
  banco (ADR 0007).
- Voltar para uma versão anterior é `deploy/publish.sh <commit>`.
- **Dívida:** o Postgres ainda não tem backup. Os outros stacks com banco usam `borgmatic`; incluir o
  mesmo aqui exige a configuração do repositório Borg e a senha dele no `.env`.
