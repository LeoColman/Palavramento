# ADR 0016: Chave de assinatura do app no repositório, cifrada com git-secret

**Status:** aceita (2026-09-16)

## Contexto

O `:app` tinha `isMinifyEnabled` e ProGuard no build type `release`, mas nenhum `signingConfig`.
`./gradlew :app:assembleRelease` produzia `app-release-unsigned.apk`, que o Android não instala. O
único APK distribuível era o de debug, assinado com o `~/.android/debug.keystore` da máquina de
desenvolvimento: tamanho de 15.8 MB contra 2.8 MB do release, `debuggable`, e uma chave que é a mesma
de qualquer projeto na máquina, ou seja, sem valor nenhum como identidade do app.

O dono do projeto pediu um APK para distribuir aos jogadores, o que exige uma chave de release de
verdade. Uma chave de assinatura Android tem uma propriedade desagradável: depois que um APK é
publicado com ela, **toda** atualização daquele `applicationId` precisa ser assinada com a mesma
chave. Perder a chave significa não poder mais atualizar o app, só publicar outro com outro id.

Isso deixa duas formas ruins de guardá-la: em claro no git (qualquer um com acesso ao repositório
assina em nome do projeto) ou só na máquina de quem criou (um disco morto encerra o app). O repositório
é privado, mas "privado" não é o mesmo que "cifrado", e um dia ele pode deixar de ser.

## Decisão

Versionar a chave no repositório, cifrada com [git-secret](https://sobolevn.me/git-secret/) sobre GPG.

- **Keystore**: `app/palavramento-release.jks`, PKCS12 (não o formato JKS, que é legado), RSA 4096,
  alias `palavramento`, `CN=Leonardo Colman Lopes, O=Palavramento, C=BR`, validade de 10000 dias
  (até 2054-02-01, bem além do mínimo de 2033 que a Play Store exige).
- **Senha**: 40 caracteres aleatórios, iguais para store e key porque PKCS12 não admite duas. Gerada
  com `openssl rand` direto para dentro de `keystore.properties`, nunca por argumento de linha de
  comando (que apareceria em `ps`) nem impressa no terminal.
- **`keystore.properties`** (raiz do repositório) guarda `storeFile`, `storePassword`, `keyAlias` e
  `keyPassword`. É o mesmo formato que o `local.properties` já usa para `sdk.dir` e
  `palavramento.serverUrl`, mas separado dele: `local.properties` é configuração de máquina e não
  entra no git de forma nenhuma, enquanto este entra cifrado.
- **Cifragem**: `git secret add` nos dois arquivos e `git secret hide`. O que o git versiona são
  `app/palavramento-release.jks.secret` e `keystore.properties.secret`; os originais estão no
  `.gitignore` (`keystore.properties`, `*.jks`), e o `!*.secret` que o `git secret init` acrescenta
  garante que a versão cifrada não seja ignorada junto.
- **Quem decifra**: hoje só `leonardo.dev@colman.com.br`, chave GPG
  `B3A599099ECC4DB4FD40896F77061922C5872792`. `git secret whoknows` é a lista corrente.
- **Build**: o `:app` lê `keystore.properties` se o arquivo existir e configura `signingConfigs.release`
  com ele. Se não existir, `signingConfig` fica nulo e o release sai sem assinatura, **em vez de
  quebrar o build**. Isso é deliberado: um clone novo, o CI e qualquer pessoa sem a chave GPG precisam
  continuar rodando `check`, `test` e `assembleDebug` normalmente. Quem pode assinar é quem revelou o
  segredo, e essa é exatamente a distinção que se quer.

## Consequências

- Publicar uma versão passa a ser `git secret reveal` e depois `./gradlew :app:assembleRelease`. Sem o
  reveal, o APK sai sem assinatura e não instala, o que é um erro barulhento na hora certa.
- **A chave GPG privada virou parte da cadeia de sobrevivência do app.** Perdê-la é perder a senha da
  keystore, que é perder o direito de atualizar o `br.com.colman.palavramento`. Backup da chave GPG
  fora do repositório é obrigatório, e um backup da keystore em claro num cofre de senhas não é má
  ideia como segunda rede.
- Uma segunda máquina ou pessoa exige `git secret tell <email>` seguido de `git secret hide` (o hide
  recifra para todos os destinatários) e um commit. Sair da lista é `git secret removeperson`, mas isso
  não desfaz o que a pessoa já decifrou: se alguém com acesso sair mal, a chave está comprometida e o
  remédio é outro `applicationId`, não uma rotação.
- O CI não assina nada, porque não tem a chave GPG. Se um dia precisar, as opções são uma chave GPG só
  do CI em `git secret tell`, ou os secrets do GitHub Actions. A primeira mantém uma fonte de verdade só.
- Se o app um dia for para a Play Store, esta chave passa a ser a *upload key* e o Google guarda a
  chave de assinatura real (Play App Signing). Aí a perda desta deixa de ser fatal, mas até lá é.
- O APK de debug continua como estava, assinado com a chave de debug, e serve para desenvolvimento.
  O que se distribui é o release.
