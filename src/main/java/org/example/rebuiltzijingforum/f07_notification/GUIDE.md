# F07 notification：通知中心

从需求到技术选型、替代方案与缺陷账本：[DesignFromZeroF07.md](DesignFromZeroF07.md)。

逐方法阅读当前实现：[NeedToUnderstandingF07.md](NeedToUnderstandingF07.md)。下文是功能清单与练习路线；RabbitMQ/Redis 均需显式开启。

> 本包已有目录图中的 DTO、VO、Entity、Service 接口/Impl、Mapper、RabbitMqConfig，另有 Outbox Mapper/发布器/消费者与可选 Redis 未读数缓存。先读 [F04–F11 从前端到数据库](../../../../../../../docs/F04-F11-从前端到数据库.md) 的 F07 章节。默认同步通知并实时查 MySQL；Redis/RabbitMQ 路径尚未用真实服务联调。

## 这一包交付什么

完成未读数、分类列表、单条已读和全部已读；先同步生成通知，再升级为 RabbitMQ 异步和 Outbox。核心是数据隔离、消费者幂等和 DB/MQ 双写可靠性。

## 在 IDEA 中按顺序创建

```text
f07_notification/（通知功能根包：负责通知列表、未读状态和异步事件落库）
├─ controller/（Controller 包：通知查询和已读接口）
│  └─ NotificationController.java（Controller：未读数、列表、单条已读和全部已读）
├─ dto/（DTO 包：通知筛选和消息事件参数）
│  ├─ NotificationPageDTO.java（DTO：类型、页码和每页数量）
│  └─ NotificationEventDTO.java（DTO：MQ 中传递的通知事件）
├─ vo/（VO 包：通知列表和未读数输出）
│  ├─ NotificationVO.java（VO：单条通知）
│  └─ UnreadCountVO.java（VO：当前用户未读数量）
├─ service/（Service 业务接口包）
│  ├─ NotificationService.java（Service：定义通知查询、生成和已读方法）
│  ├─ NotificationMessageService.java（Service：定义通知消息处理方法）
│  └─ impl/（ServiceImpl 业务实现包）
│     ├─ NotificationServiceImpl.java（ServiceImpl：过滤、聚合、幂等和已读业务）
│     ├─ NotificationMessageServiceImpl.java（ServiceImpl：处理事件并调用 NotificationService.persistEvent）
│     ├─ NotificationOutboxPublisher.java（Service：提交后扫描 Outbox 并发送 MQ）
│     ├─ NotificationQueueConsumer.java（Service：@RabbitListener 消费主队列）
│     └─ NotificationUnreadCache.java（缓存组件：未读数 Redis 快照）
├─ mapper/（Mapper 数据库访问包）
│  ├─ NotificationMapper.java（Mapper：通知表增删改查）
│  └─ NotificationOutboxMapper.java（Mapper：待发布事件表）
├─ entity/（Entity 数据库实体包）
│  └─ NotificationEntity.java（Entity：对应 notification 表）
└─ config/（Config 配置包）
   └─ RabbitMqConfig.java（Config：配置交换机、队列、重试和死信队列）
```

## API 契约

- `GET /api/notifications/unread-count`。
- `GET /api/notifications?kind=...&page=1&size=20`。
- `PUT /api/notifications/{id}/read`。
- `PUT /api/notifications/read-all`。

所有查询和更新必须带 currentUserId 条件；不能先按 id 更新再检查归属。A 猜中 B 的通知 id 仍必须 404/403。

## 数据设计

当前表是 `notification(id, user_id, actor_id, kind, target_id, event_key, content, is_read, created_at)`；`event_key` 唯一。可选异步版增加 `notification_outbox(id,event_key,payload,published,created_at)`。实际索引以 `schema.sql` 为准。

当前每个事件单独一行，不做点赞 amount 聚合。自己操作自己不通知；通知列表返回保存在表里的安全文本，不强制 JOIN 已删除目标。

## 分阶段实现

1. 同步版：业务操作完成后调用通知 Service，先把权限和已读语义做正确。
2. 缓存版：开启 `forum.redis.enabled` 后先查当前用户 Redis 快照，缺失时从 MySQL 重建，写通知/已读后失效；尚未连真实 Redis 验证并发故障。
3. MQ 版：打开 `forum.rabbit.enabled` 后，业务事务写 Outbox；提交后发布器发事件；消费者以 eventKey 幂等插入，成功后由容器自动 ACK。
4. 可靠版：已配置持久队列、publisher confirm、有限重试和死信队列，但没有真实 RabbitMQ 联调。

## 必测用例

- 自己操作、重复事件、乱序事件、消费者处理后未 ACK。
- A 读取或标记 B 的通知；并发 read-all；分类过滤。
- 缓存删除后未读数从 DB 重建，不误报 0。
- MQ 停机时主业务仍成功且 Outbox 留存；恢复后补发。
- 目标已删除、发送人被禁用、通知内容不产生 XSS。

## 人工验收

A 对 B 点赞/评论/关注，B 收到通知，A 不收到自己的通知。重复投递同一事件数量不重复。暂停消费者时主业务成功；恢复后通知补到。A 构造 B 的通知 id 不得修改成功。

## 参考入口

对照通知 Controller、通知 ServiceImpl、RabbitMQ Config、通知 Mapper 和 `user/info.html` 或顶部通知展示。

## 完成标准

能解释至少一次投递为什么要求消费者幂等、DB+MQ 双写的失败窗口，以及 Outbox 如何让事件可恢复。
