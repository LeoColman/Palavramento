-- Who took part in a finished round, split into people and robots (ADR 0024). Robots have no row in
-- players or round_results, so counting round_results no longer gives the leaderboard's size: the
-- history reads it from here. Null on rounds finished before this migration, where the history falls
-- back to counting round_results, which was the whole room back then.
ALTER TABLE rounds ADD COLUMN human_players INTEGER;
ALTER TABLE rounds ADD COLUMN bot_players INTEGER;
