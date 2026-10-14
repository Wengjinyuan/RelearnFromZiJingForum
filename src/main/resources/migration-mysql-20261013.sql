-- 已有 MySQL 练习库只执行一次；新库由 schema-mysql.sql 直接创建该字段。
ALTER TABLE post ADD COLUMN last_reply_at DATETIME NULL AFTER updated_at;
UPDATE post p SET last_reply_at=(
  SELECT MAX(c.created_at) FROM comment c WHERE c.post_id=p.id AND c.deleted=FALSE
) WHERE p.last_reply_at IS NULL;
