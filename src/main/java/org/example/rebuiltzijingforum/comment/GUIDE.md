# F04 comment：评论与楼中楼回复

## 这一包交付什么

完成顶级评论、回复、分页查询和删除展示规则。重点是树结构建模、目标一致性校验、批量装配和软删除语义。

## 在 IDEA 中按顺序创建

```text
comment/（评论功能根包：负责顶级评论、楼中楼回复和删除展示）
├─ controller/（Controller 包：评论 HTTP 接口）
│  └─ CommentController.java（Controller：发表评论、查回复和删除）
├─ dto/（DTO 包：接收评论和回复参数）
│  ├─ CreateCommentDTO.java（DTO：帖子、父评论、回复对象和内容）
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
- `GET /api/posts/{postId}/comments?page=1&size=20`：分页顶级评论并批量附带部分回复。
- `GET /api/comments/{parentId}/replies?cursor=...`：加载更多回复。
- `DELETE /api/comments/{id}`：作者或管理员删除，返回稳定结果。

## 数据设计

`comment(id, post_id, author_id, parent_id, reply_user_id, content, image_urls, like_count, deleted, created_at, deleted_at)`。对 `(post_id, parent_id, created_at, id)` 建查询索引。parent_id 为空表示顶级评论；若只支持两层，则回复的 parent_id 必须指向顶级评论。

创建回复时必须验证：帖子存在且可评论、父评论存在、父评论属于同一帖子、replyUser 合法。不能只相信前端提交的 postId/replyUserId。

## 实现顺序

1. 明确只支持两层还是任意深度；本项目建议两层展示。
2. 实现顶级评论和回复的领域校验。
3. 在事务中插入评论并更新帖子 comment_count，失败整体回滚。
4. 查询时先分页顶级评论，再一次查询该页 parentId 对应回复，再批量查用户并内存组装。
5. 实现软删除：有回复时显示“该评论已删除”，无回复时可隐藏；计数语义必须固定。

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
