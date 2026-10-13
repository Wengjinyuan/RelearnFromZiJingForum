-- 仅在以后切换到已有 MySQL 库时执行；当前默认仍使用 H2。
ALTER TABLE user_profile ADD COLUMN birthday DATE NULL;

CREATE TABLE IF NOT EXISTS password_reset_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL,
  handled_at DATETIME,
  handled_by BIGINT,
  KEY idx_password_reset_status(status, created_at)
);
