-- MySQL 8 教学版建表；索引写在 CREATE TABLE 内，重复启动不会因索引已存在而报错。
-- 与 schema.sql(H2) 保持相同字段。DATETIME 不受 MySQL TIMESTAMP 2038 年范围限制。
CREATE TABLE IF NOT EXISTS user_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account VARCHAR(32) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  email VARCHAR(128) NOT NULL,
  mobile VARCHAR(20),
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  token_version INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  CONSTRAINT uk_user_account_account UNIQUE(account),
  CONSTRAINT uk_user_account_email UNIQUE(email),
  CONSTRAINT uk_user_account_mobile UNIQUE(mobile)
);

CREATE TABLE IF NOT EXISTS user_profile (
  user_id BIGINT PRIMARY KEY,
  display_name VARCHAR(255), avatar_url VARCHAR(255), gender VARCHAR(32),
  signature VARCHAR(512), major VARCHAR(128), birthday DATE, updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS password_reset_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL,
  handled_at DATETIME,
  handled_by BIGINT,
  KEY idx_password_reset_status(status, created_at)
);

CREATE TABLE IF NOT EXISTS post (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  author_id BIGINT NOT NULL, category VARCHAR(32) NOT NULL,
  title VARCHAR(120) NOT NULL, content MEDIUMTEXT NOT NULL, summary VARCHAR(240) NOT NULL,
  cover_url VARCHAR(1024), status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
  view_count INT NOT NULL DEFAULT 0, like_count INT NOT NULL DEFAULT 0,
  comment_count INT NOT NULL DEFAULT 0, favorite_count INT NOT NULL DEFAULT 0,
  is_top BOOLEAN NOT NULL DEFAULT FALSE, is_essence BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, last_reply_at DATETIME, deleted_at DATETIME,
  KEY idx_post_status_category_created(status,category,created_at),
  KEY idx_post_author_created(author_id,created_at)
);

CREATE TABLE IF NOT EXISTS tag (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(32) NOT NULL, use_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  CONSTRAINT uk_tag_name UNIQUE(name)
);

CREATE TABLE IF NOT EXISTS post_tag (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL, tag_id BIGINT NOT NULL,
  CONSTRAINT uk_post_tag UNIQUE(post_id,tag_id),
  KEY idx_post_tag_tag(tag_id)
);

CREATE TABLE IF NOT EXISTS comment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL, author_id BIGINT NOT NULL,
  parent_id BIGINT, root_id BIGINT, reply_user_id BIGINT,
  content VARCHAR(2000) NOT NULL,
  image_urls TEXT,
  like_count INT NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL, deleted_at DATETIME,
  KEY idx_comment_post_parent(post_id,parent_id,created_at),
  KEY idx_comment_root(root_id,id)
);

CREATE TABLE IF NOT EXISTS like_record (
  user_id BIGINT NOT NULL, target_type VARCHAR(16) NOT NULL, target_id BIGINT NOT NULL,
  CONSTRAINT uk_like_record UNIQUE(user_id,target_type,target_id)
);

CREATE TABLE IF NOT EXISTS favorite (
  user_id BIGINT NOT NULL, post_id BIGINT NOT NULL,
  CONSTRAINT uk_favorite UNIQUE(user_id,post_id)
);

CREATE TABLE IF NOT EXISTS follow_relation (
  follower_id BIGINT NOT NULL, followed_id BIGINT NOT NULL, created_at DATETIME NOT NULL,
  CONSTRAINT uk_follow UNIQUE(follower_id,followed_id),
  KEY idx_follow_target(followed_id,created_at)
);

CREATE TABLE IF NOT EXISTS notification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL, actor_id BIGINT, kind VARCHAR(32) NOT NULL,
  target_id BIGINT, event_key VARCHAR(128) NOT NULL, content VARCHAR(500) NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE, created_at DATETIME NOT NULL,
  CONSTRAINT uk_notification_event UNIQUE(event_key),
  KEY idx_notification_user_read(user_id,is_read,created_at)
);

CREATE TABLE IF NOT EXISTS notification_outbox (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_key VARCHAR(128) NOT NULL,
  payload TEXT NOT NULL,
  published BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL,
  CONSTRAINT uk_notification_outbox_event UNIQUE(event_key),
  KEY idx_notification_outbox_pending(published,id)
);

CREATE TABLE IF NOT EXISTS conversation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  pair_key VARCHAR(64) NOT NULL, last_message_id BIGINT,
  last_message_at DATETIME, created_at DATETIME NOT NULL,
  CONSTRAINT uk_conversation_pair UNIQUE(pair_key)
);

CREATE TABLE IF NOT EXISTS conversation_member (
  conversation_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
  unread_count INT NOT NULL DEFAULT 0, last_read_message_id BIGINT,
  CONSTRAINT uk_conversation_member UNIQUE(conversation_id,user_id),
  KEY idx_conversation_member_user(user_id)
);

CREATE TABLE IF NOT EXISTS private_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id BIGINT NOT NULL, sender_id BIGINT NOT NULL,
  client_message_id VARCHAR(80) NOT NULL, message_type VARCHAR(16) NOT NULL,
  content VARCHAR(4000) NOT NULL, created_at DATETIME NOT NULL,
  CONSTRAINT uk_private_message_client UNIQUE(sender_id,client_message_id),
  KEY idx_private_message_history(conversation_id,id)
);

CREATE TABLE IF NOT EXISTS user_punishment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL, operator_id BIGINT NOT NULL,
  action VARCHAR(24) NOT NULL, reason VARCHAR(500) NOT NULL,
  expires_at DATETIME, created_at DATETIME NOT NULL,
  KEY idx_punishment_user(user_id,created_at)
);

CREATE TABLE IF NOT EXISTS admin_audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  operator_id BIGINT NOT NULL, action VARCHAR(40) NOT NULL,
  target_type VARCHAR(20) NOT NULL, target_id BIGINT NOT NULL,
  before_state VARCHAR(100), after_state VARCHAR(100),
  reason VARCHAR(500) NOT NULL, created_at DATETIME NOT NULL,
  KEY idx_admin_audit_target(target_type,target_id,created_at)
);
