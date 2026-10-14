CREATE TABLE kv (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE themes (
  name TEXT PRIMARY KEY,
  json TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE widget_layouts (
  name TEXT PRIMARY KEY,
  json TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE operation_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ts TEXT NOT NULL DEFAULT (datetime('now')),
  utterance TEXT,
  action_id TEXT NOT NULL,
  params TEXT,
  status TEXT NOT NULL,
  message TEXT,
  duration_ms INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_history_ts ON operation_history(ts);
