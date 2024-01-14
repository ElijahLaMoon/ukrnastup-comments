CREATE TABLE banned_users (
  id INTEGER PRIMARY KEY,
  is_currently_banned BOOLEAN NOT NULL, -- records are kept forever for history, if true updated_at indicates time of last ban
  is_channel BOOLEAN NOT NULL,
  telegram_id INTEGER NOT NULL UNIQUE,
  telegram_name TEXT NOT NULL,
  telegram_username TEXT,
  reason TEXT,
  banned_by TEXT,
  banned_by_telegram_id INTEGER NOT NULL,
  message_got_banned_for TEXT,
  message_got_banned_for_link TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER
)