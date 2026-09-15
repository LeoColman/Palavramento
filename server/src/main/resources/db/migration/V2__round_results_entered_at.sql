-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2026 Leonardo Colman Lopes

-- ADR 0010 (late join, product decision 2026-09-15): a player who joins a round already running
-- becomes a participant with their own entry time (max(round starts_at, join time) on the server
-- clock), not the round's own starts_at. entered_at records that per (round, player) row so
-- RoundStatsCalculator.compute can measure secondsPerWord from a late joiner's own entry both right
-- after the round (RoundEnd) and later on demand (/players/me/rounds), which round_results.starts_at
-- alone cannot express once the in-memory RoundState is gone.

ALTER TABLE round_results ADD COLUMN entered_at TIMESTAMPTZ;

-- Backfill: every row written before this migration predates late join, so every participant back
-- then joined at the round's own starts_at, which is the correct value here, not a guess.
UPDATE round_results
SET entered_at = rounds.starts_at
FROM rounds
WHERE rounds.id = round_results.round_id;

ALTER TABLE round_results ALTER COLUMN entered_at SET NOT NULL;
