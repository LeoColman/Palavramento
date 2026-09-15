-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2026 Leonardo Colman Lopes

-- ADR 0012 (mutadores, decisao do dono do produto, 2026-09-15): LETRA_PROIBIDA e TAMANHO_MINIMO
-- foram removidos do sealed interface Mutator (:domain). Uma rodada antiga com um desses valores em
-- rounds.mutator_json (br.com.colman.palavramento.server.repository.RoundRepository decodifica essa
-- coluna com PalavramentoJson a cada leitura, inclusive na recuperacao apos reinicio e em
-- /players/me/rounds) nao decodifica mais no codigo atual: o discriminador "type" nao bate com
-- nenhum caso do sealed interface e a decodificacao lanca excecao.
--
-- Reescreve esses valores para o mutador padrao. theme_title/theme_subtitle sao colunas separadas
-- (V1__init.sql) e nao sao tocadas aqui, entao o historico continua mostrando o titulo original
-- ("Letra X proibida", "Minimo de N letras") mesmo depois de o mutador ativo virar SEM_MUTADOR.
UPDATE rounds
SET mutator_json = '{"type":"SEM_MUTADOR"}'
WHERE mutator_json LIKE '%"type":"LETRA_PROIBIDA"%' OR mutator_json LIKE '%"type":"TAMANHO_MINIMO"%';
