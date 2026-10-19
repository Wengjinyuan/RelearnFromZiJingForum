# OSS 上传、AI 搜索与 Docker 部署设计

## 1. 目标与边界

本次改造为 REBUILTZIJINGFORUM 增加三条可运行、可测试、可学习的功能链：

1. 图片上传默认使用本地磁盘，也能通过配置切换到阿里云 OSS。
2. 保留并完善 F08 站内 AI 搜索；用户正在学习的 LangChain4j 页面仍作为独立实验功能。
3. 使用 Docker Compose 默认启动 Spring Boot 应用与 MySQL，并按需启动 Redis、RabbitMQ 和 Elasticsearch。

本次不把云服务凭据写进仓库，不强制本地开发者拥有 OSS 或 DeepSeek 密钥，也不把原项目中只有测试调用的 DeepSeek 代码描述成已经存在的站内 AI 搜索。

## 2. 总体架构

```text
图片上传
前端 -> AuthenticationInterceptor -> 头像/帖子上传接口
     -> ImageValidator
     -> ImageStorage
        |- LocalImageStorage（默认）
        `- AliyunOssImageStorage（配置后启用）
     -> 返回统一图片 URL

论坛 AI 搜索
search.html -> /api/search/ai
            -> AiSearchService
            -> AiChatClient 改写查询条件
            -> SearchService 检索站内帖子
            -> AiChatClient 根据候选帖子生成回答
            -> 回答 + 检索关键词 + 来源帖子

独立学习页面
/langchian/index.html -> /ai/ask
                      -> LangChain4j
                      -> 独立开关与密钥，不替代 F08

Docker
浏览器 -> Spring Boot 容器 -> MySQL 容器
                         |- Redis（可选 profile）
                         |- RabbitMQ（可选 profile）
                         `- Elasticsearch（可选 profile）
```

## 3. 图片存储设计

### 3.1 模块边界

新增 `f12_media` 作为跨头像和帖子功能复用的媒体模块。F02 与 F03 保留现有 HTTP 路由，只把文件校验、命名和保存委托给媒体模块，以免破坏现有前端。

核心职责如下：

| 组件 | 职责 |
| --- | --- |
| `ImageStorage` | 定义保存和删除图片的统一接口 |
| `StoredImage` | 同时携带对象 key 和公开 URL，供数据库写入与失败补偿使用 |
| `ImageValidator` | 校验空文件、大小、MIME、真实图片内容并生成受控扩展名 |
| `LocalImageStorage` | 默认写入本机 `uploads/`，返回 `/uploads/...` URL |
| `AliyunOssImageStorage` | 使用阿里云 OSS SDK 上传或删除对象，返回可访问 URL |
| `StorageProperties` | 承载存储类型和 OSS 配置，不包含仓库内默认密钥 |

### 3.2 兼容接口

- 头像：`POST /api/profiles/me/avatar`
- 帖子图片：`POST /api/posts/images`

两个接口继续使用 multipart 字段 `file`。帖子接口继续返回 wangEditor 能识别的 `errno` 与 `data.url`；头像接口继续返回统一 `ApiResponse<String>`。

### 3.3 对象命名

```text
avatars/{userId}/{UUID}.{ext}
posts/{yyyy}/{MM}/{UUID}.{ext}
```

扩展名只根据校验后的 MIME/图片签名生成，不拼接客户端文件名。对象 key 必须是相对路径，不允许 `..`、盘符或绝对路径。

### 3.4 配置

默认配置：

```yaml
forum:
  storage:
    type: ${FORUM_STORAGE_TYPE:local}
    local-root: ${FORUM_STORAGE_LOCAL_ROOT:./uploads}
    oss:
      endpoint: ${ALIYUN_OSS_ENDPOINT:}
      region: ${ALIYUN_OSS_REGION:}
      bucket: ${ALIYUN_OSS_BUCKET:}
      access-key-id: ${ALIYUN_OSS_ACCESS_KEY_ID:}
      access-key-secret: ${ALIYUN_OSS_ACCESS_KEY_SECRET:}
      public-base-url: ${ALIYUN_OSS_PUBLIC_BASE_URL:}
```

`type=local` 时不创建 OSS 客户端。`type=oss` 时必须校验 endpoint、region、bucket 与密钥；缺失时以清晰的配置错误阻止错误部署，而不是等到第一次上传才返回空 URL。

### 3.5 一致性与补偿

头像上传顺序为：校验文件 -> 保存新对象 -> 更新 `user_profile.avatar_url` -> 返回 URL。数据库更新失败时删除本次新对象。只有当旧头像属于当前受管存储且新头像数据库提交成功后，才允许清理旧头像；不能删除外部 URL 或默认头像。

帖子图片在编辑器选择图片时已经上传，用户可能放弃发布，因此可能产生孤儿对象。本轮记录该边界并保留可追踪的 `posts/年/月/` 路径，不额外引入上传会话表和定时清理任务。

## 4. AI 搜索设计

### 4.1 正式功能与学习功能分离

F08 是论坛正式 AI 搜索。它复用论坛普通搜索，只允许根据站内候选帖子回答，并向前端返回来源。`learninglangchian` 是独立学习页面，用来观察最小模型调用，不替换 F08，也不被 F08 依赖。

### 4.2 正式调用链

```text
POST /api/search/ai
-> AuthenticationInterceptor（必须登录）
-> AiSearchController
-> AiSearchServiceImpl
-> AiChatClient.rewrite(question)
-> SearchService.posts(keyword, category, page=1, size=6)
-> AiChatClient.answer(question, sources)
-> AiSearchResponse(answer, rewrittenKeyword, category, sources)
```

当前 `AiSearchServiceImpl` 内部直接创建 JDK `HttpClient` 的职责将被提取到 `AiChatClient`。正式实现使用 DeepSeek 的 OpenAI 兼容接口，测试实现使用确定性的替身，不连接公网。

### 4.3 安全和失败语义

- 只有已登录用户能调用会产生外部费用的 AI 搜索。
- 问题去除首尾空白后必须为 1 到 300 字。
- 帖子标题和摘要属于不可信资料；系统提示词要求忽略资料中的命令。
- 生成答案必须尽量使用 `[帖子#ID]` 标注来源。
- 未检索到帖子时不调用回答模型，返回 HTTP 200 和“资料不足”的明确结果。
- 功能关闭或缺少密钥时返回 `AI_SEARCH_DISABLED` 与 HTTP 503。
- 供应商超时、非 2xx、空回答或非法响应返回 `AI_SEARCH_UNAVAILABLE` 与 HTTP 503，不向前端泄露密钥、完整上游响应或内部堆栈。
- 网络连接和单次请求必须设置有限超时；线程中断时恢复中断标志。

### 4.4 独立 LangChain4j 页面

保留 `/langchian/index.html` 与 `/ai/ask` 学习链。后端 Bean 由独立开关控制，默认关闭；密钥缺失时不影响论坛和测试启动。该功能不能共享浏览器端密钥，也不能把用户问题写进 URL 查询参数造成代理访问日志泄露；页面改用 POST 请求体发送问题。

## 5. Docker 部署设计

### 5.1 文件

```text
Dockerfile
.dockerignore
compose.yaml
.env.example
src/main/resources/application-docker.yml
docs/Docker部署.md
```

### 5.2 应用镜像

使用多阶段构建：第一阶段用 Maven 与 JDK 17 执行打包，第二阶段只保留 JRE 17 和应用 JAR。镜像以非 root 用户运行，容器内监听 8080，不把源码、测试产物、IDE 配置、数据库文件、上传文件或密钥复制到运行镜像。

### 5.3 默认服务

`docker compose up -d --build` 默认启动：

- `app`：Spring Boot 应用，启用 `docker` profile。
- `mysql`：MySQL 8，使用持久化卷和 `mysqladmin ping` 健康检查。

应用等待 MySQL 健康后启动。MySQL URL 使用服务名 `mysql`，不使用容器内的 `localhost`。上传目录在本地存储模式下挂载独立持久化卷。

### 5.4 可选服务

Redis、RabbitMQ 和 Elasticsearch 分别放入 Compose profile；`full` profile 同时启用三者。仅启动容器不代表应用功能自动开启，应用侧的 `forum.redis.enabled`、`forum.rabbit.enabled` 和 `forum.search.elasticsearch.enabled` 仍由环境变量明确控制。

所有中间件使用健康检查和数据卷。Elasticsearch 只按单节点开发环境配置，并在文档中明确它不是生产集群方案。

### 5.5 密钥管理

`.env.example` 只放变量名和无敏感示例。实际 `.env` 被 `.gitignore` 排除。以下值只能在运行环境提供：

- MySQL 密码与 JWT Secret。
- OSS AccessKey、Bucket、Endpoint 和 Region。
- DeepSeek API Key。
- RabbitMQ 用户名和密码。

## 6. 测试和验收

实现遵循测试先行，每条生产逻辑先出现会因功能缺失而失败的测试，再写最小实现。

### 6.1 自动化测试

- `ImageValidator`：空文件、超限、伪造 MIME、真实 JPG/PNG/GIF/WEBP 和安全扩展名。
- `LocalImageStorage`：路径隔离、目录创建、保存、删除和返回 URL。
- `AliyunOssImageStorage`：对象 key、Bucket、公开 URL 与异常映射；使用注入的客户端工厂，不连接真实 OSS。
- 头像流程：只能修改本人头像，数据库失败时删除新对象。
- 帖子图片：未登录拒绝、非法图片拒绝、成功响应符合 wangEditor 协议。
- AI 搜索：禁用状态、问题边界、改写失败降级、分类回退、无结果不调用回答模型、来源输出与上游失败。
- MockMvc：从鉴权拦截器经过 Controller、Service 到 H2 的完整接口行为。

### 6.2 构建与部署验证

依次运行：

```powershell
mvn test
mvn clean package
docker compose config
docker build -t rebuilt-zijing-forum:test .
docker compose up -d --build
```

若当前机器可运行 Docker，则继续检查容器健康状态，并请求 `/api/health`、注册/登录、普通搜索、本地图片上传和 AI 状态接口。验证结束后使用 `docker compose down` 停止本次容器；除非用户明确要求，不删除数据卷。

真实 OSS 上传和 DeepSeek 回答需要有效环境变量。没有密钥时，验收结论必须区分“代码及替身测试通过”和“真实云服务联调通过”，不能用前者冒充后者。

## 7. 文档与学习链

新增或更新文档时，分别给出：

- 前端请求位置。
- 鉴权或权限检查位置。
- Controller、Service、存储/AI 客户端的调用顺序。
- 配置开关和默认状态。
- 本地模式、OSS 模式与 Docker 模式的启动命令。
- 自动化测试位置和真实云服务尚未验证的边界。

README 的“当前已完成与未完成”必须随实现结果更新，不能继续把已经实现的图片上传和 AI 搜索写成未完成。
