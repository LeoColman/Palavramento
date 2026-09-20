# Ficha da Google Play

A campanha de App não aceita palavra-chave: o Google lê esta ficha para decidir onde exibir o
anúncio. Então o trabalho de palavra-chave acontece aqui, não no Google Ads.

Os textos deste arquivo já foram conferidos contra os limites da Play Console pelo
[`src/check-copy.js`](src/check-copy.js).

## Título

Limite de 30 caracteres. O título é o campo de maior peso na busca da loja, então ele carrega o nome
da marca e o termo que as pessoas digitam.

<!-- limite: 30 -->
| Texto | Car. |
|---|---|
| Palavramento: caça-palavras | 27 |

## Descrição curta

Limite de 80 caracteres. É o que aparece embaixo do ícone antes de expandir a ficha, e também conta
para a busca.

<!-- limite: 80 -->
| Texto | Car. |
|---|---|
| Todos na mesma grade 4x4, dois minutos, placar ao vivo. Grátis e sem anúncios. | 78 |

## Descrição completa

Limite de 4000 caracteres. Repetir termo à exaustão é motivo de rejeição na Play Console, então
cada termo abaixo aparece onde faz sentido na frase.

<!-- limite: 4000 -->
```
Palavramento é um caça-palavras multijogador em português do Brasil. Você e todo mundo da sala
jogam a MESMA grade 4x4, no MESMO cronômetro de dois minutos. Quando o tempo acaba, o placar
aparece na hora e todo mundo vê quem achou mais palavra.

COMO SE JOGA
Arraste o dedo por letras vizinhas para formar palavras. Vale ligar na horizontal, na vertical e
na diagonal, desde que você não repita a mesma peça. Palavras a partir de três letras contam. Cada
letra tem um valor, e a pontuação da palavra é a soma das peças que você percorreu, então achar a
palavra comprida nem sempre é o melhor negócio.

TODO MUNDO NA MESMA GRADE
Não é jogo por turnos nem partida contra robô. A sala inteira recebe o mesmo tabuleiro no mesmo
instante e joga ao mesmo tempo. Uma rodada nova começa a cada poucos minutos, e você pode entrar no
meio: não precisa esperar ninguém.

TEMAS QUE MUDAM AS REGRAS
Cada rodada anuncia um tema no topo. Às vezes uma letra vale muito mais que o normal. Às vezes
entram peças de dígrafo (CH, LH, NH, RR, SS) que contam como uma letra só. Às vezes aparece uma
peça de duas letras alternativas, que serve para uma ou para outra. O tabuleiro nunca é o mesmo
duas vezes.

O FIM DA RODADA ENSINA
Acabou o tempo, o jogo mostra a lista completa de palavras que a grade tinha: as que você achou, as
comuns que deixou passar e as difíceis que só um especialista acharia. Dá para ver quantos pontos
existiam ali e quanto você tirou. É assim que se melhora.

SEU PROGRESSO FICA GUARDADO
Crie uma conta e o jogo guarda sua pontuação total, suas palavras, sua melhor partida, sua melhor
palavra, sua média de pontos e sua melhor colocação. Você ganha experiência e sobe de nível a cada
partida. O histórico deixa você revisitar rodadas antigas com o tabuleiro e a solução inteira.

PORTUGUÊS DE VERDADE
As palavras são validadas com o dicionário Hunspell pt_BR do projeto VERO. Acento, cedilha e
dígrafo funcionam como você espera de um jogo de letras feito para o português, não de uma tradução
de jogo em inglês.

SEM ANÚNCIO, SEM COMPRA, SEM RASTREAMENTO
Não existe anúncio no meio da rodada, não existe item para comprar, não existe versão paga e não
existe telemetria. O app pede uma permissão só: acesso à internet, porque o jogo é multijogador.

CÓDIGO ABERTO
O Palavramento é software livre sob licença AGPL-3.0-or-later, app e servidor. O código está em
github.com/LeoColman/Palavramento, e qualquer pessoa pode ler, estudar, modificar e rodar o próprio
servidor.

Se você gosta de caça-palavras, jogo de palavras cruzadas, desafio de vocabulário ou simplesmente
de provar que é bom de palavra, a próxima rodada começa em menos de um minuto.
```

## Palavras-chave

Termos que o Google usa para casar a busca com o app. Os marcados com "sim" já estão escritos na
ficha acima; os outros ficam de reserva para quando houver dado de busca real para justificar a
troca.

| Termo | Na ficha |
|---|---|
| caça-palavras | sim |
| jogo de palavras | sim |
| multijogador | sim |
| jogo de palavras em português | sim |
| formar palavras | sim |
| grade de letras / tabuleiro | sim |
| dígrafo | sim |
| vocabulário | sim |
| placar | sim |
| grátis | sim |
| sem anúncios | sim |
| dicionário português | sim |
| código aberto | sim |
| jogo de letras | sim |
| palavras cruzadas | sim |
| desafio de palavras | sim |
| partida rápida | não |
| jogo online multiplayer | não |
| treinar o cérebro | não |
| soletrar | não |
| ligar letras | não |
| ranking ao vivo | não |

Dois termos ficam de fora de propósito: **Wordament** e **Boggle**. São marcas de terceiros
(Microsoft e Hasbro) e usá-las na ficha ou no anúncio é motivo de reprovação, por mais que o dossiê
do projeto descreva o jogo como um clone de Wordament.

## Recursos gráficos

| Recurso | Especificação | Arquivo |
|---|---|---|
| Ícone | 512x512, PNG 32 bits | [`ads/play-icone-512x512.png`](ads/play-icone-512x512.png) |
| Imagem de destaque | 1024x500, JPEG ou PNG 24 bits | [`ads/play-destaque-1024x500.png`](ads/play-destaque-1024x500.png) |
| Capturas de smartphone | mínimo 2, máximo 8; 1080x2424 aqui | [`screenshots/`](screenshots/) |
| Vídeo de prévia | URL do YouTube | [`ads/video/`](ads/video/) (falta subir) |

O ícone é o mesmo desenho do `ic_launcher` instalado no aparelho (o path de
`app/src/main/res/drawable/ic_launcher_foreground.xml` sobre o laranja de
`ic_launcher_background`), renderizado no viewport 108x108 inteiro. Ícone da loja diferente do
ícone da gaveta de apps é uma desconfiança gratuita.

A Play Console recomenda no mínimo 4 capturas com pelo menos 1080 px para o app concorrer a
destaque editorial. As de [`screenshots/`](screenshots/) são 1080x2424 e saem do emulador com a
barra de status em modo demo, então não têm relógio torto nem ícone de notificação.

## Formulários do Console

A ficha acima é o que o jogador lê. O que a revisão exige, e que não está em lugar nenhum do build,
é o que segue.

### Segurança dos dados

Coleta dados: **sim**. Compartilha com terceiros: **não**. Criptografado em trânsito: **sim**.
O usuário pode pedir exclusão: **sim**, pelo app e pela página web (ADR 0020).

| Tipo | Coletado | Obrigatório | Para quê |
|---|---|---|---|
| E-mail | Só de quem cria conta | Não, jogar de convidado não pede | Login e recuperação da conta |
| Nome de exibição | Sim | Sim | Mostrar quem é quem no placar da rodada |
| Id do jogador | Sim | Sim | Ligar a sessão ao histórico |
| Ações no app (palavras e rodadas) | Sim | Sim | Pontuação, placar e estatísticas |
| Registros de acesso (IP, data) | Sim | Sim | Operar e proteger o servidor |

Senha entra como prática de segurança, não como dado coletado em forma bruta: fica só como hash
bcrypt (`auth/PasswordHasher.kt`).

### URLs obrigatórias

| Campo do Console | URL |
|---|---|
| Política de privacidade | https://palavramento.colman.com.br/privacidade |
| Exclusão de conta | https://palavramento.colman.com.br/exclusao-de-conta |

As duas páginas são servidas pelo próprio servidor (`rest/LegalRoutes.kt`), então sobem junto com o
deploy.

### Classificação etária (IARC)

Jogo de palavras: sem violência, sem conteúdo sexual, sem linguagem imprópria, sem jogo de azar, sem
compra. Interação entre usuários **indireta**: jogadores veem nome de exibição e pontuação uns dos
outros no placar da rodada, e não existe chat nem mensagem direta. Sem localização compartilhada.
Sem anúncio dentro do app.

### Público-alvo

13 anos ou mais, por causa da conta com e-mail. Não é destinado a crianças, então fica fora do
programa Família.

## Requisitos técnicos da 1.0.0

- Android App Bundle assinado (`./gradlew :app:bundleRelease`), com Play App Signing usando a chave
  de release atual como chave de envio (ADR 0017)
- `versionCode` 3, `versionName` 1.0.0
- minSdk 26, targetSdk 36
- Única permissão declarada: `android.permission.INTERNET`
