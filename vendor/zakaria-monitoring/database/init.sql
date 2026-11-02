-- Run this script while connected to the Render database crashai_neo_db.
-- Safe to execute repeatedly; no database switch or destructive operations.
CREATE TABLE IF NOT EXISTS crash_games (
    id BIGSERIAL PRIMARY KEY,
    game_id VARCHAR(50) UNIQUE NOT NULL,
    multiplier DECIMAL(10, 2) NOT NULL CHECK (multiplier >= 1.00),
    timestamp TIMESTAMP NOT NULL,
    source VARCHAR(20) DEFAULT 'xbet',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_crash_games_timestamp
    ON crash_games(timestamp DESC, game_id DESC);
CREATE INDEX IF NOT EXISTS idx_crash_games_multiplier
    ON crash_games(multiplier);
CREATE OR REPLACE VIEW crash_stats AS
SELECT COUNT(*) AS total_games,
       AVG(multiplier) AS avg_multiplier,
       STDDEV(multiplier) AS std_multiplier,
       MIN(multiplier) AS min_multiplier,
       MAX(multiplier) AS max_multiplier,
       PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY multiplier) AS median_multiplier,
       SUM(CASE WHEN multiplier < 2 THEN 1 ELSE 0 END) AS low_count,
       SUM(CASE WHEN multiplier >= 2 AND multiplier < 10 THEN 1 ELSE 0 END) AS medium_count,
       SUM(CASE WHEN multiplier >= 10 THEN 1 ELSE 0 END) AS high_count,
       MAX(timestamp) AS last_update
FROM crash_games;
