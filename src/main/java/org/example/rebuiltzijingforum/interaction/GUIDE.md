# F05 interaction：点赞、收藏与浏览计数

## 这一包交付什么

完成帖子/评论点赞、帖子收藏、状态查询和浏览计数。先做 MySQL 强一致版，再做 Redis 最终一致版；两版不要同时起步。

## 在 IDEA 中按顺序创建

```text
interaction/（互动功能根包：负责点赞、收藏、浏览量及其并发一致性）
├─ controller/（Controller 包：点赞、收藏和状态接口）
│  └─ InteractionController.java（Controller：点赞、取消、收藏、取消收藏和状态）
├─ dto/（DTO 包：互动目标和分页参数）
│  └─ InteractionDTO.java（DTO：目标类型和目标 id）
├─ vo/（VO 包：返回互动状态）
│  └─ InteractionVO.java（VO：是否点赞/收藏及当前计数）
├─ service/（Service 业务接口包）
│  ├─ InteractionService.java（Service：定义点赞、收藏和浏览计数方法）
│  └─ impl/（ServiceImpl 业务实现包）
│     └─ InteractionServiceImpl.java（ServiceImpl：关系写入、计数事务和幂等）
├─ mapper/（Mapper 数据库访问包）
│  ├─ LikeMapper.java（Mapper：点赞关系表）
│  └─ FavoriteMapper.java（Mapper：收藏关系表）
├─ entity/（Entity 数据库实体包）
│  ├─ LikeEntity.java（Entity：like_record 表）
│  └─ FavoriteEntity.java（Entity：favorite 表）
└─ utils/（Utils 工具包）
   └─ RedisLockUtils.java（Utils：Redis 锁的加锁与安全释放）
```

## API 契约

- `PUT /api/posts/{postId}/likes`、`DELETE /api/posts/{postId}/likes`。
- `PUT /api/comments/{commentId}/likes`、`DELETE /api/comments/{commentId}/likes`。
- `PUT /api/posts/{postId}/favorites`、`DELETE /api/posts/{postId}/favorites`。
- `GET /api/posts/{postId}/interactions/me`：返回当前用户是否点赞/收藏。
- 详情访问由 Service 记录浏览，不允许客户端直接提交最终计数。

显式 PUT/DELETE 比“toggle”更容易实现重试幂等；重复点赞和重复取消都返回稳定结果。

## 数据设计

- `like_record(user_id, target_type, target_id, created_at)`，组合唯一。
- `favorite(user_id, post_id, created_at)`，组合唯一。
- 冗余计数放在 post/comment，但关系表才是真相。

第一版在同一事务中插入/删除关系并原子增减计数，减法要防止小于 0。捕获唯一键冲突并转成幂等成功，而不是先查后插作为唯一防线。

## Redis 进阶版

关系可放 Set，排行放 ZSet，粗略 UV 才考虑 HLL。计数更新和脏标记使用 Lua 原子执行；回写任务要有批次幂等、失败重试和 MySQL 对账。分布式锁 token 必须唯一，只能比较持有者后删除；未获得锁绝不能释放。

## 实现顺序

1. 完成 MySQL 关系与计数事务及唯一约束。
2. 写 20～100 并发请求测试并运行对账 SQL。
3. 增加显式状态查询，页面初始化以服务端状态为准。
4. 再引入 Redis 写后缓存/延迟回写，保留 MySQL 重建能力。
5. 增加定时对账和故障恢复，不把 Redis 当唯一事实源。

## 必测用例

- 不存在目标、自己给自己业务上是否允许、重复 PUT/DELETE。
- 同一用户并发点赞；不同用户并发点赞；唯一键冲突。
- 事务中插入成功但计数更新失败必须回滚。
- Redis 重启、回写中途崩溃、多实例同时执行、计数归零。
- 浏览刷新/重试的统计口径符合事先定义。

## 人工验收

两个浏览器对同一帖子快速操作；按钮状态和计数可短暂延迟但不能永久错误。用 SQL 比较关系表 COUNT 与冗余计数。停止 Redis 后按设计降级，恢复后可重建/对账。

## 参考入口

对照帖子 Controller 的点赞/收藏接口、`InteractionServiceImpl`、`LikeMapper`、`FavoriteMapper`、Redis 锁 Utils、浏览计数实现和 Redis Config。

## 完成标准

能解释幂等与防重复的区别、为什么唯一约束仍必需，以及强一致和最终一致各自的失败窗口。
