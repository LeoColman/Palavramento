# ADR 0023: Mutação sai do `check`, roda toda semana e vira badge

**Status:** aceita (2026-09-25). Substitui a parte "portões de build" da ADR 0016.

## Contexto

A ADR 0016 pôs o Pitest como dependência de `check` nos três módulos. Funcionou como medida, mas
cobrou caro como portão:

- `./gradlew check` passou a levar uns 40 minutos, a maior parte na mutação do `:server`, que roda a
  suíte inteira por mutante coberto e fala com Postgres de verdade. Todo push esperava isso.
- O workflow de release (ADR 0022) depende do `check`, então publicar uma correção urgente também.
- Portão de mutação é portão instável. O PIT exige suíte verde para começar, e um teste que passa
  sozinho e estoura tempo sob a carga de três rodadas de mutação em paralelo derrubou o `:app`
  inteiro por um motivo que não tinha nada a ver com o código medido.

A mutação mede a qualidade dos testes, e essa qualidade muda devagar. Uma medida por semana basta
para ver a tendência.

## Decisão

- **`check` não roda mais Pitest.** Fica com testes, detekt e lint.
- **Os limites continuam na tarefa `pitest` de cada módulo** (90% em `:domain` e `:server`, 50% no
  `:app`). `./gradlew pitest` rodado à mão ou pelo workflow ainda falha abaixo deles, e a regra da ADR
  0016 segue igual: mutante sobrevivente vira teste ou exclusão justificada, nunca limite menor.
- **Workflow `Mutation`**, toda segunda às 06:00 UTC e pelo botão. Roda `./gradlew pitest --continue`,
  para que um módulo abaixo do limite não impeça a medição dos outros, e o job termina vermelho se
  algum ficou abaixo.
- **Badge no README com a força dos testes** (*test strength* no PIT): mortos sobre mutantes que algum
  teste alcançou, somados os três módulos. É uma métrica diferente da que o limite usa (mortos sobre
  todos os mutantes): o limite também cobra cobertura, e a força pergunta só se os testes que rodam
  percebem o código errado. O detalhe por módulo sai no resumo de cada execução.
- O número vem de `tools/mutation-strength.main.kts`, que lê o `mutations.xml` de cada módulo. É o
  mesmo script para o CI e para quem roda à mão, e ele imprime a data de cada relatório, para que um
  resultado velho não se passe por novo.
- O badge é um JSON no formato *endpoint* do shields.io, num branch órfão `badges` reescrito a cada
  execução. Commit semanal de robô na `main` enterraria o histórico que as pessoas leem.

## Consequências

- `check` volta a ser rápido, e o release deixa de esperar a mutação.
- Uma regressão na qualidade dos testes pode passar até uma semana despercebida. É o preço aceito.
  Quem mexer em algo delicado roda `./gradlew :modulo:pitest` antes de subir, e o CLAUDE.md diz isso.
- O badge mostra a força agregada, puxada para baixo pelo `:app`, onde metade do código é Compose que
  só o `androidTest` alcança. É o número honesto do projeto inteiro, não o do melhor módulo.
- Antes da primeira execução do workflow o branch `badges` não existe e o badge aparece como inválido.
