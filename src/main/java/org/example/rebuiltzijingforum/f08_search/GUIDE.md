# F08 search：帖子与用户搜索

从需求到技术选型、替代方案与缺陷账本：[DesignFromZeroF08.md](DesignFromZeroF08.md)。

逐方法阅读当前实现：[NeedToUnderstandingF08.md](NeedToUnderstandingF08.md)。下文是功能清单与练习路线；默认搜索走 MySQL，ES 需显式开启。

> 本包已建立目录图中的 DTO、VO、ES Entity、三个 Service 接口/Impl 与 ElasticsearchConfig；另有默认 MySQL 查询的 SearchMapper 和安全高亮工具。先读 [F04–F11 从前端到数据库](../../../../../../../docs/F04-F11-从前端到数据库.md) 的 F08 章节。ES 是可选路径，尚未用真实 ES 联调；RabbitMQ 增量同步未实现。

## 这一包交付什么

完成帖子/用户关键词搜索、分页、排序和高亮。先用 MySQL 实现产品行为，再接 Elasticsearch；MySQL 始终是事实源，ES 是可重建副本。

## 在 IDEA 中按顺序创建

```text
f08_search/（搜索功能根包：负责帖子/用户搜索、索引同步和故障降级）
├─ controller/（Controller 包：帖子和用户搜索接口）
│  ├─ SearchController.java（Controller：接收关键词、分页和排序）
│  └─ SearchMaintenanceController.java（Controller：可选 ES 的管理员重建入口）
├─ dto/（DTO 包：搜索输入参数）
│  └─ SearchDTO.java（DTO：关键词、页码、每页数量和排序）
├─ vo/（VO 包：搜索结果和高亮输出）
│  ├─ PostSearchVO.java（VO：帖子搜索结果和安全高亮片段）
│  └─ UserSearchVO.java（VO：用户搜索结果和安全高亮片段）
├─ service/（Service 业务接口包）
│  ├─ SearchService.java（Service：定义帖子/用户搜索方法）
│  ├─ SearchIndexService.java（Service：定义索引新增、更新、删除和重建方法）
│  ├─ SearchSyncService.java（Service：定义增量同步消息处理方法）
│  └─ impl/（ServiceImpl 业务实现包）
│     ├─ SearchServiceImpl.java（ServiceImpl：ES 查询和 MySQL 降级）
│     ├─ SearchIndexServiceImpl.java（ServiceImpl：索引新增、更新、删除和重建）
│     └─ SearchSyncServiceImpl.java（ServiceImpl：事务提交后按 ID 读取最新 MySQL 数据）
├─ mapper/（Mapper：MySQL 搜索和索引事实源）
│  └─ SearchMapper.java（Mapper：LIKE 查询、索引读取、重建游标）
├─ entity/（Entity 搜索文档实体包）
│  ├─ EsPostEntity.java（Entity：Elasticsearch 帖子文档）
│  └─ EsUserEntity.java（Entity：Elasticsearch 用户文档）
├─ utils/（Utils 文本处理包）
│  └─ SearchHighlighter.java（Utils：转义原文后只插入固定 em 标签）
└─ config/（Config 配置包）
   └─ ElasticsearchConfig.java（Config：配置 Elasticsearch 客户端）
```

## API 契约

- `GET /api/search/posts?q=...&page=1&size=20&sort=relevance`（兼容 `keyword`）。
- `GET /api/search/users?q=...&page=1&size=20`。
- 可选 ES 开启时，`POST /api/admin/search/rebuild`：管理员全量重建（教学版同步执行）。

预先定义空关键词、特殊字符、最大长度、页码和 size 上限。高亮片段只允许受控标签并再次清洗，不能把 ES 返回内容原样注入页面。

## 索引设计

定义 `EsPostEntity` 和 `EsUserEntity`，明确中文分词器、可搜索字段、字段权重、过滤字段和排序字段。删除、驳回或禁用的数据不可出现。索引 Entity 记录 sourceUpdatedAt/version，防止乱序旧事件覆盖新状态。

## 分阶段实现

1. MySQL 版：已用参数化 LIKE 实现搜索、分页和排序。
2. ES 版：可选开关下已实现 mapping、别名、multi_match 和排序；两种后端结果统一做安全的“完整关键词”高亮，暂未使用 ES 分词片段高亮。
3. 增量同步：事务提交后按 id 回 MySQL 读取最新状态再 upsert/delete；尚未用 RabbitMQ 持久事件，ES 停机期间可通过重建补齐。
4. 全量重建：已实现新索引 -> 分页导入 -> 数量校验 -> 别名原子切换；抽样校验和旧索引自动清理未做。
5. 降级：ES 请求失败回退 MySQL LIKE。真实 ES 联调尚未做。

## 必测用例

- 中文、英文、混合词、特殊字符、空词、无结果。
- 标题/摘要/标签/用户名/签名的权重和排序。
- 分页边界、稳定排序、size 限制和深分页策略。
- 新建、修改、删除/驳回后的索引延迟。
- 重复/乱序事件、ES 停机、全量重建期间查询。
- 高亮内容不能执行脚本。

## 人工验收

发布具有唯一关键词的帖子并记录可搜出延迟；修改后旧词不再命中；删除后搜索与详情均不可访问。停止 ES 后观察明确降级，再重建索引并校验数量。

## 参考入口

对照参考项目的 `PostController`/`UserController` 搜索接口、原有 ES 数据访问类、`EsUtil`、`ElasticsearchConfig`、消息消费者、`EsPost`、`EsUser` 和 `post/search.html`。

## 完成标准

能解释倒排索引、分词、相关度、高亮，以及为何 ES 不能成为论坛事实数据的唯一来源。
