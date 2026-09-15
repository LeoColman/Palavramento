# ADR 0004: Artefato binário do léxico

**Status:** aceita (fase 1)

## Contexto

A fase 0 (ADR 0002) decidiu as fontes: Hunspell pt_BR do VERO (`.dic`+`.aff`) e a lista de frequência
`pt_br_50k.txt`. A fase 1 (dossiê §2, §11) pede um expansor Hunspell próprio, filtros sobre as formas
expandidas, ranks de frequência com colapso de formas que compartilham a forma normalizada, um TSV
intermediário e uma trie binária compacta carregada pelo servidor em menos de 1 segundo.

## Decisão

### Expansor

`server/src/lexiconCompiler` (novo source set de `:server`, não embarcado no jar) implementa o
algoritmo Hunspell em Kotlin puro, sem `unmunch`:

- `AffixCondition` compila a condição de cada regra (4ª coluna do `.aff`) numa sequência de
  casadores por caractere (`.`, literal, `[abc]`, `[^abc]`), testada contra a palavra inteira, ancorada
  no fim para sufixo e no início para prefixo. O número de caracteres da condição é independente do
  tamanho do `strip`: `SFX D o a [^ã]o` olha 2 caracteres mas remove só 1 (`limo` -> `lima`).
- Sufixos, prefixos e o produto cruzado prefixo+sufixo (quando as duas classes são `Y`) são aplicados
  sobre cada entrada do `.dic`. O produto cruzado testa a condição do prefixo contra a palavra **já
  sufixada**, replicando a ordem real do Hunspell (ele desfaz o sufixo antes do prefixo ao validar).
- Flags de continuação (`add/flags`) viram o conjunto de flags da palavra derivada (não se somam às
  flags do stem) e disparam **um nível extra** de afixação, nunca recursivo. Isso é suficiente para o
  `.aff` do VERO (só 26 das ~26 mil regras carregam flags de continuação, todas usando a mesma flag
  `Ý`) e garante que o algoritmo termina mesmo com um `.aff` adversarial (sem `COMPOUND`, sem ciclos
  possíveis, porque não há uma segunda rodada de continuação).
- `FORBIDDENWORD` (`ý`) derruba a ocorrência marcada. `NOSUGGEST` (`Ý`) também é descartada: no VERO
  essa flag marca principalmente regionalismos e termos de baixo calão, que não interessam pontuar
  num jogo de palavras. A decisão é por ocorrência, não por stem: como flags não se herdam por
  afixação, a forma base marcada `NOSUGGEST` é descartada mas suas conjugações regulares (se a regra
  de afixo usada não carregar `Ý` como continuação) continuam entrando: isso é fiel à semântica do
  próprio Hunspell, não um atalho do expansor.

### Filtros (dossiê §2.2)

Sobre cada forma canônica gerada: descarta hífen, apóstrofo (reto ou tipográfico), ponto, dígito
decimal e qualquer letra maiúscula (inclusive maiúscula acentuada, cobre nomes próprios e siglas).
Depois de normalizar com `WordNormalizer.normalize`, descarta formas fora de 3 a 16 letras ou cuja
forma normalizada não seja puro `A-Z` (rede de segurança: pega dígitos Unicode fora de `0`-`9`, como
sobrescritos, e entradas de múltiplas palavras como `água de cheiro`).

### Frequência e colapso (dossiê §2.3, §1.6)

Rank de uma forma = linha 1-based da sua **própria grafia em minúsculas** (com acento) em
`pt_br_50k.txt`; ausente da lista, rank é infinito (`LexiconEntry.Unranked`, especialista). Formas que
colapsam na mesma forma normalizada (`pais`/`país`) viram uma entrada só: rank = melhor rank entre as
formas do grupo, display = a forma que atingiu esse rank.

**Regra de desempate**, quando duas formas do mesmo grupo têm o mesmo rank (o caso comum é as duas
sem rank, ambas `Unranked`): vence a forma canônica menor em ordem lexicográfica. Não tem significado
linguístico, existe só para o artefato ser byte-idêntico entre builds independente da ordem de
iteração.

### TSV intermediário

`forms.tsv` (`build/generated/lexicon/forms.tsv`, não versionado) tem uma linha por forma canônica
sobrevivente aos filtros (antes do colapso): `forma_canonica \t forma_normalizada \t
frequencia_rank`. Rank ausente é escrito como `LexiconEntry.Unranked` (`2147483647`), não como campo
vazio, para a coluna ser sempre um inteiro.

### Trie binária (`:domain`)

`TrieLexicon` (`domain/src/main/kotlin/.../lexicon/TrieLexicon.kt`) implementa `Lexicon` sem mudar o
contrato. Nós numerados em largura (BFS) de forma que os filhos de um nó ocupem uma faixa contígua de
ids (`childStart[nó] até childStart[nó+1]`): essa faixa já identifica os nós-filho, dispensando um
array separado de "nó alvo". `letter: ByteArray` guarda a letra de entrada de cada nó; `parent:
IntArray` permite reconstruir a grafia default (minúscula, sem acento) andando até a raiz.

Strings de exibição só são armazenadas quando diferem da grafia default (a maioria das palavras
portuguesas com acento precisa, mas a maioria das formas sem acento não), e mesmo essas ficam
empacotadas como um único blob UTF-8 com offsets, não como um `String` por entrada, para o load não
alocar um objeto por palavra.

Formato binário (`write`/`read`): cabeçalho mágico `PLEX` + versão (`Int`), contagem de nós, e os
arrays acima em blocos de bytes lidos/escritos em lote (via `ByteBuffer`), não um `Int` de cada vez.

### Build (`server/build.gradle.kts`)

`compileLexicon` (`JavaExec`) roda o expansor sobre os três arquivos versionados (`.dic`, `.aff`,
`pt_br_50k.txt`), entradas e saídas declaradas (cacheável). `processResources` depende dele e empacota
só o binário, em `/lexicon/pt-BR.bin` no jar; `forms.tsv` não é embarcado, só usado pelos testes do
`:server` (que recebem o caminho via propriedade de sistema).

## Números medidos

Medidos rodando `./gradlew :server:compileLexicon` com o dicionário e a lista de frequência reais.

| Métrica | Valor |
|---|---|
| Stems do `.dic` | 312 368 |
| Formas sobreviventes aos filtros (linhas de `forms.tsv`) | 2 425 525 |
| Entradas normalizadas após colapso (nós com palavra na trie) | 2 328 204 |
| Nós da trie | 4 291 589 |
| Tamanho do artefato binário | 84 042 649 bytes (~80,1 MiB) |
| Tempo de `compileLexicon` (expandir + filtrar + colapsar + montar trie + escrever) | ~5,5 s |
| Heap dado ao `compileLexicon` | 3 GB (`maxHeapSize`); folgado, não medido o pico real |
| Tempo de carregar o artefato pelo classpath (`TrieLexicon.read`, `LexiconLoaderTest`) | ~38 ms (primeiro acesso na JVM do teste; limite é 1000 ms) |
| Pitest de `:domain` após `TrieLexicon` | 98% mutantes mortos (limite é 80%) |

A explosão de candidatos descartados durante a expansão (a classe de sufixo `k`, pronomes clíticos,
tem 1192 regras e gera só formas com hífen) é intencionalmente não otimizada: o pipeline é uma
sequência preguiçosa (`Sequence`) filtrada forma a forma, então a memória de pico é dominada pelas
formas sobreviventes, não pelas descartadas, e o tempo total medido (~5,5 s) já inclui esse
desperdício.

## Consequências

- O jar do servidor ganha ~80 MB de recurso estático. Aceitável para um processo de backend; seria um
  problema para o APK, por isso o dossiê já exclui o cliente de embarcar o léxico (§2.5).
- Atualizar o dicionário é trocar os três arquivos em `server/src/lexicon/` e rodar o build de novo; o
  artefato e o `forms.tsv` são regenerados a partir deles, sem passo manual.
- A decisão de descartar `NOSUGGEST` é por ocorrência, não por família de palavras. Se o VERO um dia
  marcar sistematicamente as conjugações de um verbo vulgar, elas somem sozinhas; hoje pode sobrar
  alguma conjugação de uma forma base marcada. Ajustar exigiria propagar flags especiais através da
  afixação, o que o Hunspell em si não faz.
