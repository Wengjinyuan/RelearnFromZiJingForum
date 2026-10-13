-- F01 账号表。IF NOT EXISTS 让本地应用重复启动时不会删除已有数据。
CREATE TABLE IF NOT EXISTS user_account (
    -- 由数据库生成主键，业务代码注册时不需要提前生成 id。
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- account/email/mobile 的唯一约束既保证业务规则，也防止并发注册产生重复数据。
    account VARCHAR(32) NOT NULL,
    -- 这里只保存 BCrypt 哈希，永远不保存明文密码。
    password_hash VARCHAR(100) NOT NULL,
    email VARCHAR(128) NOT NULL,
    mobile VARCHAR(20),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    -- 修改密码时递增；JWT 中版本与这里不一致时，旧 Token 立即失效。
    token_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_account_account UNIQUE (account),
    CONSTRAINT uk_user_account_email UNIQUE (email),
    CONSTRAINT uk_user_account_mobile UNIQUE (mobile)
);
CREATE TABLE IF NOT EXISTS user_profile (
    user_id BIGINT PRIMARY KEY,
    display_name VARCHAR(255),
    avatar_url VARCHAR(255),
    gender VARCHAR(32),
    signature VARCHAR(512),
    major VARCHAR(128),
    updated_at TIMESTAMP
);
ALTER TABLE user_profile ADD COLUMN IF NOT EXISTS birthday DATE;

-- F03 帖子主表。第一阶段只依赖 MySQL/H2，缓存、MQ、ES 留到后续增强。
CREATE TABLE IF NOT EXISTS post (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    author_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content CLOB NOT NULL,
    summary VARCHAR(240) NOT NULL,
    cover_url VARCHAR(1024),
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    view_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    favorite_count INT NOT NULL DEFAULT 0,
    is_top BOOLEAN NOT NULL DEFAULT FALSE,
    is_essence BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_post_status_category_created
    ON post(status, category, created_at);
CREATE INDEX IF NOT EXISTS idx_post_author_created
    ON post(author_id, created_at);

CREATE TABLE IF NOT EXISTS tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    use_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_tag_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS post_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    CONSTRAINT uk_post_tag UNIQUE (post_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_post_tag_tag ON post_tag(tag_id);

-- F04：parent_id 是直接父评论，root_id 是整棵回复树的顶层评论。
CREATE TABLE IF NOT EXISTS comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    parent_id BIGINT,
    root_id BIGINT,
    reply_user_id BIGINT,
    content VARCHAR(2000) NOT NULL,
    image_urls CLOB,
    like_count INT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);
-- 旧 H2 练习库可能已经有 comment 表；IF NOT EXISTS 建表不会增加新字段。
ALTER TABLE comment ADD COLUMN IF NOT EXISTS image_urls CLOB;
ALTER TABLE comment ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_comment_post_parent ON comment(post_id, parent_id, created_at);
CREATE INDEX IF NOT EXISTS idx_comment_root ON comment(root_id, id);

-- F05：关系表是事实来源；post/comment 上的计数是便于读取的冗余值。
CREATE TABLE IF NOT EXISTS like_record (
    user_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    CONSTRAINT uk_like_record UNIQUE(user_id, target_type, target_id)
);
CREATE TABLE IF NOT EXISTS favorite (
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    CONSTRAINT uk_favorite UNIQUE(user_id, post_id)
);

-- F06：关注方向是 follower_id -> followed_id；唯一键保证重复关注不新增行。
CREATE TABLE IF NOT EXISTS follow_relation (
    follower_id BIGINT NOT NULL,
    followed_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_follow UNIQUE(follower_id, followed_id)
);
CREATE INDEX IF NOT EXISTS idx_follow_target ON follow_relation(followed_id, created_at);

-- F07：event_key 去重，接收人只能访问自己的通知。
CREATE TABLE IF NOT EXISTS notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    actor_id BIGINT,
    kind VARCHAR(32) NOT NULL,
    target_id BIGINT,
    event_key VARCHAR(128) NOT NULL,
    content VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_notification_event UNIQUE(event_key)
);
CREATE INDEX IF NOT EXISTS idx_notification_user_read ON notification(user_id, is_read, created_at);

-- F07 Outbox：业务事务只插入本表，定时发布器等提交后再投递 RabbitMQ。
CREATE TABLE IF NOT EXISTS notification_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(128) NOT NULL,
    payload CLOB NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_notification_outbox_event UNIQUE(event_key)
);
CREATE INDEX IF NOT EXISTS idx_notification_outbox_pending ON notification_outbox(published, id);

-- F10：pair_key 按较小/较大用户 ID 拼接，保证双人会话只有一条。
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pair_key VARCHAR(64) NOT NULL,
    last_message_id BIGINT,
    last_message_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_conversation_pair UNIQUE(pair_key)
);
CREATE TABLE IF NOT EXISTS conversation_member (
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    unread_count INT NOT NULL DEFAULT 0,
    last_read_message_id BIGINT,
    CONSTRAINT uk_conversation_member UNIQUE(conversation_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_conversation_member_user ON conversation_member(user_id);
CREATE TABLE IF NOT EXISTS private_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    client_message_id VARCHAR(80) NOT NULL,
    message_type VARCHAR(16) NOT NULL,
    content VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_private_message_client UNIQUE(sender_id, client_message_id)
);
CREATE INDEX IF NOT EXISTS idx_private_message_history ON private_message(conversation_id, id);

-- F11：处罚和审计随业务状态一起在数据库事务内写入。
CREATE TABLE IF NOT EXISTS user_punishment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    action VARCHAR(24) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_punishment_user ON user_punishment(user_id, created_at);
CREATE TABLE IF NOT EXISTS admin_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    before_state VARCHAR(100),
    after_state VARCHAR(100),
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_admin_audit_target ON admin_audit_log(target_type, target_id, created_at);

CREATE TABLE IF NOT EXISTS password_reset_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL,
    handled_at TIMESTAMP,
    handled_by BIGINT
);
CREATE INDEX IF NOT EXISTS idx_password_reset_status ON password_reset_request(status, created_at);
