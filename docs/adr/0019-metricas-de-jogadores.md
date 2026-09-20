# ADR 0019: Métricas de jogadores ativos

**Status:** aceita

## Contexto

A 1.0.0 vai para a Play Store, e até aqui a única forma de saber se alguém está jogando era abrir o
Postgres à mão no `ritalee`. Sem número de jogador ativo não dá para responder nada do que importa
depois de publicar: se a divulgação trouxe gente, se quem entra volta no dia seguinte, se um deploy
espantou todo mundo, se vale a pena subir uma segunda rodada simultânea.

O que existia de monitoramento era um Prometheus com Grafana no `ninho`, rede doméstica, raspando só
o NAS por SNMP. O servidor do jogo fica no `ritalee`, exposto na internet.

## Decisão

O servidor expõe métricas em formato Prometheus e o stack ganha Prometheus e Grafana ao lado dele, no
mesmo `docker stack`.

**No servidor** (`:server`), com Micrometer e o plugin `MicrometerMetrics` do Ktor:

| Métrica | Tipo | O que diz |
|---|---|---|
| `palavramento_players_connected` | gauge | jogadores com socket aberto agora |
| `palavramento_players_active{window="24h","7d","30d"}` | gauge | jogadores distintos que entraram em rodada na janela |
| `palavramento_players_accounts{kind="guest","registered"}` | gauge | contas que existem, por tipo |
| `ktor_http_server_requests_seconds_*` | timer | requisições REST, por rota e status |
| `jvm_*`, `process_*` | gauge | memória, threads e GC, de graça com o Micrometer |

Decisões dentro disso:

- **Jogador ativo é quem entrou numa rodada**, lido de `round_results.entered_at`, não quem abriu o
  app. Entrar numa rodada é a única coisa que o servidor registra de forma durável por jogador, e é
  também a definição que interessa: gente jogando, não gente que abriu e fechou.
- **A janela conta a própria borda** (`entered_at >= agora - janela`), e a migração `V4` cria o
  índice por `entered_at`, senão a consulta vira varredura da tabela inteira a cada leitura.
- **As contagens do banco são atualizadas por um laço a cada 60 s** (`METRICS_REFRESH_SECONDS`), e o
  scrape só lê memória. O Prometheus raspa a cada 15 s, e três `COUNT(DISTINCT)` por scrape fariam do
  monitoramento o cliente mais pesado do servidor. Conexões abertas são exceção: esse número já está
  em memória e é lido na hora.
- **`_total` não entra em nome de gauge.** O Prometheus reserva esse sufixo para counter e o
  Micrometer o remove do nome, o que deixaria `palavramento_players` cru no fio. Daí `accounts`.
- **`/metrics` exige bearer token** (`METRICS_TOKEN`), comparado em tempo constante. Sem token
  configurado a rota responde 404, em vez de anunciar que existe. Os números dizem quanta gente joga
  e a que horas, o que não é assunto de quem passar pela URL.

**No deploy**, Prometheus e Grafana sobem no mesmo stack do servidor. O Prometheus raspa
`http://server:8080/metrics` pela rede interna, então o token nunca trafega pela internet, e só o
Grafana é publicado pelo Caddy.

## Consequências

- Dá para responder "quantos jogaram hoje" sem abrir o banco, e o histórico fica no Prometheus.
- Uma janela de 30 dias num Prometheus com retenção de um ano é barata: são poucas séries, todas
  gauge.
- O número de ativos atrasa até 60 s, o que é irrelevante para a pergunta que ele responde. Quem
  precisa de tempo real olha `palavramento_players_connected`.
- `entered_at` passa a ser coluna de produto, não só de cálculo de `secondsPerWord` (ADR 0010).
  Mexer nela mexe nas métricas.
- Convidado conta como jogador ativo. É intencional: no Palavramento dá para jogar sem conta, e
  ignorar convidado esconderia a maior parte do público.
- O stack ganha dois serviços para manter e o disco do `ritalee` passa a guardar série temporal.
