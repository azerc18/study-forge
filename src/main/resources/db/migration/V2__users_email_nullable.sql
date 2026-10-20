ALTER TABLE users DROP CONSTRAINT users_email_key;
CREATE INDEX idx_users_email ON users (email);