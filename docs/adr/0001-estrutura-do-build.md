# ADR 0001: Estrutura do build

**Status:** aceita (fase 0)

## Contexto

O dossiê limita o repositório a três módulos Gradle (`:domain`, `:server`, `:app`) e exige Detekt
falhando o build e Pitest com pelo menos 80% de mutantes mortos em `:domain`.

## Decisão

- **Gradle 9.6.1** via wrapper. O daemon roda em JDK 21 (`gradle/gradle-daemon-jvm.properties`),
  independente do `JAVA_HOME` da máquina. Toolchains faltantes são baixadas pelo plugin foojay.
- **Toolchains:** `:domain` e `:app` em Java 17 (o `:app` consome o bytecode do `:domain` e o D8 lida
  melhor com 17). `:server` em Java 21.
- **Kotlin 2.4.20**, **AGP 9.2.1** com `android.builtInKotlin=false` e `android.newDsl=false`,
  porque o SQLDelight 2.3.x ainda depende da API de variantes legada. A mesma combinação está em
  produção no Petals.
- **Catálogo de versões único** em `gradle/libs.versions.toml`.
- **Detekt 1.23.8** com `buildUponDefaultConfig` e `config/detekt/detekt.yml` compartilhado;
  `maxIssues: 0`.
- **Pitest** só em `:domain`, que é JVM puro, rodando pela linha de comando do PIT numa tarefa
  `JavaExec`. O plugin `info.solidsoft.pitest` (1.9.11) não aplica no Gradle 9: ele lê
  `ReportingExtension.baseDir`, que foi removido. `--mutationThreshold=80` e `check` depende de
  `pitest`: o limite de 80% é portão de build, não relatório. O PIT dirige o Kotest pelo plugin
  `kotest-extensions-pitest`. Specs precisam terminar em `Test`, senão o glob `--targetTests` as
  ignora e os mutantes aparecem como sem cobertura.

## Consequências

- `./gradlew check` roda testes, Detekt, Android Lint e Pitest. É mais lento que só `test`, mas é o
  critério de aceite de cada fase.
- A remoção de `builtInKotlin=false` é obrigatória no AGP 10; até lá o SQLDelight precisa suportar a
  nova API.
