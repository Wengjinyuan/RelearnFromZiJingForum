-- 仅供“升级旧练习 MySQL 库”手动执行一次；新库直接跑 schema-mysql.sql，无需本文件。
-- 先用 SHOW COLUMNS FROM comment 检查；若已有 image_urls/deleted_at，不要重复执行 ALTER。
ALTER TABLE comment ADD COLUMN image_urls TEXT NULL, ADD COLUMN deleted_at DATETIME NULL;

-- RabbitMQ Outbox 是新表；IF NOT EXISTS 允许重复执行。
CREATE TABLE IF NOT EXISTS notification_outbox (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_key VARCHAR(128) NOT NULL,
  payload TEXT NOT NULL,
  published BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL,
  CONSTRAINT uk_notification_outbox_event UNIQUE(event_key),
  KEY idx_notification_outbox_pending(published,id)
);
