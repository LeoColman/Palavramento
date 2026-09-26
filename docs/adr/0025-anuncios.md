# ADR 0025: Banner do AdMob no lobby e nos resultados

**Status:** aceita (2026-09-26)

## Contexto

Com o app publicado, o dono decidiu pôr anúncio: um banner embaixo, só no lobby e na tela de
resultados e placar. A conta AdMob é a mesma do publisher `pub-9745951044027822`, e o `app-ads.txt`
dele já está em `https://leonardo.colman.com.br/app-ads.txt`.

A política de privacidade dizia que o app não mostrava anúncio e prometia, se isso mudasse, atualizar
a página antes e avisar dentro do app.

## Decisão

- **SDK do Google Mobile Ads com banner adaptativo ancorado** (`AdBanner`), no fim do lobby, fora da
  rolagem, e embaixo das duas abas de resultados. **Nunca na partida:** o jogador arrasta o dedo pela
  grade o tempo todo, e um banner logo abaixo gera toque acidental, que o AdMob trata como tráfego
  inválido. A espera entre rodadas também fica sem anúncio.
- **Consentimento antes do SDK, mesmo sem mensagem configurada.** O app só é distribuído no Brasil,
  onde nenhuma lei exige consentimento prévio para anúncio: a LGPD aceita o legítimo interesse (art.
  7º, IX) e cobra transparência, que a política de privacidade dá. A exigência de formulário é do
  Google para o Espaço Econômico Europeu, o Reino Unido e a Suíça, e por isso nenhuma mensagem foi
  criada em Privacidade e mensagens do AdMob. O `GmsAds` ainda passa pelo User Messaging Platform e
  só chama `MobileAds.initialize` quando `canRequestAds()` permite: sem mensagem configurada, ele
  libera na hora e nada aparece para o jogador. Se a distribuição abrir para esses países, basta criar
  a mensagem no AdMob; o formulário e o botão "Privacidade dos anúncios" do lobby já estão no app.
- **IDs de teste em debug, sempre.** Quem desenvolve nunca toca anúncio real. O release usa os IDs
  reais declarados em `app/build.gradle.kts`, que não são segredo (vão em todo APK), e a tarefa
  `verifyAdmobIds` impede um `bundleRelease` com os IDs de teste.
- **A política de privacidade muda antes do app.** A nova seção "Anúncios" diz o que o Google recebe,
  como funciona o consentimento e como limitar. Ela é servida pelo servidor, então é publicada com o
  deploy do servidor, antes da versão do app com anúncio.
- **Aviso no app**, como a política prometia: um cartão no topo do lobby, "O Palavramento agora tem
  anúncios", com link para a política, que some quando o jogador toca "Entendi" e não volta.
- **Permissão adicional na licença.** O app é AGPL e os dois SDKs do Google são proprietários. O
  `LICENSES.md` registra uma permissão adicional (AGPLv3 §7) para combinar o programa com eles.

## Consequências

- O APK ganha o SDK do Google Play Services Ads e a permissão `AD_ID`.
- No Play Console: marcar "Contém anúncios" e atualizar a Segurança dos dados (ID de publicidade,
  interações com o app e diagnóstico compartilhados com o Google para publicidade).
- `GmsAds` e o `AdBanner` ficam fora do PIT: são chamadas ao SDK do Google, sem comportamento próprio
  que um teste na JVM alcance.
- Um fork que não queira anúncio remove o `AdsModule`, o `AdBanner` e as duas dependências.
