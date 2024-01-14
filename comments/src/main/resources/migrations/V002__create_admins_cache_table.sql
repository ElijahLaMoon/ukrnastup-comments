CREATE TABLE admins_cache (
  id INTEGER PRIMARY KEY,
  telegram_id INTEGER NOT NULL UNIQUE,
  telegram_name TEXT NOT NULL,
  telegram_username TEXT,
  created_at INTEGER NOT NULL
)