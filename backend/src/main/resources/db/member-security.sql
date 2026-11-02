-- Additive migration only. Never run automatically against the legacy schema.
CREATE TABLE IF NOT EXISTS kunirank_member_sessions (
  token_hash CHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES auth_user(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS kunirank_member_sessions_expiry ON kunirank_member_sessions(expires_at);
CREATE TABLE IF NOT EXISTS kunirank_member_limits (
  bucket_key CHAR(64) PRIMARY KEY,
  attempts INTEGER NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS kunirank_member_limits_expiry ON kunirank_member_limits(expires_at);
