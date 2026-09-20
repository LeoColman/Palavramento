# ADR 0020: Exclusão de conta

**Status:** aceita

## Contexto

A Play Store exige, de todo app que deixa criar conta, dois caminhos de exclusão: um dentro do
próprio app e uma página web onde alguém pede o mesmo sem precisar instalar nada. Sem isso a revisão
reprova, e a 1.0.0 não sai. A LGPD pede a mesma coisa por outro motivo: quem entrega dado pessoal
tem direito de retirá-lo.

O Palavramento guarda por jogador: e-mail e senha (hash), nome de exibição, resultados de rodada,
palavras submetidas, estatísticas de vida e tokens de sessão. Convidado tem tudo isso menos e-mail e
senha.

## Decisão

`DELETE /players/me`, autenticado pelo mesmo bearer das outras rotas de jogador, responde 204 e apaga
o jogador junto com tudo que aponta para ele: `refresh_tokens`, `submissions`, `round_results`,
`player_stats` e a linha em `players`. Não existe exclusão em duas etapas nem período de carência: o
jogador pediu, some.

Decisões dentro disso:

- **Apaga de verdade, não marca como apagado.** Coluna `deleted_at` deixaria e-mail e histórico no
  banco, o que é exatamente o que o jogador pediu para não existir mais.
- **Vale para convidado também.** Convidado é conta, só que sem e-mail: guarda histórico e
  estatística, e a mesma regra se aplica.
- **A rodada em si fica.** `rounds` e `round_words` descrevem o tabuleiro e as palavras possíveis,
  não o jogador. O que sai é a participação dele: resultado, submissões e estatísticas.
- **O placar histórico daquela rodada perde essa linha.** É consequência aceita: manter o nome no
  ranking seria manter dado de quem pediu para sair.
- **O socket aberto cai.** Se o jogador estiver numa partida quando pedir a exclusão, a conexão é
  fechada: o token dele deixou de existir.
- **Depois de excluir, o app entra como convidado novo**, para a pessoa continuar jogando sem
  reinstalar nada.
- **A página web fica no próprio servidor** (`/exclusao-de-conta`), junto com a política de
  privacidade (`/privacidade`). O servidor já tem domínio, HTTPS pelo Caddy e deploy versionado
  (ADR 0013), então não vale subir site estático separado só para duas páginas.

## Consequências

- Requisito da Play e da LGPD atendido pelos dois caminhos, app e web.
- Exclusão é irreversível e imediata. O texto de confirmação no app diz isso com todas as letras,
  antes do botão.
- Quem exclui a conta perde o histórico e volta ao nível 1 como convidado novo.
- O servidor passa a servir HTML, coisa que até aqui não fazia. São duas páginas estáticas, em pt-BR,
  sem framework.
