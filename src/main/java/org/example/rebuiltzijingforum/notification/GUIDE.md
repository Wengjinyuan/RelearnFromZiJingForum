# F07 notification：通知中心

## 这一包交付什么

完成未读数、分类列表、单条已读和全部已读；先同步生成通知，再升级为 RabbitMQ 异步和 Outbox。核心是数据隔离、消费者幂等和 DB/MQ 双写可靠性。

## 在 IDEA 中按顺序创建

```text
notification/（通知功能根包：负责通知列表、未读状态和异步事件落库）
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
│     └─ NotificationMessageServiceImpl.java（ServiceImpl：使用 @RabbitListener 接收消息并调用 NotificationService）
├─ mapper/（Mapper 数据库访问包）
│  └─ NotificationMapper.java（Mapper：通知表增删改查）
├─ entity/（Entity 数据库实体包）
│  └─ NotificationEntity.java（Entity：对应 notification 表）
└─ config/（Config 配置包）
   └─ RabbitMqConfig.java（Config：配置交换机、队列、重试和死信队列）
```

## API 契约

- `GET /api/notifications/unread-count`。
- `GET /api/notifications?type=...&page=1&size=20`。
- `PUT /api/notifications/{id}/read`。
- `PUT /api/notifications/read-all`。

所有查询和更新必须带 currentUserId 条件；不能先按 id 更新再检查归属。A 猜中 B 的通知 id 仍必须 404/403。

## 数据设计

`notification(id, user_id, sender_id, type, target_id, content, amount, is_read, event_id, created_at, updated_at)`；`event_id` 或业务幂等键唯一；为 `(user_id, is_read, created_at, id)` 建索引。

聚合规则要按类型定义：同一目标的点赞可聚合 amount，评论通常逐条。自己操作自己不通知。目标删除后列表仍应安全展示占位而非报错。

## 分阶段实现

1. 同步版：业务操作完成后调用通知 Service，先把权限和已读语义做正确。
2. 缓存版：未读数优先缓存，缓存丢失必须从数据库重建，key 包含 userId。
3. MQ 版：业务事务提交后发事件；消费者以 eventId 幂等插入/聚合并手动 ACK。
4. 可靠版：业务事务写 Outbox；发布器重试发送；配置持久化、publisher confirm、有限重试和死信队列。

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
