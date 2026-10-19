# 紫荆论坛复现工程

这是根据《紫荆论坛功能倒推复现手册》创建的教学型 Spring Boot 骨架。它采用“按功能纵向分包”，不是把 Controller、Service、Mapper 全部堆在全局分层包里，也没有复制参考项目的现成实现。

## 在 IDEA 中打开

1. `File -> Open`，选择 `E:\REBUILTZIJINGFORUM\pom.xml`。
2. Project SDK 选择 JDK 17 或更高版本；Maven 编译目标固定为 Java 17。
3. 等 Maven 同步完成，运行 `RebuiltZijingForumApplication`。
4. 浏览器访问 `http://localhost/api/health` 和 `http://localhost/api/features`（默认端口在 `application.yml` 中是 80）。
5. 运行 `BaselineApiTest`，确认工程基线通过。

命令行也可以执行：

```powershell
mvn test
mvn spring-boot:run
```

## 前端与 H2 演示数据

原项目的 `frontend/html` 页面已复制到 `src/main/resources/static`，由 Spring Boot 直接提供。启动后打开 `http://localhost/`；若使用 `--server.port=18080`，改为 `http://localhost:18080/`。不需要另起 Nginx。前端 `js/axios-config.js` 把原页面的旧接口路径与响应格式转换为本工程的 `/api/...` 接口。

默认仍使用文件型 H2：`./data/rebuilt-zijing-forum`。第一次启动会补充四个演示账号、四篇帖子、一条评论、一条关注关系和一条通知；后续启动按账号和帖子标题检查，不会重复生成，也不会清空已有练习数据。演示账号为 `demo_alice`、`demo_bob`、`demo_admin`、`demo_super_admin`，密码均为 `Demo12345`。只有 `demo_super_admin` 能授予或撤销管理员；普通管理员负责内容和用户治理。演示管理账号只用于本地练习。可通过 `forum.demo-data.enabled=false` 关闭自动补充；`mysql` profile 已默认关闭它。

建议先打开首页查看帖子，再用 `demo_alice` 登录测试发帖、评论、关注。头像和富文本图片已经接入 F12 `ImageStorage`：默认写 `./uploads`，设 `FORUM_STORAGE_TYPE=oss` 后可切到阿里云 OSS。

AI 搜索采用 DeepSeek 的 OpenAI 兼容接口，默认关闭且不会把密钥写进仓库。启用前设置环境变量 `FORUM_AI_ENABLED=true`、`FORUM_AI_API_KEY=你的密钥`；可选配置 `FORUM_AI_URL` 与 `FORUM_AI_MODEL`。它先把自然语言问题改写为关键词/分类，再检索站内帖子，最后只基于检索结果生成带帖子来源的回答。原项目仅在测试类直接调用过 `DeepSeekChatModel`，没有可用的论坛 AI 搜索业务链，因此这里实现的是新的检索增强链路，不冒充原项目已有功能。

## 包与功能一一对应

| 顺序 | 功能包 | 目标功能 | 包内指导 |
| --- | --- | --- | --- |
| 1 | `f01_account` | F01 账号与鉴权 | `f01_account/GUIDE.md` |
| 2 | `f02_profile` | F02 资料与头像 | `f02_profile/GUIDE.md` |
| 3 | `f03_post` | F03 帖子与标签 | `f03_post/GUIDE.md` |
| 4 | `f04_comment` | F04 评论与回复 | `f04_comment/GUIDE.md` |
| 5 | `f05_interaction` | F05 点赞收藏浏览 | `f05_interaction/GUIDE.md` |
| 6 | `f06_follow` | F06 关注与关注流 | `f06_follow/GUIDE.md` |
| 7 | `f07_notification` | F07 通知中心 | `f07_notification/GUIDE.md` |
| 8 | `f08_search` | F08 搜索 | `f08_search/GUIDE.md` |
| 9 | `f09_home` | F09 首页与热门标签 | `f09_home/GUIDE.md` |
| 10 | `f10_message` | F10 私信 | `f10_message/GUIDE.md` |
| 11 | `f11_admin` | F11 后台治理 | `f11_admin/GUIDE.md` |
| 12 | `f12_media` | F12 图片存储 | `f12_media/GUIDE.md` |

功能根包使用 `f01_` 到 `f12_` 前缀，IDEA 按名称展示时就是建议实现顺序。

## 每个功能包内部的固定结构

第一次看这些名称时，先读 [Java 包与文件类型说明](docs/01-Java包与文件类型说明.md)。F01–F03 使用以 MyBatis-Plus 为主的分层示例；F04–F11 为了把 SQL 每一部分直接展示给初学者，使用 `Controller → Service 接口 → ServiceImpl → Mapper(JdbcTemplate) → MySQL/H2`，并分别放置 DTO、VO、Entity。后者的一些模型用 Java `record` 表达，以实际代码为准。

开始某个功能时，在对应包中按下面结构创建代码：

```text
feature/
├─ controller/       Controller：HTTP 接口
├─ dto/              DTO：接收请求或后端内部传递数据
├─ vo/               VO：返回给前端的数据
├─ service/          Service：业务接口
│  └─ impl/          ServiceImpl：业务实现和事务
├─ mapper/           Mapper：MyBatis 数据访问接口
├─ entity/           Entity：数据库表实体
├─ config/           Config：Spring 配置
├─ interceptor/      Interceptor：登录拦截器
├─ utils/            Utils：JWT、文件等工具类
└─ GUIDE.md          该功能的一对一复现手册
```

调用方向固定为 `前端 -> 拦截器（需登录时）-> Controller -> DTO -> Service 接口/ServiceImpl -> Mapper -> MySQL`；查询再由 Entity 转 VO 返回前端。F04–F11 已拆分业务接口和 `impl/` 实现。

## 复现纪律

- 每次只做一个纵向闭环，先让 MySQL/同步版本正确，再增加 Redis、RabbitMQ、Elasticsearch。
- Controller 不写业务规则，数据库实体不直接作为 API 响应。
- 每个写操作都回答四个问题：谁能做、重复做怎样、并发怎样、失败后怎样恢复。
- 每完成一包，必须运行该包文档中的自动化与人工验收清单。
- 参考项目用于对照，不直接复制整类；先自己设计，再读参考入口找差异。

## 当前已完成与未完成

已完成：Maven 基线、统一响应与异常、健康检查、F01–F03 基础功能，以及 F04–F11 的 MySQL/H2 同步版接口、分包 DTO/VO/Entity/ServiceImpl/Mapper、调用链注释和跨功能测试。F04 评论图片地址、批量回复预览和软删除时间已实现；F10 另有 SSE 单向推送。初学者先读 [F04–F11 从前端到数据库](docs/F04-F11-从前端到数据库.md)，再按 Controller → ServiceImpl → Mapper 阅读。

可选进阶代码：打开 `forum.redis.enabled` 后，F07 未读数与 F09 首页使用 Redis 快照/短锁；打开 `forum.rabbit.enabled` 后，F07 使用 Outbox + RabbitMQ 生产/消费/死信队列；打开 `forum.search.elasticsearch.enabled` 后，F08 使用 ES 索引、事务提交后增量同步和 MySQL 降级。搜索结果已有安全高亮，F08 正式 AI 搜索也已实现“改写 → 站内检索 → 带来源回答”。F12 头像/帖子图片默认本地存储，OSS 按配置可选。默认外部服务全部关闭；未拿真实 Redis/RabbitMQ/Elasticsearch/OSS 凭据联调。

仍未完成：F05 Redis 异步计数、F08 持久 MQ 索引事件/ES 原生分词高亮、F10 图片私信和跨实例推送。F03 新帖默认发布，F11 虽有审核接口但尚无默认待审核工作流。

## Docker 快速启动

```powershell
Copy-Item .env.example .env
# 编辑 .env，替换 MySQL 密码和 32+ 字节 JWT secret
docker compose up -d --build
docker compose ps
```

默认只启动应用和 MySQL；`--profile redis`、`--profile rabbit`、`--profile search` 或 `--profile full` 启动可选中间件，同时还要在 `.env` 打开对应 `FORUM_*_ENABLED`。完整说明见 [Docker 部署指南](docs/Docker部署.md)。

旧库升级：H2 启动时会自动补 F04 新增的 `image_urls/deleted_at`；已有旧版 MySQL 表时，先检查字段，再手动执行一次 [MySQL 迁移脚本](src/main/resources/migration-mysql-20261008.sql)。不要直接删除已有练习数据。

本地 `mvn test` 使用 H2；MySQL profile 使用独立的 `schema-mysql.sql`，但本轮尚未连接真实 MySQL 做运行验证。

F11 管理端需要 `ADMIN` 或 `SUPER_ADMIN`。默认 H2 的 `demo_super_admin` 已可管理角色；MySQL 首次部署需要通过受控初始化流程建立唯一超级管理员，不能向公网开放自助提权接口。角色变化会递增 `token_version`，用户必须重新登录。
