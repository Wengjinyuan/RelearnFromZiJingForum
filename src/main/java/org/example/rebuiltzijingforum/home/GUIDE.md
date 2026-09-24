# F09 home：首页统计与热门标签

## 这一包交付什么

完成首页总用户、总帖子、今日/昨日发帖数和热门标签。先做 MySQL 聚合，再加 Redis Cache Aside；训练统计口径、缓存重建、防击穿和时间边界。

## 在 IDEA 中按顺序创建

```text
home/（首页功能根包：负责站点统计和热门标签）
├─ controller/（Controller 包：首页查询接口）
│  └─ HomeController.java（Controller：首页统计和热门标签）
├─ vo/（VO 包：首页输出数据）
│  ├─ HomeStatisticsVO.java（VO：总用户、总帖子、今日/昨日帖子和更新时间）
│  └─ HotTagVO.java（VO：热门标签名称和排名分数）
├─ service/（Service 业务接口包）
│  ├─ HomeService.java（Service：定义首页统计和热门标签查询）
│  └─ impl/（ServiceImpl 业务实现包）
│     └─ HomeServiceImpl.java（ServiceImpl：MySQL 聚合、Redis 缓存、回源和降级）
└─ mapper/（Mapper 数据库访问包）
   └─ HomeMapper.java（Mapper：执行首页聚合 SQL）
```

## API 契约

- `GET /api/home`：返回统计值和 `calculatedAt`，便于理解缓存延迟。
- `GET /api/home/hot-tags?limit=10`：返回热门标签及分数/使用次数。

接口可匿名访问。依赖故障时返回可解释的旧值或明确错误，不允许无声返回全 0。

## 先固定统计口径

- 总帖子是否只算已发布且未删除。
- 今日/昨日使用 `Asia/Shanghai` 还是部署时区。
- 热门标签按累计使用次数还是时间衰减热度。
- 可以接受多少秒/分钟延迟。

把这些口径写进测试名称和接口文档，不能只藏在 SQL 中。时间计算注入 `Clock`，测试不要直接依赖系统当前时间。

## 分阶段实现

1. 为四项统计分别写聚合 SQL，用固定测试数据核对。
2. 实现无缓存版本和空站点行为。
3. 添加 Redis Hash/ZSet 缓存、TTL 和 cache-aside 回源。
4. 缓存失效时使用短锁/单飞；抢锁失败可返回稍旧快照，不让所有请求同时 COUNT。
5. 添加定时全量校准、失败日志和指标；缓存必须能从 MySQL 重建。

## 必测用例

- 空站点、删除/驳回帖子不计入、热门标签并列。
- 23:59:59、00:00:00、昨日边界和固定业务时区。
- 缓存命中、未命中、过期、手动删除和 Redis 停机。
- 多实例同时刷新时不会形成缓存击穿。
- 定时校准失败保留旧值并可重试。

## 人工验收

记录首页基线，然后注册、发帖、删除/驳回并核对变化。删除 Redis key 后访问应从 MySQL 重建。用 SQL COUNT 与接口值对比，并确认响应中的更新时间符合延迟预期。

## 参考入口

对照 `HomeController`、`HomeServiceImpl`、首页统计刷新实现、`HomeStatisticsVO`、`TagRankVO`、Redis Config 和前端 `index.html`。

## 完成标准

能区分实时计数、定时快照和物化结果，并解释为什么首页统计适合“可重建且允许短暂延迟”的缓存。
