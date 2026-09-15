# Licenças

Auditoria da fase 0 (dossiê §2.1, §2.3 e §12.1). Registra de onde vem cada peça distribuída e por que
ela pode conviver com a licença do projeto.

## Projeto

Palavramento (app Android e servidor) é distribuído sob **AGPL-3.0-or-later**. Texto integral em
[`LICENSE`](LICENSE).

## Léxico: Hunspell pt_BR (projeto VERO)

| Item | Valor |
|---|---|
| Arquivos | `server/src/lexicon/pt_BR.dic`, `server/src/lexicon/pt_BR.aff` |
| Origem | <https://github.com/LibreOffice/dictionaries/tree/master/pt_BR> (baixado em 2026-09-14) |
| Autores | Projeto VERO, Raimundo Santos Moura e colaboradores (ver `server/src/lexicon/README_pt_BR.txt`) |
| Licença | Dupla: **LGPLv3** ou **MPL 2.0**, à escolha de quem redistribui |

**Decisão:** usamos o dicionário sob **LGPLv3**. A LGPLv3 é a GPLv3 acrescida de permissões
adicionais, e a AGPLv3 §13 permite combinar obras AGPLv3 com obras GPLv3. Não há conflito.

**Obrigações cumpridas:**

- Os arquivos originais ficam no repositório, sem modificação, junto do `README_pt_BR.txt` com os
  avisos de autoria e licença.
- O artefato derivado (a trie binária gerada no build, dossiê §2.2 e §2.4) é produzido por código que
  está neste repositório. Quem recebe o servidor recebe também o "código-fonte correspondente" do
  artefato: o `.dic`, o `.aff` e o expansor.
- O cliente Android **não** embarca o léxico (dossiê §2.5), então o APK não carrega esta obrigação.

## Frequência: FrequencyWords pt_BR (OpenSubtitles 2018)

| Item | Valor |
|---|---|
| Arquivo | `server/src/lexicon/pt_br_50k.txt` |
| Origem | <https://github.com/hermitdave/FrequencyWords/blob/master/content/2018/pt_br/pt_br_50k.txt> (baixado em 2026-09-14) |
| Autor | Hermit Dave |
| Licença do conteúdo | **CC BY-SA 4.0** (o código do gerador é MIT e não é usado aqui) |
| Corpus de base | OpenSubtitles2018, via OPUS (P. Lison e J. Tiedemann, 2016) |

**Decisão (ponto aberto §12.1, resolvido):** a lista é aceita. O fallback de frequência sintética
(dossiê §2.3) **não** é necessário.

**Justificativa:**

- A Creative Commons declarou a CC BY-SA 4.0 compatível, em sentido único, com a GPLv3
  (<https://creativecommons.org/share-your-work/licensing-considerations/compatible-licenses>).
- A lista, e os ranks derivados dela que vão para a trie, são **dados** empacotados ao lado do
  programa, lidos em tempo de execução. Tratamos o artefato como agregação: o arquivo de dados
  mantém CC BY-SA 4.0 com atribuição, o código continua AGPL.
- Contagens de ocorrência de palavras são fatos sobre o corpus; não reproduzem texto das legendas.

**Atribuição exigida (CC BY-SA 4.0):** "Lista de frequência pt_BR de FrequencyWords, por Hermit Dave,
CC BY-SA 4.0, derivada do corpus OpenSubtitles2018 (OPUS)." Esta atribuição deve aparecer na tela
"Sobre" do app quando ela existir, porque o app exibe a classificação comum/especialista derivada
destes dados.

**Risco residual:** o OPUS não publica uma licença formal para o OpenSubtitles2018, apenas pede
citação. Como só usamos contagens agregadas, o risco é considerado baixo. Se for necessário
eliminá-lo, o caminho é trocar a lista pelo corpus Leipzig ou pela frequência sintética do §2.3; a
separação comum/especialista é parametrizada e não depende da fonte.

## Dependências de build e execução

Todas compatíveis com AGPLv3:

| Dependência | Licença |
|---|---|
| Kotlin, kotlinx.serialization, kotlinx.coroutines, Ktor, Exposed | Apache-2.0 |
| Koin, Kotest, SQLDelight, AndroidX, Jetpack Compose | Apache-2.0 |
| Flyway (community), HikariCP, bcrypt (at.favre.lib), MockK | Apache-2.0 / MIT |
| Testcontainers | MIT |
| Driver JDBC do PostgreSQL | BSD-2-Clause |
| Logback | EPL-1.0 ou LGPL-2.1 (usado sob LGPL-2.1 ou posterior) |
| Detekt, PIT (Pitest) | Apache-2.0 (só build, não distribuídos) |
