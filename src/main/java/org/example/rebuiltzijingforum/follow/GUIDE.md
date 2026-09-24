# F06 follow：关注、粉丝与关注流

## 这一包交付什么

完成关注/取关、关注状态、关注列表、粉丝列表和拉模式关注流。训练关系表、双向冗余计数、事务和幂等。

## 在 IDEA 中按顺序创建

```text
follow/（关注功能根包：负责关注关系、粉丝列表和关注流）
├─ controller/（Controller 包：关注、取关、列表和 Feed 接口）
│  └─ FollowController.java（Controller：定义关注相关 HTTP 接口）
├─ dto/（DTO 包：关注列表和 Feed 分页参数）
│  └─ FollowPageDTO.java（DTO：页码和每页数量）
├─ vo/（VO 包：关注状态、用户卡片和 Feed 输出）
│  ├─ FollowStatusVO.java（VO：当前用户是否已关注目标）
│  ├─ FollowUserVO.java（VO：关注/粉丝列表用户信息）
│  └─ FollowFeedVO.java（VO：关注流帖子信息）
├─ service/（Service 业务接口包）
│  ├─ FollowService.java（Service：定义关注、取关、列表和 Feed 方法）
│  └─ impl/（ServiceImpl 业务实现包）
│     └─ FollowServiceImpl.java（ServiceImpl：关系事务、双向计数和拉模式 Feed）
├─ mapper/（Mapper 数据库访问包）
│  └─ FollowMapper.java（Mapper：关注关系表增删改查）
└─ entity/（Entity 数据库实体包）
   └─ FollowEntity.java（Entity：对应 follow 表）
```

## API 契约

- `PUT /api/follows/{targetUserId}`、`DELETE /api/follows/{targetUserId}`。
- `GET /api/follows/{targetUserId}/status`。
- `GET /api/users/{userId}/following`、`GET /api/users/{userId}/followers`。
- `GET /api/follows/feed?page=1&size=20`：当前用户关注流。

自己不能关注自己；重复关注/取关幂等；不存在目标返回明确错误；被禁用用户如何展示需先定规则。

## 数据与事务

`follow(id, follower_id, followed_id, created_at)`，`(follower_id, followed_id)` 唯一，并分别建立两个方向的分页索引。

新增关系、当前用户 following_count +1、目标用户 follower_count +1 必须同事务；取消时反向且不能减为负数。用关系表 COUNT 做定期对账，冗余计数不是事实源。

关注流第一版：先查询当前用户关注 ID，再分页查询这些作者的已发布帖子，按 `(created_at, id)` 稳定倒序。关注数很大时避免巨大 IN，可改 join 或游标查询；此阶段不做推模式 Feed。

## 实现顺序

1. 实现 FollowEntity、FollowMapper、唯一约束和关注/取关事务。
2. 实现两个方向的稳定分页列表。
3. 实现关注状态，详情页不根据本地按钮猜测。
4. 实现拉模式关注流并测试分页稳定性。
5. 事务提交后发布 FOLLOW 领域事件；通知失败不能回滚关系。

## 必测用例

- 关注自己、不存在用户、重复关注、重复取关。
- 并发关注同一用户，数据库最多一条关系，两个计数只加一次。
- 事务中任意计数更新失败，关系和计数全部回滚。
- 双向列表分页、计数对账、禁用/删除用户策略。
- 关注后能看到新帖，取关后不再看到后续帖子。

## 人工验收

A 关注 B，双方页面的状态、计数和列表一致；B 发帖后 A 的关注流可见。快速重复点击不产生重复关系。手工篡改计数后运行对账能修复。

## 参考入口

对照 `UserController` 的 follow 接口、`FollowServiceImpl`、`FollowMapper`、`Follow`、`UserCardVO`、`user/following.html`、`user/followers.html` 和 `user/feed.html`。

## 完成标准

能说明关系表为什么是真相、冗余计数为何有价值，以及拉模式、推模式和推拉结合的适用条件。
