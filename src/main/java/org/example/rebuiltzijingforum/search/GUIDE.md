# F08 search：帖子与用户搜索

## 这一包交付什么

完成帖子/用户关键词搜索、分页、排序和高亮。先用 MySQL 实现产品行为，再接 Elasticsearch；MySQL 始终是事实源，ES 是可重建副本。

## 在 IDEA 中按顺序创建

```text
search/（搜索功能根包：负责帖子/用户搜索、索引同步和故障降级）
├─ controller/（Controller 包：帖子和用户搜索接口）
│  └─ SearchController.java（Controller：接收关键词、分页和排序）
├─ dto/（DTO 包：搜索输入参数）
│  └─ SearchDTO.java（DTO：关键词、页码、每页数量和排序）
├─ vo/（VO 包：搜索结果和高亮输出）
│  ├─ PostSearchVO.java（VO：帖子搜索结果）
│  └─ UserSearchVO.java（VO：用户搜索结果）
├─ service/（Service 业务接口包）
│  ├─ SearchService.java（Service：定义帖子/用户搜索方法）
│  ├─ SearchIndexService.java（Service：定义索引新增、更新、删除和重建方法）
│  ├─ SearchSyncService.java（Service：定义增量同步消息处理方法）
│  └─ impl/（ServiceImpl 业务实现包）
│     ├─ SearchServiceImpl.java（ServiceImpl：ES 查询和 MySQL 降级）
│     ├─ SearchIndexServiceImpl.java（ServiceImpl：索引新增、更新、删除和重建）
│     └─ SearchSyncServiceImpl.java（ServiceImpl：使用 @RabbitListener 接收增量同步消息）
├─ entity/（Entity 搜索文档实体包）
│  ├─ EsPostEntity.java（Entity：Elasticsearch 帖子文档）
│  └─ EsUserEntity.java（Entity：Elasticsearch 用户文档）
└─ config/（Config 配置包）
   └─ ElasticsearchConfig.java（Config：配置 Elasticsearch 客户端）
```

## API 契约

- `GET /api/search/posts?q=...&page=1&size=20&sort=relevance`。
- `GET /api/search/users?q=...&page=1&size=20`。

预先定义空关键词、特殊字符、最大长度、页码和 size 上限。高亮片段只允许受控标签并再次清洗，不能把 ES 返回内容原样注入页面。

## 索引设计

定义 `EsPostEntity` 和 `EsUserEntity`，明确中文分词器、可搜索字段、字段权重、过滤字段和排序字段。删除、驳回或禁用的数据不可出现。索引 Entity 记录 sourceUpdatedAt/version，防止乱序旧事件覆盖新状态。

## 分阶段实现

1. MySQL 版：用受控 LIKE/全文索引实现最小搜索，把产品规则和 API 测通。
2. ES 版：创建 mapping、别名和测试数据，实现 multi_match、高亮和排序。
3. 增量同步：事务提交后发事件；消费者拿 id 回 MySQL 读取最新状态再 upsert/delete，不直接相信旧事件正文。
4. 全量重建：新索引 -> 批量导入 -> 数量与抽样校验 -> 别名原子切换 -> 延迟删除旧索引。
5. 降级：小规模可回退 MySQL；否则明确返回 503，其他功能继续可用。

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
