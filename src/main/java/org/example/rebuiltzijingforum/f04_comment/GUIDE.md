# F04 comment：评论与楼中楼回复

从需求到技术选型、替代方案与缺陷账本：[DesignFromZeroF04.md](DesignFromZeroF04.md)。

> 本包已按下方目录图建立 Controller、DTO、VO、Service 接口/Impl、Mapper、Entity。逐方法理解当前实现，请读同目录的 [NeedToUnderstandingF04.md](NeedToUnderstandingF04.md)；本文保留功能清单与验收入口。图片 URL 已支持、图片上传未实现；实际表以 `schema.sql` 为准。

## 这一包交付什么

完成顶级评论、回复、分页查询和删除展示规则。重点是树结构建模、目标一致性校验、批量装配和软删除语义。

## 在 IDEA 中按顺序创建

```text
f04_comment/（评论功能根包：负责顶级评论、楼中楼回复和删除展示）
├─ controller/（Controller 包：评论 HTTP 接口）
│  └─ CommentController.java（Controller：发表评论、查回复和删除）
├─ dto/（DTO 包：接收评论和回复参数）
│  ├─ CreateCommentDTO.java（DTO：正文、可选父评论和图片 URL；帖子 id 从 URL、回复对象从数据库推算）
│  └─ CommentPageDTO.java（DTO：评论分页参数）
├─ vo/（VO 包：返回评论树）
│  └─ CommentVO.java（VO：评论、作者和回复列表）
├─ service/（Service 业务接口包）
│  ├─ CommentService.java（Service：定义评论、回复、查询和删除方法）
│  └─ impl/（ServiceImpl 业务实现包）
│     └─ CommentServiceImpl.java（ServiceImpl：事务、权限、计数和评论树装配）
├─ mapper/（Mapper 数据库访问包）
│  └─ CommentMapper.java（Mapper：评论表增删改查）
└─ entity/（Entity 数据库实体包）
   └─ CommentEntity.java（Entity：对应 comment 表）
```

## API 契约

- `POST /api/posts/{postId}/comments`：`parentId` 为空时创建顶级评论；非空时创建回复。
- `GET /api/posts/{postId}/comments?page=1&size=20`：分页顶级评论并批量附带每棵楼最多 3 条回复。
- `GET /api/comments/{parentId}/replies?beforeId=...`：加载更早回复。
- `DELETE /api/comments/{id}`：作者或管理员删除，返回稳定结果。

## 数据设计

`comment(id, post_id, author_id, parent_id, root_id, reply_user_id, content, image_urls, like_count, deleted, created_at, deleted_at)`。查询索引以 `schema.sql` 为准。parent_id 为空表示顶级评论，root_id 表示所属根楼；回复回复时直接 parent_id 仍指向被回复者，但列表按 root_id 归到同一棵楼。

创建回复时必须验证：帖子存在且可评论、父评论存在、父评论属于同一帖子、replyUser 合法。不能只相信前端提交的 postId/replyUserId。

## 实现顺序

1. 明确只支持两层还是任意深度；本项目建议两层展示。
2. 实现顶级评论和回复的领域校验。
3. 在事务中插入评论并更新帖子 comment_count，失败整体回滚。
4. 查询时先分页顶级评论，再一次查询该页 parentId 对应回复，再批量查用户并内存组装。
5. 实现软删除：当前代码无论有无回复都显示“该评论已删除”；删除成功时帖子计数减一，根评论分页总数仍包含占位根评论。

## 必测用例

- 不存在/已删除帖子、跨帖子 parentId、回复已删除父评论。
- 空内容、超长内容、图片数量上限、XSS。
- A 删除 B 评论、管理员删除、重复删除。
- 删除有子回复的父评论后，回复仍能理解上下文。
- 同时发表评论的计数正确；分页排序稳定且无 N+1。

## 人工验收

A 发顶级评论，B 回复 A，再由 A/B 分别删除自己的内容；刷新后树结构和占位符合约定。构造跨帖 parentId 请求必须失败。检查帖子 comment_count 与有效评论口径一致。

## 参考入口

对照 `PostController` 中评论接口、`InteractionServiceImpl`、`CommentMapper`、`Comment`、`CommentCreateDTO`、`CommentVO` 和 `post/detail.html`。重点检查参考实现是否校验父评论归属、删除后计数和批量查询。

## 完成标准

你能解释邻接表模型、两层展示与任意深度树的取舍，并能写出不会 N+1 的评论页查询流程。
