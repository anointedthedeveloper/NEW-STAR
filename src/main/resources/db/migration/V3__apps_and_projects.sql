CREATE TABLE app_usage (
  path TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  launch_count INTEGER NOT NULL DEFAULT 0,
  last_launched TEXT,
  favorite INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE dev_projects (
  path TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  last_opened TEXT,
  approved INTEGER NOT NULL DEFAULT 1
);
