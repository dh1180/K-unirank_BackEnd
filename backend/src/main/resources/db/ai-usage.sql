-- Additive, explicitly applied migration. No prompt, email, token or IP is stored.
CREATE TABLE IF NOT EXISTS kunirank_ai_requests (
  request_id UUID PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES auth_user(id) ON DELETE CASCADE,
  status_code SMALLINT NOT NULL CHECK(status_code BETWEEN 200 AND 599),
  elapsed_ms INTEGER NOT NULL CHECK(elapsed_ms BETWEEN 0 AND 120000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS kunirank_ai_requests_created ON kunirank_ai_requests(created_at);
CREATE INDEX IF NOT EXISTS kunirank_ai_requests_user_created ON kunirank_ai_requests(user_id,created_at);
