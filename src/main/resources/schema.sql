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
