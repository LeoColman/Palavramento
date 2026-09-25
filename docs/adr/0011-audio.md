# ADR 0011: Áudio do jogo no `:app`

**Status:** aceita

## Contexto

O pedido do dono do projeto (2026-09-15): "Efeitos sonoros tipo música de fundo que acelera quando o
tempo está se esgotando e um som para palavras certas e palavras erradas." Isso cobre três pedaços:
uma trilha de fundo que toca durante a rodada e acelera perto do fim, efeitos curtos de
aceite/rejeição/já-encontrada, e os dois ajustes correspondentes na folha de configurações que a fase
6 já criou para os haptics (`docs/adr/0008-polimento-do-app.md`). Só `:app`, `tools/audio/`,
`LICENSES.md` e docs entram no escopo; `:domain` e `:server` ficam intocados.

## Decisão

### Geração dos sons: sintetizados por script, não gravados nem baixados

`tools/audio/generate_sounds.py` gera os quatro arquivos (`bg_music`, `sfx_accepted`, `sfx_rejected`,
`sfx_already_found`) usando só a biblioteca padrão do Python (`wave`, `math`, `struct`, `random` com
semente fixa `20260915`, a data do pedido). Isso evita repetir a auditoria de licença que o léxico e a
lista de frequência já exigiram (dossiê §12.1, `LICENSES.md`): não há amostra nem áudio de terceiro
para creditar, e o script no repositório é o "código-fonte correspondente" do `.ogg` gerado, rodável
por qualquer um (`python3 tools/audio/generate_sounds.py`) para reproduzir exatamente os mesmos bytes.

A trilha é um loop curto de 8 tempos (132 BPM, ~3.6s) somando duas camadas por cima uma da outra: um
arpejo pentatônico (dó maior pentatônica: C D E G A) subindo até C6 e descendo de volta a C5 - a nota
em que o próximo ciclo do loop recomeça, uma costura melódica, não só técnica - e uma linha de baixo
simples de 4 notas. Cada nota usa um envelope de ataque curto seguido de decaimento exponencial até
~0 bem antes do fim do seu próprio intervalo, o que garante que toda nota (e portanto o buffer inteiro)
comece e termine em silêncio: é isso que torna o ponto de loop livre de estalo, sem precisar de
crossfade. Os efeitos curtos reaproveitam o mesmo sintetizador de nota única: `sfx_accepted` é uma
subida rápida de três notas (C5-E5-G5, ~270ms) com um pouco do 2º harmônico para brilho; `sfx_rejected`
é uma nota grave curta (~160ms) com uma pitada de ruído branco para textura de "baque"; `sfx_already_found`
é um único tom médio (E4, ~180ms) mais suave, com ataque mais lento e volume mais baixo que os outros
dois - o par sonoro do flash amarelo de "já encontrada" (`BoardView.kt`'s `TileFlashKind.Duplicate`),
não um erro.

Como `ffmpeg` está disponível nesta máquina, o script codifica cada WAV intermediário para OGG Vorbis
(`qscale:a 2`) antes de escrever em `app/src/main/res/raw/`, e apaga o WAV; sem `ffmpeg`, ele copia o
WAV 16-bit mono (22050 Hz, "taxa de amostragem modesta") direto para lá - o nome do recurso (`R.raw.*`)
não depende da extensão, então nada no Kotlin muda entre os dois casos. Os quatro arquivos somam
~23KB, bem abaixo do orçamento de ~1MB do task brief.

### Interface `GameAudio`, real por trás do Koin, fake nos testes

```kotlin
interface GameAudio {
  fun startMusic(roundId: String)
  fun updateRemaining(remainingMs: Long)
  fun stopMusic()
  fun play(effect: SoundEffect)
  fun release()
}
```

`AndroidGameAudio` (`audio/AndroidGameAudio.kt`) é a única implementação real: `MediaPlayer` para a
trilha (só ele expõe `PlaybackParams` para o `speed`/`pitch` da rampa) e `SoundPool` para os três
efeitos (baixa latência, task brief). Os testes usam `FakeGameAudio` (`app/src/test/.../audio/`), um
espião em memória - nenhuma dessas classes do `android.media` roda na JVM pura, o mesmo motivo pelo
qual `PersistenceModule` (ADR 0009) e `ViewModelModule` ficam fora de `AppModuleTest`. `di/AudioModule.kt`
é um módulo Koin novo, também fora de `checkModules()`, com `factory<GameAudio>` (não `single`): uma
instância nova por sala, liberada em `RoomViewModel.onCleared()`, no mesmo padrão que
`PersistenceModule` já usa para `MultiplayerSession`.

Ambos os `AudioAttributes` usam `USAGE_GAME` (task brief). Só a música pede foco de áudio
(`AudioManager.requestAudioFocus`, API `AudioFocusRequest` desde a API 26, que já é o `minSdk`): uma
ligação ou a reprodução de outro app pausa o `MediaPlayer` (`AUDIOFOCUS_LOSS*`) e ele retoma sozinho em
`AUDIOFOCUS_GAIN`, se ainda fizer sentido tocar (`wantsToPlayMusic`, que uma chamada explícita a
`stopMusic()` zera). Os efeitos, por serem curtos e sob demanda, não pedem foco - convenção comum para
sons de UI/jogo.

### Curva de velocidade: função pura de `remainingMs`, com um degrau nos últimos 10s

`MusicSpeedCurve.speedFor(remainingMs: Long): Float` (`audio/MusicSpeedCurve.kt`), sem estado e sem
Android:

- Mais de 30s restantes: **1.0x**, tempo normal.
- Entre 30s e 10s restantes: rampa linear de **1.0x a 1.25x**.
- 10s ou menos restantes: **1.5x** fixo - um degrau perceptível a partir da rampa, não sua simples
  continuação, sincronizado com o mesmo instante em que o cronômetro já fica vermelho
  (`isCountdownUrgent`, `ui/common/TimeFormat.kt`, mesmos 10s). A ideia é que a virada para "correndo
  contra o tempo" seja um evento distinto tanto visual quanto sonoro, não um continuum.

Sempre dentro de `[1.0, 1.5]` e nunca menor para um `remainingMs` menor (monotônica), verificado como
propriedade em `MusicSpeedCurveTest` além dos casos de fronteira fixos.

**Pitch sobe junto com o tempo, de propósito.** `PlaybackParams` separa `speed` de `pitch`; deixar só
o `speed` mudar produz um "time stretch" que preserva o tom (como a maioria dos players de vídeo faz
ao acelerar). Optamos por `setPitch(speed)` igual a `setSpeed(speed)` - o efeito clássico de
"correria" de fliperama (o tema fica mais agudo, não só mais rápido) - porque é isso que lê como
urgência mesmo de ouvido só, sem olhar para o cronômetro, e é a implementação mais simples (não exige
nenhum processamento de áudio extra). `AndroidGameAudio.updateRemaining` é chamado pelo próprio tique
do cronômetro do `MatchScreen` (`rememberRemainingMs`, já existente desde a fase 6, ~200ms) via um
novo parâmetro `onRemainingMsChanged`, em vez de um segundo laço de tique dentro do `RoomViewModel` -
um laço assim, usando `delay()` de verdade dentro do `viewModelScope`, nunca terminaria sob
`advanceUntilIdle()` nos testes existentes de `RoomViewModelTest` (que já dependem de
`UnconfinedTestDispatcher` para o laço de reconexão de `MultiplayerSession` avançar). `RoomViewModel.
updateMusicSpeed` também descarta a chamada quando a velocidade resultante não mudou desde o último
tique, para não reaplicar os mesmos `PlaybackParams` centenas de vezes por rodada fora da janela de
rampa.

### Quando a música toca: lógica pura sobre `MatchUiState`, dirigida pelo `RoomViewModel`

`RoomAudioPolicy` (`audio/RoomAudioPolicy.kt`) decide, sem depender de `GameAudio` nem de Android:

- `musicAction(currentRoundId, state)`: `Start(roundId)` ao entrar em `InRound` para uma rodada
  diferente da que já tocava, `Stop` ao sair de `InRound`, `None` caso contrário - inclusive quando um
  `RoundStart` de reconexão chega para a **mesma** `roundId` (o requisito explícito do task brief: "não
  reiniciar a trilha do zero"). O `RoomViewModel` nem chega a chamar `GameAudio.startMusic` de novo
  nesse caso; não é `AndroidGameAudio` quem decide não reiniciar, é a política que nunca pede.
- `effectAction(previousFeedback, state)`: o `SoundEffect` para o `lastFeedback` atual do estado, só
  se for diferente do último feedback já sonorizado - evita tocar de novo por uma recoleta do mesmo
  `WordAccepted`/`WordRejected` sem que um novo de fato tenha chegado.

`SoundEffectMapper.kt` (`SubmissionFeedback.toSoundEffect()`) faz o mapeamento canal-a-canal do task
brief: `Accepted` → `SoundEffect.Accepted`; `Rejected(JA_ENCONTRADA)` → `SoundEffect.AlreadyFound`;
qualquer outro `Rejected` → `SoundEffect.Rejected`.

`RoomViewModel` (não `MatchScreen`) é quem dobra cada `MatchUiState` novo por essas duas funções e
chama `GameAudio` - a trilha e os efeitos ficam amarrados ao ciclo de vida da sala (o `MultiplayerSession.
state` que já existia), não a se o `MatchScreen` está de fato composto no momento. Isso também é o que
faz `pause()`/`resume()` (fundo/primeiro plano, ADR 0008) e `leaveRoom()` pararem a música
corretamente: `pause()` chama `gameAudio.stopMusic()` e zera a `roundId` rastreada, e `resume()`
reavalia a política sobre o `state` atual (que pode não ter mudado desde a pausa) para religar a
trilha, em vez de esperar por uma mensagem nova do servidor que pode demorar mais que o usuário levou
para voltar ao app.

### Ajustes: dois toggles novos, mesmo padrão do haptics

`SettingsRepository`/`DataStoreSettingsRepository` (ADR 0008) ganham `musicEnabled` e `effectsEnabled`,
os dois `true` por padrão, na mesma DataStore `"settings"` que já guardava `hapticsEnabled` - sem
tabela nova, mesma chave de preferência por Boolean. `MatchSettingsSheet` ganha dois `Switch` novos
("Música", "Efeitos sonoros", `strings.xml`), entre o de vibração e o divisor que leva a "Sobre".
`RoomViewModel` lê os dois como `StateFlow` (`SharingStarted.Eagerly`, não `WhileSubscribed`: eles
controlam uma decisão de áudio tomada dentro do coletor de `state`, não algo que um composable
assina) e usa o valor atual para decidir se chama `gameAudio.startMusic`/`play` - a política de
`RoomAudioPolicy` continua sem saber de ajustes; o filtro é só no ponto de chamada, mesmo lugar onde
`MatchScreen` já filtra o haptics por `hapticsEnabled`.

### Os dois toggles valem na hora, sem reiniciar o app

O de efeitos já valia por construção: o gate é relido a cada som. O da música não, e o dono do
projeto relatou o sintoma (2026-09-25): a trilha é decidida uma vez por rodada, então desligar o
toggle no meio da rodada não parava nada e ligar de volta só era ouvido na rodada seguinte.
`RoomViewModel` passou a coletar `musicEnabled` num segundo `launch` do `viewModelScope`, e tanto a
transição de rodada quanto o toggle agora passam por um único `setMusicPlaying(shouldPlay)`. Ele
guarda em `musicTrackPlaying` se `GameAudio` chegou mesmo a ser mandado tocar, coisa que a
`musicRoundId` da política não sabe (ela também é preenchida para a rodada que está em silêncio por
causa do ajuste), e com isso nunca manda um `stopMusic()` de mentira.

O haptics (ADR 0008) tinha o mesmo sintoma por outro motivo, do lado do Compose: o `pointerInput` de
`BoardView` só reinicia quando o tabuleiro ou a rotação mudam, então os dois handlers de gesto
capturavam o `hapticsEnabled` da composição em que a rodada começou. Agora leem o valor por
`rememberUpdatedState`, que é a mesma `State` em toda recomposição.

### Ciclo de vida: sem vazamento de `MediaPlayer`/`SoundPool`

Cada `RoomViewModel` recebe seu próprio `GameAudio` (`factory`, não `single`, ver acima).
`RoomViewModel.onCleared()` chama `gameAudio.release()` (que por sua vez para a música e libera o
`SoundPool`), o mesmo ponto onde `session.stop()` já rodava (ADR 0006). `leaveRoom()` e `pause()`
também param a trilha na hora (`setMusicPlaying(false)`, ver acima), sem esperar o `onCleared`/a
próxima mensagem do servidor, para que sair da sala ou ir para segundo plano corte o som de imediato.

## Consequências

- `RoomViewModel` ganhou dois parâmetros de construtor (`GameAudio`, `SettingsRepository`);
  `di/ViewModelModule.kt` foi atualizado. `RoomViewModelTest` foi estendido com um `FakeGameAudio` e um
  `FakeSettingsRepository` novos (`app/src/test/.../audio/`, `.../settings/`), cobrindo início/parada de
  música, o caso de reconexão sem reinício, o mapeamento de efeito e os dois toggles desligados.
- `MatchScreen` ganhou um parâmetro `onRemainingMsChanged` (padrão `{}`, não quebra nenhum teste
  existente) que só encaminha o tique de cronômetro já existente para
  `RoomViewModel.updateMusicSpeed`; nenhuma lógica de áudio nova mora no Compose.
- `:domain` e `:server` não foram tocados. `tools/audio/generate_sounds.py` é reprodutível (semente
  fixa) e determinístico; rodar de novo produz byte a byte os mesmos quatro arquivos.
- Um `PlaybackParams` inválido (por exemplo, chamado antes do `MediaPlayer` estar preparado) é só
  ignorado (`runCatching`), o mesmo padrão de tolerância a falha que `MultiplayerSession` já usa para
  I/O de rede (ADR 0006): não há nada de mais específico a fazer além de tentar de novo no próximo
  tique do cronômetro.
