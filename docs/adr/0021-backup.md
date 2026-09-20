# ADR 0021: Backup do servidor

**Status:** aceita

## Contexto

Até a 1.0.0 o servidor não tinha backup nenhum. Tudo que um jogador acumula vive só no Postgres do
`ritalee`: conta, e-mail, senha, histórico de rodada, palavras submetidas e estatística de vida. Um
disco que morre, um `docker volume rm` errado ou um `DROP` numa sessão de psql apagam isso sem
volta, e o jogo acabou de entrar na Play Store, onde essa perda deixa de ser problema só meu.

Os outros stacks do mesmo servidor já resolvem isso do mesmo jeito: um container `b3vis/borgmatic`
dentro do próprio stack, com cron próprio, empurrando para BorgBase e para o Curupira, o NAS de
casa. Não faz sentido inventar caminho novo.

## Decisão

Um serviço `backup` no stack do Palavramento, borgmatic, rodando às 4h da manhã, com dois
repositórios Borg:

| Repositório | Onde | Por quê |
|---|---|---|
| `ssh://gn1yvera@gn1yvera.repo.borgbase.com/./repo` | BorgBase | fora de casa e fora do provedor do servidor |
| `ssh://curupira@curupira.colman.com.br:2222/volume1/Borg/palavramento.colman.com.br` | NAS Curupira | cópia física própria, restauração rápida e sem custo de saída |

Decisões dentro disso:

- **A fonte de verdade é o `pg_dump`**, pelo hook `postgresql_databases` do borgmatic, formato
  `custom`. É o único jeito de tirar cópia consistente de um banco que está em uso.
- **O diretório cru do Postgres entra junto**, montado somente leitura em `/data`, como nos outros
  stacks. É rede de segurança para o caso de o dump falhar, sabendo que uma cópia de arquivo de
  banco em uso pode sair inconsistente e só serve como última cartada.
- **Retenção**: 7 diários, 4 semanais, 12 mensais. Um ano de histórico num banco que hoje cabe em
  poucos megabytes.
- **A senha do Borg fica no `.env`** do servidor, junto das outras, fora do git. Sem ela o backup
  não abre, então ela precisa existir também fora do servidor: se o `ritalee` sumir com o `.env`,
  os dois repositórios viram lixo cifrado.
- **A chave SSH é a do servidor**, no volume externo `ssh` que os outros stacks já compartilham. O
  Palavramento não cria chave própria.
- **A configuração não vira `docker config` criado à mão**, diferente dos outros stacks. O
  `publish.sh` já envia `deploy/` para o servidor, então o compose monta `deploy/borgmatic.yaml`
  direto: mudou no git, rodou o deploy, valeu. Um passo manual a menos para esquecer.

## Consequências

- Perder o servidor deixa de perder o jogador. A restauração é `borgmatic restore`, com o banco
  parado, a partir de qualquer um dos dois repositórios.
- Duas cópias em lugares independentes: provedor na Finlândia e NAS no Brasil.
- O backup só protege o que está no Postgres e no diretório dele. Código, léxico e imagem se
  refazem do git, e é assim que deve ser.
- Mudar usuário, senha ou nome do banco exige mexer no `borgmatic.yaml` junto, senão o dump começa
  a falhar em silêncio. O `check` do borgmatic roda a cada 4 semanas e é o que denuncia repositório
  quebrado.
- Falta alerta: hoje um backup que falha aparece só no log do serviço. O Grafana (ADR 0019) já está
  no ar e é o lugar natural para isso, quando a hora chegar.
