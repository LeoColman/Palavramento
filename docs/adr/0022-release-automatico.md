# ADR 0022: Release automático para a Google Play com fastlane

**Status:** aceita (2026-09-25)

## Contexto

Publicar uma versão era inteiramente manual: `git secret reveal`, `./gradlew :app:bundleRelease`,
abrir a Play Console, arrastar o `.aab`, colar as notas da versão, conferir a ficha. Cada passo é
uma chance de esquecer um, e o que se esquece não aparece: uma ficha desatualizada, uma captura
antiga, um `versionCode` que já foi usado, um bundle que saiu sem assinatura porque o `reveal` não
rodou (ADR 0017 faz o build cair para não assinado em silêncio, de propósito).

A 1.0.0 já está na loja, então o primeiro envio manual que o Google exige já aconteceu e o
`supply` do fastlane tem com o que conversar.

## Decisão

`fastlane` com `supply`, rodando no GitHub Actions.

- **Gatilho: tag `v*` ou o botão de `workflow_dispatch`. Nunca push em `main`.** Este workflow
  publica ao vivo em produção; um gatilho automático a cada merge transformaria qualquer PR numa
  versão pública.
- **`./gradlew check` inteiro roda antes**, como job separado do qual o release depende. Publicar
  sem os portões deixaria o `check` opcional justo no push em que ele mais importa.
- **`versionCode` e `versionName` continuam em `app/build.gradle.kts`**, e nada os deriva nem os
  escreve. A tag só dispara. O `Fastfile` confere que a tag e o arquivo dizem a mesma versão e
  falha se divergirem, que é o erro de pegar a tag `v1.0.1` sobre um build ainda em `1.0.0`.
- **Trilha `production`, estado `completed`**, ou seja, ao vivo. Existe o lane `validate`
  (`validate_only`, manda tudo para o Google conferir sem publicar) e o lane `internal` para ensaio.
- **A ficha sobe junto com o binário.** Título, descrições e notas ficam em `fastlane/metadata`.

### Segredos no CI: só a chave de envio

A ADR 0017 deixou em aberto como o CI assinaria, com duas opções: uma chave GPG só do CI em
`git secret tell`, ou os segredos do GitHub. Fica a segunda, por privilégio mínimo.

Uma chave GPG no CI abre **tudo** que o git-secret protege, hoje e no futuro. A chave de envio
sozinha abre uma coisa só. E como o app está na Play com Play App Signing, essa chave é a *upload
key*: se vazar, o remédio é pedir ao Google a troca da chave de envio, não abandonar o
`applicationId`, que era o medo da ADR 0017 antes de a Play entrar na história.

Três segredos no repositório do GitHub:

| Segredo | O que é |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 app/palavramento-release.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | a senha do `keystore.properties` (store e key são a mesma, PKCS12) |
| `PLAY_SERVICE_ACCOUNT_JSON` | JSON da conta de serviço da Play Console, com permissão de publicar |

O `keystore.properties` é remontado no runner a partir deles, e o `Fastfile` falha cedo se o
arquivo não existir, em vez de deixar sair um bundle sem assinatura que o Google recusa com um erro
que não diz nada sobre a causa.

### As imagens continuam morando em `marketing/`

O `supply` quer as imagens dentro de `fastlane/metadata/.../images`, mas elas são feitas e revisadas
em `marketing/`. Versionar as duas cópias é garantir que um dia elas discordem. Então o `Fastfile`
copia de `marketing/screenshots` e `marketing/ads` na hora do release, e esse diretório de imagens
fica no `.gitignore`. O texto da ficha, esse sim, é versionado em `fastlane/metadata`, tirado de
[`marketing/play-store.md`](../../marketing/play-store.md), que segue sendo onde ele é escrito e
conferido contra os limites de caractere.

## Consequências

- Publicar vira `git tag v1.0.1 && git push origin v1.0.1`, e o resto é o workflow.
- **Um release errado sobrescreve a ficha ao vivo.** É o preço de subir ficha e binário juntos, e
  foi escolhido de olhos abertos. O lane `validate` existe para ensaiar antes.
- **Não existe rede de segurança entre o workflow e o público.** Trilha de produção em `completed`
  significa que a versão vai para os jogadores assim que o Google liberar a revisão. Quem quiser a
  rede troca `status` para `draft` no `Fastfile`, ou usa o lane `internal`.
- O CI passa a guardar a chave de envio. Vazou, é troca de upload key com o Google e um segredo
  novo aqui, sem tocar no `applicationId`.
- `fastlane` fica fixado em 2.240.1. Subir a versão é uma linha no `release.main.kts`, e o YAML
  regenerado junto.
- Cada versão nova quer uma nota em `fastlane/metadata/android/pt-BR/changelogs/<versionCode>.txt`.
  Sem ela o `default.txt` sobe no lugar, que é melhor que nada e pior que a nota certa.
- O workflow é gerado do `release.main.kts`, como o `check`, e o job de consistência do próprio
  workflow reprova o YAML editado à mão.
