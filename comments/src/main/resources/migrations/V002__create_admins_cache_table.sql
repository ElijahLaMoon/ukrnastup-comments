CREATE TABLE admins_cache (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  telegram_id INTEGER NOT NULL,
  telegram_name TEXT,
  telegram_username TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER
);