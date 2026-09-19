# ADR 0018: O app aguenta um servidor mais novo que ele

**Status:** aceita

## Contexto

O deploy é sempre servidor primeiro: o servidor em `palavramento.colman.com.br` sobe assim que o
código entra no `main`, e o APK só chega aos aparelhos quando alguém corta uma release e instala.
Entre um e outro existe uma janela em que todo aparelho instalado fala com um servidor que sabe mais
que ele.

Nessa janela o app quebrava. `ServerMessage` e `Mutator` são hierarquias seladas do
kotlinx.serialization, e um discriminador `type` que o build não conhece vira `SerializationException`.
No `KtorMultiplayerTransport` essa exceção estourava dentro do `Flow` de mensagens, o
`MultiplayerSession` tratava como conexão perdida e reconectava, e o servidor mandava o mesmo
`RoundStart` de novo: laço de reconexão até o jogador desistir. Foi o que aconteceu no Fairphone
quando os mutadores `DIGRAFOS`, `LETRA_NOS_CANTOS` e `UMA_OU_OUTRA` entraram no ar (2026-09-15 e
2026-09-18), com o app anterior instalado.

O `ignoreUnknownKeys` do `PalavramentoJson` já cobria campo novo em mensagem conhecida. O que faltava
era tipo novo.

## Decisão

Três camadas, da mais específica para a mais geral:

1. **`Mutator.Unknown`** (`DESCONHECIDO`) e **`ServerMessage.Unknown`** (`Unknown`): membros das duas
   hierarquias que o `PalavramentoJson` produz, via `polymorphicDefaultDeserializer`, para qualquer
   `type` sem caso neste build. O gerador nunca os produz e nenhum servidor os envia.
2. **O app ignora o que não entende**: o `MatchStateReducer` devolve o estado intacto para
   `ServerMessage.Unknown`, e `MutatorTheme.title` dá "Grade especial" para `Mutator.Unknown`. A
   rodada continua jogável porque as peças já chegam com o efeito do mutador aplicado nelas: o
   servidor manda valor e letras de cada peça, e `themeTitle`/`themeSubtitle` como texto pronto.
3. **Quadro indecifrável é descartado, não fatal**: `decodeServerMessageOrNull` no transporte pula o
   quadro com um `Log.w` em vez de derrubar o `Flow`. Sobra para essa camada só a mensagem conhecida
   cujo formato mudou, por exemplo um campo obrigatório que sumiu.

O caminho contrário, um servidor mais velho que o app, não recebe tratamento: o servidor sempre sobe
primeiro, então essa combinação não existe em produção.

## Consequências

- Mutador novo pode entrar em produção sem derrubar quem está com APK antigo. Esse jogador vê a
  grade e joga normalmente; só não lê o nome da regra, que vem pronto do servidor de qualquer forma.
- Mensagem nova do servidor pode entrar antes de o app saber lê-la. Quem não conhece ignora.
- Remover ou renomear campo obrigatório de mensagem existente continua quebrando o app antigo, agora
  com perda de um quadro em vez de laço de reconexão. Campo novo deve entrar com valor padrão, e
  campo velho deve ser aposentado só depois que os aparelhos estiverem atualizados.
- `Mutator.Unknown` entra nos `when` exaustivos do `:domain` (`BoardGenerator`, `MutatorTheme`) como
  sinônimo de `NoMutator`. O gerador nunca o recebe, porque quem gera conhece a própria regra.
- O cache local já tinha o `toDomainOrNull` do `HistoryRepository` para rodada antiga que não decodifica
  mais (ADR 0012). As duas defesas são complementares: aquela é para o passado, esta é para o futuro.
