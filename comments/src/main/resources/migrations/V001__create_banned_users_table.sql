CREATE TABLE banned_users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  telegram_id INTEGER NOT NULL,
  telegram_name TEXT,
  telegram_username TEXT,
  reason TEXT,
  banned_by TEXT,
  banned_by_id INTEGER NOT NULL,
  message_got_banned_for TEXT,
  message_got_banned_for_link TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER
)