# Java 包与文件命名说明

本项目统一使用常见的 Spring Boot 分层名称，不使用 Repository、Gateway、Row、Session、View、Request 等额外名称。

## 固定调用路线

```text
浏览器 HTTP 请求
      ↓
Controller（接收请求）
      ↓
DTO（保存输入参数）
      ↓
Service（业务接口）
      ↓
ServiceImpl（业务实现、事务、权限）
      ↓
Mapper（执行数据库操作）
      ↓
Entity（对应数据库表）
      ↓
MySQL

ServiceImpl 处理完成后
      ↓
VO（只保留允许返回给前端的字段）
      ↓
Controller 返回 JSON
```

## 每个包是什么意思

### `controller`（控制器包）

放 `XxxController.java`。负责定义 URL、接收 DTO、调用 Service、返回 VO。不在 Controller 中写 SQL 或复杂业务。

### `dto`（输入数据包）

DTO 是 Data Transfer Object。放 `RegisterDTO.java`、`LoginDTO.java` 等接收请求或后端内部传递的数据。DTO 不直接对应数据库表。

### `vo`（输出数据包）

VO 是 View Object。放 `AccountVO.java`、`LoginVO.java` 等返回给前端的数据。VO 可以隐藏密码、Token 版本等内部字段，并对邮箱和手机号脱敏。

### `service`（业务接口包）

放 `XxxService.java` 接口，只写业务方法名称，例如 `login()`、`register()`，不写具体实现。

### `service.impl`（业务实现包）

放 `XxxServiceImpl.java`，实现 Service 接口。事务、权限、查重、密码校验和调用 Mapper 的顺序都写在这里。

### `mapper`（数据库访问包）

放 `XxxMapper.java`。Mapper 通常是 MyBatis/MyBatis-Plus 接口，负责增删改查数据库。

### `entity`（数据库实体包）

放 `XxxEntity.java`。一个 Entity 通常对应一张数据库表，例如 `UserAccountEntity` 对应 `user_account`。

### `config`（配置包）

放 `XxxConfig.java`。负责创建 Spring Bean、注册拦截器路径等配置。

### `interceptor`（拦截器包）

放 `XxxInterceptor.java`。在 Controller 执行前处理登录 Token、权限或日志。

### `utils`（工具包）

放 `XxxUtils.java`。例如 `JwtUtils` 负责生成和解析 JWT。工具类不负责完整业务流程。

## Java 文件类型

| 类型 | 作用 | 例子 |
| --- | --- | --- |
| `class` | 普通类，保存数据或实现功能 | `AccountController`、`AccountServiceImpl`、`UserAccountEntity` |
| `interface` | 接口，只规定有哪些方法 | `AccountService`、`UserAccountMapper` |
| `record` | 简洁的数据类，适合 DTO 和 VO | `LoginDTO`、`AccountVO` |
| `enum` | 固定值集合，例如状态和类型 | 后续可使用 `PostStatusEnum` |

## Account 的实际结构

```text
account/
├─ controller/AccountController.java（Controller）
├─ dto/
│  ├─ RegisterDTO.java（DTO）
│  ├─ LoginDTO.java（DTO）
│  └─ ResetPasswordDTO.java（DTO）
├─ vo/
│  ├─ AccountVO.java（VO）
│  └─ LoginVO.java（VO）
├─ service/AccountService.java（Service 接口）
├─ service/impl/AccountServiceImpl.java（Service 实现）
├─ mapper/UserAccountMapper.java（Mapper 接口）
├─ entity/UserAccountEntity.java（Entity）
├─ config/AccountConfig.java（配置）
├─ config/WebMvcConfig.java（MVC 配置）
├─ interceptor/AuthenticationInterceptor.java（登录拦截器）
└─ utils/JwtUtils.java（JWT 工具）
```

登录请求的实际路线是：

```text
LoginDTO
  → AccountController
  → AccountService
  → AccountServiceImpl
  → UserAccountMapper
  → UserAccountEntity
  → JwtUtils
  → LoginVO
```

## 十一个功能包的含义

| 包名 | 中文含义 | 主要职责 |
| --- | --- | --- |
| `account` | 账号 | 注册、登录、鉴权、密码 |
| `profile` | 个人资料 | 昵称、签名、专业、头像 |
| `post` | 帖子 | 发帖、列表、详情、标签、分类 |
| `comment` | 评论 | 评论、回复、评论树、删除 |
| `interaction` | 互动 | 点赞、收藏、浏览计数 |
| `follow` | 关注 | 关注/取关、粉丝、关注流 |
| `notification` | 通知 | 通知列表、未读、异步事件 |
| `search` | 搜索 | 帖子/用户搜索、索引同步 |
| `home` | 首页 | 站点统计、热门标签 |
| `message` | 私信 | 会话、消息历史、未读、实时推送 |
| `admin` | 后台管理 | 用户处罚、帖子治理、审计 |
