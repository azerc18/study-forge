ALTER TABLE users ALTER COLUMN daily_quota SET DEFAULT 10;
UPDATE users SET daily_quota = 10 WHERE daily_quota < 10;