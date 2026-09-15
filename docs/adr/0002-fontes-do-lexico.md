# ADR 0002: Fontes do léxico e da frequência

**Status:** aceita (fase 0)

## Contexto

O dossiê pede Hunspell pt_BR empacotado (§2.1), expandido para lista plena de formas no build (§2.2),
e uma lista de frequência de licença aceitável para separar palavras comuns de especialistas (§2.3).
O ponto aberto §12.1 bloqueia essa separação até a licença ser decidida.

## Decisão

- **Dicionário:** Hunspell pt_BR do projeto VERO, copiado do repositório de dicionários do
  LibreOffice para `server/src/lexicon/`. Usado sob LGPLv3. Detalhes em `LICENSES.md`.
- **Frequência:** `pt_br_50k.txt` do FrequencyWords (OpenSubtitles 2018), CC BY-SA 4.0. As 50 mil
  formas mais frequentes são mais que suficientes, porque o corte comum/especialista fica bem abaixo
  disso; formas válidas fora da lista recebem rank infinito (especialista).
- **Arquivos versionados no repositório**, não baixados no build: o build não depende de rede e as
  fontes ficam auditáveis ao lado do código.
- **Expansão própria em Kotlin**, sem `unmunch`. O `.aff` do VERO usa `FLAG UTF-8`, 103 classes de
  afixo e cerca de 26 mil regras; só 26 regras carregam flags de continuação. Um expansor próprio
  roda em qualquer máquina com JDK (CI incluída) e pode ser testado com Kotest, o que o `unmunch` do
  pacote `hunspell-tools` não permite.

## Consequências

- O repositório carrega cerca de 6 MB de dados de léxico.
- Atualizar o dicionário é copiar os arquivos novos e rodar o build; o artefato binário é regenerado.
