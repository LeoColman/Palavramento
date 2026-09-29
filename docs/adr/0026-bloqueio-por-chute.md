# ADR 0026: Bloqueio por chute

**Status:** aceita (2026-09-29)

## Contexto

Um jogador reclamou de quem fica chutando: arrastar por qualquer caminho até alguma coisa colar. Com
a validação otimista (ADR 0014), o chute não custa nada, nem espera: o veredito sai na hora.

## Decisão

- **5 palavras inválidas seguidas travam a grade por 5 segundos.** Só `INVALIDA` conta. Palavra
  repetida, caminho inválido e palavra curta demais são deslize, não chute. Qualquer palavra aceita
  zera a contagem, e o bloqueio também, para que o próximo exija mais 5 erros.
- **Não tira pontos.** O Wordament original não penaliza, e perder pontos por errar assusta quem está
  começando (decisão do dono).
- **A regra mora no `:domain`** (`GuessGuard`), imutável e sem relógio próprio: quem chama passa o
  tempo em milissegundos.
- **Quem aplica é o app.** Pela ADR 0014 a palavra inválida é rejeitada no aparelho e nunca vai ao
  servidor, então o servidor não vê os erros e não teria o que contar. O `OptimisticSubmission`
  atualiza a guarda a cada veredito, com o relógio sincronizado com o servidor, e devolve `Locked`
  enquanto ela vale: o caminho não é aplicado nem enviado. A tela da partida cobre a grade com o
  aviso e a contagem regressiva e engole o toque.
- **Sem relógio sincronizado, não conta.** Antes da primeira amostra de relógio não há como marcar
  até quando travar, então o erro passa sem contar. É uma janela de segundos no começo da conexão.

## Consequências

- Não protege contra app modificado. Um app modificado já recebe a lista inteira de palavras da
  rodada (ADR 0014); o bloqueio é para o jogador comum, não para quem trapaceia.
- A guarda recomeça a cada `RoundStart`, inclusive o de uma reconexão no meio da rodada.
- Só vale a partir da versão do app que a traz. Quem estiver em versão anterior continua sem bloqueio.
