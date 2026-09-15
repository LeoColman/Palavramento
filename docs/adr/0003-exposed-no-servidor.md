# ADR 0003: Exposed para acesso a dados no servidor

**Status:** aceita (fase 0)

## Contexto

O dossiê (§7) recomenda Exposed no servidor e só admite SQLDelight com ADR e spike provando o driver
de PostgreSQL.

## Decisão

Seguir a recomendação: **Exposed 1.x** (DSL, não DAO) sobre JDBC, com **HikariCP** e migrações em
**Flyway**. O esquema vive em SQL versionado (`server/src/main/resources/db/migration`), não em
`SchemaUtils.create`, para que produção e testes (Testcontainers) apliquem exatamente os mesmos
passos.

Não houve spike de SQLDelight: a simetria com o cliente não compensa o risco de um dialeto ainda
imaturo no caminho crítico do servidor.

## Consequências

- Duas tecnologias de persistência no repositório (SQLDelight no app, Exposed no servidor). Nenhuma
  entidade é compartilhada entre as duas: o que atravessa a fronteira são os DTOs de protocolo em
  `:domain`.
