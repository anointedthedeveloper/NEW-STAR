-- kind: APP | FOLDER | PHRASE ; target meaning depends on kind (app display name/path, folder path, canonical command text)
CREATE TABLE aliases (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  kind TEXT NOT NULL CHECK (kind IN ('APP','FOLDER','PHRASE')),
  phrase TEXT NOT NULL,
  target TEXT NOT NULL,
  usage_count INTEGER NOT NULL DEFAULT 0,
  confidence REAL NOT NULL DEFAULT 1.0,
  disabled INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  last_used TEXT,
  UNIQUE(kind, phrase)
);

-- Utterance -> action mappings that succeeded; a rejected mapping stays recorded so it is not suggested again.
CREATE TABLE learned_commands (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  utterance TEXT NOT NULL UNIQUE,
  action_id TEXT NOT NULL,
  params TEXT NOT NULL,
  success_count INTEGER NOT NULL DEFAULT 0,
  reject_count INTEGER NOT NULL DEFAULT 0,
  disabled INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  last_used TEXT
);

CREATE TABLE corrections (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  utterance TEXT NOT NULL,
  wrong_action TEXT,
  right_action TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE workflows (
  name TEXT PRIMARY KEY,
  definition TEXT NOT NULL,
  approved INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
