# Docker 部署指南

这套配置默认只启动 Spring Boot 应用和 MySQL，图片写入可持久化的 `forum_uploads` volume。Redis、RabbitMQ 和 Elasticsearch 是可选 profile，不会因为 POM 里有依赖就自动启用。

## 1. 第一次启动

```powershell
Copy-Item .env.example .env
```

打开 `.env`，填写 `MYSQL_ROOT_PASSWORD`、`MYSQL_APP_PASSWORD` 和 `ZIJING_JWT_SECRET`。这三个值留空时 Compose 会直接拒绝启动；JWT secret 使用 32 字节以上的随机字符串。应用使用 `MYSQL_APP_USER` 指定的普通账号访问数据库，不使用 root。然后执行：

```powershell
docker compose up -d --build
docker compose ps
Invoke-RestMethod http://localhost:8080/api/health
```

应用对外端口由 `APP_PORT` 控制，默认为 `8080`。MySQL 使用 `mysql_data` volume，重建容器不会自动清空数据。MySQL 和可选中间件的宿主机端口只绑定 `127.0.0.1`，局域网或公网不能直接访问它们；容器之间仍通过 Compose 内部网络通信。

## 2. 可选中间件

启动容器和启用业务功能是两件事。例如 Redis：

```powershell
# 先在 .env 中设置 FORUM_REDIS_ENABLED=true
docker compose --profile redis up -d --build
```

RabbitMQ 使用 `--profile rabbit` 和 `FORUM_RABBIT_ENABLED=true`，启用前还必须填写 `RABBITMQ_PASSWORD`；Elasticsearch 使用 `--profile search` 和 `FORUM_ES_ENABLED=true`。一次启动全部容器可使用：

```powershell
docker compose --profile full up -d --build
```

## 3. 本地图片与 OSS

默认 `FORUM_STORAGE_TYPE=local`，请求链是“页面选文件 → Controller → ImageStorage → `/app/uploads`”。容器目录映射到 `forum_uploads` volume。

切换 OSS 时，将 `FORUM_STORAGE_TYPE` 改为 `oss`，并填写 `.env` 中全部 `ALIYUN_OSS_*` 值。密钥不要写入 `compose.yaml`、`application.yml` 或 Git。

## 4. AI 功能

论坛正式 AI 搜索使用 `FORUM_AI_ENABLED=true` 与 `FORUM_AI_API_KEY`。独立 LangChain4j 学习页使用 `FORUM_LEARNING_AI_ENABLED=true` 与自己的 `FORUM_LEARNING_AI_API_KEY`。两者默认都关闭，不配密钥不会阻止论坛启动。

## 5. 停止与数据边界

```powershell
docker compose down
```

这只停止并删除容器，不删除数据 volume。只有在你确认要永久删除 MySQL、图片和中间件数据时，才能使用 `docker compose down -v`。
