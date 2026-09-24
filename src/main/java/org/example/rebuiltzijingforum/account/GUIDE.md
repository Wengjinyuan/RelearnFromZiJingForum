# F01 account：账号、登录与鉴权

## 当前实现状态

这一包已经作为其他功能的完整样例实现并通过测试，不再只是目录占位。可直接运行的能力包括：

- 账号注册：字段校验、统一小写、BCrypt 哈希、唯一约束与 409 冲突响应。
- 账号登录：统一的错误提示，避免泄露账号是否存在。
- JWT：签名、过期时间、Bearer 解析以及受保护接口拦截。
- 当前用户：身份只来自已验证 Token，不接收客户端 userId。
- 修改密码：校验当前密码、增加 `token_version`、签发新 Token，使所有旧 Token 立即失效。
- H2/MySQL 双环境：默认 H2 开箱即跑，`mysql` profile 用于连接真实 MySQL。

在 IDEA 中先运行 `AccountApiTest`，再打开项目根目录的 `http/account.http` 手工调用。

## 这一包交付什么

完成注册、登录、读取当前用户、重置密码和登录态校验。第一版只依赖 MySQL；不要在这一阶段引入 Redis、RabbitMQ 或 Elasticsearch。

## 在 IDEA 中按顺序创建

```text
account/（账号功能根包：F01 的代码全部从这里向下组织）
├─ controller/（控制器包：接收 HTTP 请求并返回统一 JSON）
│  └─ AccountController.java（Controller：注册、登录、当前用户、修改密码接口）
├─ dto/（DTO 包：接收请求或在后端内部传递数据）
│  ├─ RegisterDTO.java（DTO：注册参数）
│  ├─ LoginDTO.java（DTO：登录参数）
│  ├─ ResetPasswordDTO.java（DTO：修改密码参数）
│  ├─ CurrentUserDTO.java（DTO：拦截器验证后的当前用户）
│  ├─ TokenDTO.java（DTO：新生成的 Token 和过期时间）
│  └─ TokenUserDTO.java（DTO：从 JWT 中解析出的用户数据）
├─ vo/（VO 包：只保存允许返回给前端的数据）
│  ├─ AccountVO.java（VO：脱敏后的账号信息）
│  └─ LoginVO.java（VO：登录 Token、过期时间和账号信息）
├─ service/（Service 业务接口包）
│  ├─ AccountService.java（Service：定义注册、登录、查询本人、修改密码方法）
│  └─ impl/（ServiceImpl 业务实现包）
│     └─ AccountServiceImpl.java（ServiceImpl：业务实现、事务、密码校验和调用 Mapper）
├─ mapper/（Mapper 数据库访问包）
│  └─ UserAccountMapper.java（Mapper：MyBatis-Plus 账号表增删改查）
├─ entity/（Entity 数据库实体包）
│  └─ UserAccountEntity.java（Entity：对应 user_account 表）
├─ config/（Config 配置包）
│  ├─ AccountConfig.java（Config：创建 BCrypt 和 Clock Bean）
│  └─ WebMvcConfig.java（Config：注册需要登录的接口路径）
├─ interceptor/（Interceptor 拦截器包）
│  └─ AuthenticationInterceptor.java（Interceptor：验证 Bearer Token）
└─ utils/（Utils 工具包）
   └─ JwtUtils.java（Utils：生成和解析 JWT）
```

固定调用顺序：`Controller -> Service -> ServiceImpl -> Mapper -> Entity/数据库`。输入用 DTO，输出用 VO。

## API 契约

- `POST /api/accounts/register`：账号、邮箱、手机号三者按产品规则校验并创建用户。
- `POST /api/accounts/login`：返回 token 和脱敏用户信息。
- `GET /api/accounts/me`：必须登录；从服务端认证上下文读取 userId，不能信任请求传入的 userId。
- `POST /api/accounts/reset-password`：必须先验证旧密码或一次性凭证，再替换密码哈希。

成功使用 `ApiResponse`；校验失败 400，未登录 401，账号被禁用 403，账号冲突 409。不要全部返回 200。

## 数据设计

`user_account` 至少包含：`id`、`account`、`password_hash`、`email`、`mobile`、`role`、`status`、`created_at`、`updated_at`。给 account/email/mobile 建符合业务规则的唯一约束。密码字段只保存 BCrypt/Argon2 哈希，日志和响应永不输出它。

当前已加入 MyBatis-Plus、MySQL/H2 驱动、`spring-security-crypto` 和 JJWT。`application.yml` 中的密钥只是本地开发默认值；非本地环境必须通过 `ZIJING_JWT_SECRET` 覆盖。

## 实现顺序

1. 先写注册请求校验与唯一约束冲突测试，再实现注册事务。
2. 写密码哈希单元测试，证明同一明文两次哈希不同且都能验证。
3. 实现登录；用统一错误避免暴露“账号不存在”还是“密码错误”。
4. 实现 token 生成/解析，payload 只放身份和最少授权信息，设置过期时间。
5. 拦截受保护接口，把身份放进当前请求上下文，并在请求结束后清理。
6. 最后实现安全的密码重置；旧 token 是否立即失效要有明确策略。

## 必测用例

- 重复账号/邮箱/手机号、空白、超长和非法格式。
- 密码不落明文；错误密码、篡改 token、过期 token、缺失 token。
- 禁用账号拿旧 token 访问；两个用户请求不会串号。
- 同时注册同一账号，数据库最终只存在一条。
- 重置凭证错误或过期；重置后旧密码不能登录。

## 人工验收

用浏览器或 HTTP Client 注册 A/B 两个账号；分别登录并调用 `/me`，确认身份不串。修改 token 的一个字符应得到 401。直接查询数据库确认只有密码哈希。

## 参考入口

对照项目的 `UserController`、`UserServiceImpl`、`JwtUtils`、`JwtInterceptor`、`WebMvcConfig` 和 `user.sql`。先完成自己的设计，再检查参考实现是否存在明文密码、异常状态码或禁用后旧 token 仍可用等问题。

## 已实现代码的阅读顺序

1. `schema.sql`：先看数据库唯一约束和 `token_version`。
2. `RegisterDTO`、`LoginDTO`、`ResetPasswordDTO`：理解输入为什么统一使用 DTO。
3. `AccountController`：只负责 HTTP 映射、接收 DTO 和返回 VO。
4. `AccountService`：先看业务接口提供了哪些方法。
5. `AccountServiceImpl`：观察事务、冲突处理、登录错误和修改密码流程。
6. `UserAccountMapper`、`UserAccountEntity`：理解 Mapper 如何操作 Entity 对应的数据表。
7. `JwtUtils`、`AuthenticationInterceptor`、`WebMvcConfig`：追踪 Bearer Token 到 CurrentUserDTO 的过程。
8. `AccountApiTest`：把测试当成可执行的功能验收文档。

## 启动与切换数据库

默认运行：

```powershell
mvn spring-boot:run
```

默认数据保存在项目的 `data/` 中。连接 MySQL 时先创建 `rebuilt_zijing_forum` 数据库，再配置环境变量并启用 profile：

```powershell
$env:ZIJING_DB_USERNAME='root'
$env:ZIJING_DB_PASSWORD='你的密码'
$env:ZIJING_JWT_SECRET='至少32字节且不要提交到仓库的随机字符串'
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

`schema.sql` 使用 `CREATE TABLE IF NOT EXISTS` 创建 `user_account`，启动时不会删除已有数据。

## 完成标准

你能不看代码画出“登录请求 -> 密码验证 -> token -> 拦截器 -> 当前用户”的完整链路，并解释 401 与 403 的区别。
