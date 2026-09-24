# F10 message：持久化私信

## 这一包交付什么

完成双人会话列表、消息历史游标分页、发送消息、未读/已读。数据库负责历史事实；轮询、SSE 或 WebSocket 只改善实时体验，不能替代持久化。

## 在 IDEA 中按顺序创建

```text
message/（私信功能根包：负责会话、消息历史、未读和实时送达）
├─ controller/（Controller 包：会话、历史、发送和已读接口）
│  └─ MessageController.java（Controller：定义私信相关 HTTP 接口）
├─ dto/（DTO 包：发送消息和分页参数）
│  ├─ SendMessageDTO.java（DTO：clientMessageId、类型和内容）
│  └─ MessagePageDTO.java（DTO：历史消息游标和每页数量）
├─ vo/（VO 包：会话和消息输出）
│  ├─ ConversationVO.java（VO：会话列表项和未读数）
│  └─ MessageVO.java（VO：单条私信）
├─ service/（Service 业务接口包）
│  ├─ MessageService.java（Service：定义发送、历史、会话和已读方法）
│  └─ impl/（ServiceImpl 业务实现包）
│     └─ MessageServiceImpl.java（ServiceImpl：建会话、落消息、未读更新和事务）
├─ mapper/（Mapper 数据库访问包）
│  ├─ ConversationMapper.java（Mapper：会话表）
│  ├─ ConversationMemberMapper.java（Mapper：会话成员表）
│  └─ PrivateMessageMapper.java（Mapper：私信表）
├─ entity/（Entity 数据库实体包）
│  ├─ ConversationEntity.java（Entity：conversation 表）
│  ├─ ConversationMemberEntity.java（Entity：conversation_member 表）
│  └─ PrivateMessageEntity.java（Entity：private_message 表）
└─ config/（Config 实时通信配置包）
   └─ WebSocketConfig.java（Config：WebSocket/SSE 连接配置）
```

## API 契约

- `GET /api/messages/conversations?cursor=...`。
- `GET /api/messages/conversations/{conversationId}?beforeId=...&size=30`。
- `POST /api/messages/users/{targetUserId}`：请求含 `clientMessageId`、type、content。
- `PUT /api/messages/conversations/{conversationId}/read`。

每个会话接口都在 Service 检查 currentUserId 是成员。不能给自己发；空内容和非法图片拒绝。

## 数据设计

- `conversation(id, type, last_message_id, last_message_at, created_at)`。
- `conversation_member(conversation_id, user_id, unread_count, last_read_message_id)`，组合唯一。
- `private_message(id, conversation_id, sender_id, client_message_id, type, content, created_at)`，`(sender_id, client_message_id)` 唯一保证客户端重试幂等。

双人会话还需一个稳定的 pair key（较小 userId 在前）并设唯一约束，解决并发创建两个相同会话。

## 实现顺序

1. 完成三张表和会话成员授权查询。
2. 事务中“查找/创建会话 -> 插消息 -> 更新最后消息 -> 增接收方未读”。
3. 用 `(created_at, id)` 或递增 id 做历史游标分页，禁止 offset 导致消息漂移。
4. 实现会话列表、已读游标和未读归零的并发语义。
5. 第一版前端轮询；第二版再加 SSE/WebSocket。断线重连后按游标补拉数据库。

## 必测用例

- 给自己发、空内容、超长文本、图片失败。
- C 猜 A/B 会话 id，查询和已读都被拒绝。
- 重复 clientMessageId 只保存一次并返回原消息。
- A/B 并发发起会话只生成一个双人会话。
- 相同时间戳分页不重不漏；发送期间翻页仍稳定。
- 服务重启、实时通道断线重连、已读与新消息并发。

## 人工验收

A/B 两个浏览器互发消息，顺序和未读正确；重启后端后历史仍存在。发送时断网后用同一 clientMessageId 重试，数据库只一条。C 访问 A/B 会话必须被拒绝。

## 参考入口

对照 `PrivateMessageController`、`InMemoryPrivateMessageService`、`PrivateMessageVO`、`PrivateConversationVO` 和 `user/message.html`。参考项目的内存实现只用于理解接口，复现时不要沿用，因为重启会丢数据且难以多实例运行。

## 完成标准

能解释轮询、SSE、WebSocket 的差异，游标分页为何适合消息流，以及实时推送为何不能替代数据库。
