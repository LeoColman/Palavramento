# ADR 0024: Robôs no placar, de 2 a 5 por rodada

**Status:** aceita (2026-09-26)

## Contexto

Com a 1.0 na Play Store e poucos jogadores, a maioria das rodadas tem uma pessoa só. O placar dessa
rodada mostra uma linha, "1º de 1, Percentil 0", e a sala parece vazia justamente para quem acabou de
chegar. O dono pediu que o placar mostre sempre pelo menos 5 jogadores, completando com robôs de
pontuação baixa, e que as métricas separem jogadores humanos de robôs.

## Decisão

**Os robôs só existem no fim da rodada.** Quando a rodada termina, o `BotFiller` (`:domain`) sorteia
quantos robôs um jogador sozinho veria, entre `MIN_BOTS` e `MAX_BOTS` (padrão 2 e 5, `MAX_BOTS=0`
desliga), e cada pessoa além da primeira toma o lugar de um deles: com 3 pessoas e sorteio 4 entram 2
robôs, com 6 pessoas ou mais nenhum. O `RoundFinalizer` ranqueia os robôs junto com as pessoas.

A primeira versão desta decisão completava sempre até exatamente 5 jogadores. No mesmo dia o dono
pediu o sorteio: uma sala que tem sempre o mesmo tamanho não parece uma sala. Eles não jogam durante a rodada, não abrem
socket, não mandam palavra: durante a partida nada no app mostra os outros jogadores, então não há o
que simular ali.

**Robô é fraco de propósito.** Cada um recebe de 3 a 10 palavras sorteadas entre as comuns de até 5
letras da própria grade, as que um iniciante acha primeiro. Grade com menos de 10 dessas cai para
qualquer palavra comum e, se ainda faltar, para qualquer palavra. Quem joga de verdade quase sempre
termina acima deles, mas não sempre, e isso é intencional: um placar em que a pessoa sempre ganha
de todos não parece uma sala.

**Robô se parece com jogador.** O nome é um apelido de uma lista fixa ou o nome padrão de convidado
(`Convidado` e quatro dígitos hexadecimais), que é o que um placar de verdade tem. O fio não muda:
o `Leaderboard` continua com as mesmas linhas, então nenhum app precisa de atualização.

**Nada de robô vai para o banco como jogador.** Sem linha em `players`, `round_results`,
`submissions` ou `player_stats`. Isso mantém as contas, os jogadores ativos (ADR 0019) e as
estatísticas de cada um sem robô nenhum. Mas a posição que a pessoa recebe conta os robôs, e é essa
que vai para `round_results.rank` e para a melhor colocação: o histórico precisa mostrar o mesmo
"2º de 5" que o placar mostrou.

**`rounds` guarda quantos humanos e quantos robôs a rodada teve** (migração `V5`, colunas
`human_players` e `bot_players`). O histórico lê o total dali, porque contar `round_results` já não
dá o tamanho do placar. Rodadas anteriores à `V5` têm as colunas nulas e o histórico volta a contar
`round_results`, que naquela época era a sala inteira.

**Métrica** `palavramento_round_players{kind="human","bot"}`, gauge com os números da última rodada
encerrada da sala, atualizado no mesmo laço de 60 s das outras (ADR 0019). Toda rodada encerrada
grava sua contagem, inclusive a que ninguém jogou, como zero e zero. A primeira versão só gravava
rodada com gente, e o gauge passou horas mostrando 1 humano e 4 robôs de uma rodada de teste
enquanto a sala estava vazia. O Grafana ganha um painel
com a série empilhada de humanos e robôs por rodada e um com a última rodada.

## Consequências

- Uma pessoa sozinha vê de 3 a 6 linhas no placar ("1º de 4, Percentil 100") em vez de "1º de 1,
  Percentil 0", e pode perder para um robô numa rodada ruim.
- A melhor colocação e o percentil de quem jogou com robôs não são comparáveis com os de antes desta
  mudança. Nenhuma estatística antiga é reescrita.
- Os robôs não se distinguem de pessoas no app. Se isso precisar mudar (um ícone, um sufixo no
  nome), basta mexer no nome que o `BotFiller` gera ou acrescentar um campo ao `LeaderboardRow`, com
  as regras de compatibilidade da ADR 0018.
- O contador "N jogadores aguardando" do lobby continua contando só sockets abertos: os robôs não
  existem fora do placar.
- Com 6 pessoas ou mais, nada muda.
