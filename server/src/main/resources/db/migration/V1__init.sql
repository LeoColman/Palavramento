-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2026 Leonardo Colman Lopes

-- Schema for dossier §7, plus what auth (§8) and XP (§9) need. Applied by Flyway both in production
-- and in Testcontainers-backed tests, so the two never drift (ADR 0003). Every id is a client
-- generated UUID stored as text, matching the plain String ids the protocol DTOs already use
-- (Rest.kt, ServerMessage.kt), so no ORM identity type crosses the wire boundary.

CREATE TABLE players (
  id TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  is_guest BOOLEAN NOT NULL,
  auth_provider TEXT NOT NULL,
  email TEXT UNIQUE,
  password_hash TEXT,
  created_at TIMESTAMPTZ NOT NULL
);

-- Refresh tokens are stored only as a hash (never the raw token), per the dossier's "refresh de
-- curta duracao" requirement (§8) hardened with rotation: `replaced_by_hash` chains a token to the
-- one that rotated it out, so presenting an already-rotated token again (reuse, a sign of theft) is
-- detectable and revokes the whole chain (see AuthService, ADR 0007).
CREATE TABLE refresh_tokens (
  id TEXT PRIMARY KEY,
  player_id TEXT NOT NULL REFERENCES players (id),
  token_hash TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  replaced_by_hash TEXT
);

CREATE INDEX idx_refresh_tokens_player_id ON refresh_tokens (player_id);

-- theme_title/theme_subtitle/common_min are additions beyond the dossier's literal rounds() column
-- list (§7): RoundStart (protocol) and RoundHistoryEntry (Rest.kt) both need the theme text and the
-- generation restriction verbatim, and re-deriving them from mutator_json at read time would require
-- duplicating MutatorTheme's pt-BR strings outside :domain. Persisting the values MutatorTheme
-- produced at generation time keeps a round's displayed theme stable even if MutatorTheme's wording
-- changes later. status tracks the round through the scheduler's lifecycle for crash recovery.
CREATE TABLE rounds (
  id TEXT PRIMARY KEY,
  room_id TEXT NOT NULL,
  seed BIGINT NOT NULL,
  board_json TEXT NOT NULL,
  mutator_json TEXT NOT NULL,
  theme_title TEXT NOT NULL,
  theme_subtitle TEXT NOT NULL,
  common_min INT NOT NULL,
  max_score INT NOT NULL,
  max_words INT NOT NULL,
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL
);

CREATE INDEX idx_rounds_room_starts_at ON rounds (room_id, starts_at);

-- The full pre-calculated solution (dossier §3: "a grade + solucao completa e persistida antes do
-- inicio da rodada"). normalized is the dedup key (Solver already guarantees one entry per
-- normalized word); word is the lexicon's accented display form.
CREATE TABLE round_words (
  round_id TEXT NOT NULL REFERENCES rounds (id),
  normalized TEXT NOT NULL,
  word TEXT NOT NULL,
  score INT NOT NULL,
  tier TEXT NOT NULL,
  path_json TEXT NOT NULL,
  PRIMARY KEY (round_id, normalized)
);

CREATE TABLE submissions (
  id TEXT PRIMARY KEY,
  round_id TEXT NOT NULL REFERENCES rounds (id),
  player_id TEXT NOT NULL REFERENCES players (id),
  normalized TEXT NOT NULL,
  word TEXT NOT NULL,
  score INT NOT NULL,
  path_json TEXT NOT NULL,
  accepted_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_submissions_round_player ON submissions (round_id, player_id);

-- double_xp defaults false and is never set by v1 code (dossier §9: "Get Double XP!" is out of v1,
-- "deixar a coluna... pronta"). The rest of a round's RoundStats (secondsPerWord, averageLength,
-- bonusPoints, averagePoints) is recomputed on demand from `submissions` by RoundStatsCalculator
-- instead of being duplicated here, so there is exactly one stored source for per-word timing.
CREATE TABLE round_results (
  round_id TEXT NOT NULL REFERENCES rounds (id),
  player_id TEXT NOT NULL REFERENCES players (id),
  score INT NOT NULL,
  words INT NOT NULL,
  rank INT NOT NULL,
  xp INT NOT NULL,
  double_xp BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (round_id, player_id)
);

CREATE INDEX idx_round_results_player_id ON round_results (player_id);

-- Registered players only (dossier §8 guest policy; enforced in code, not by a constraint, because
-- promotion/migration builds this row from round_results rather than gating writes at insert time).
-- best_word_score and total_xp are additions beyond the dossier's literal column list: LifetimeStats
-- (Rest.kt) shows the best word's own score next to it, and XP (dossier §9) needs a lifetime total
-- to feed the level curve on the lobby header (PlayerProfile). games_completed is an addition too:
-- games_played counts every round_results row (dossier: "resultados... ainda contam" even after
-- LeaveRoom), games_completed counts only the ones where the player found at least one word, so the
-- lobby can distinguish "played" from "meaningfully played" without re-scanning round_results.
CREATE TABLE player_stats (
  player_id TEXT PRIMARY KEY REFERENCES players (id),
  total_score BIGINT NOT NULL,
  total_words BIGINT NOT NULL,
  best_game_score INT NOT NULL,
  best_word TEXT,
  best_word_score INT NOT NULL,
  games_played INT NOT NULL,
  games_completed INT NOT NULL,
  best_rank INT,
  total_xp BIGINT NOT NULL
);
