-- The active-player gauges (ADR 0019) count distinct players by entered_at on every metrics
-- refresh. Without this index that is a sequential scan of round_results, which grows with every
-- round every player ever played, on a query that runs once a minute forever.
CREATE INDEX idx_round_results_entered_at ON round_results (entered_at);
