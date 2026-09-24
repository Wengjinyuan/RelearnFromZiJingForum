# F03 post：帖子、标签、分类、列表与详情

## 这一包交付什么

完成发帖、分页列表、置顶列表、详情、作者删除，以及标签多对多关系。第一版使用 MySQL 强一致实现，富文本内容必须经过白名单清洗。

## 在 IDEA 中按顺序创建

```text
post/（帖子功能根包：负责发帖、列表、详情、标签和作者删除）
├─ controller/（Controller 包：帖子 HTTP 接口）
│  └─ PostController.java（Controller：发帖、列表、详情和删除）
├─ dto/（DTO 包：接收发帖和分页参数）
│  ├─ CreatePostDTO.java（DTO：标题、正文、分类和标签）
│  └─ PostPageDTO.java（DTO：页码、每页数量和筛选条件）
├─ vo/（VO 包：帖子列表和详情输出）
│  ├─ PostSummaryVO.java（VO：列表摘要）
│  └─ PostDetailVO.java（VO：正文、作者和完整标签）
├─ service/（Service 业务接口包）
│  ├─ PostService.java（Service：定义发帖、查询和删除方法）
│  └─ impl/（ServiceImpl 业务实现包）
│     └─ PostServiceImpl.java（ServiceImpl：发帖事务、查询装配和删除权限）
├─ mapper/（Mapper 数据库访问包）
│  ├─ PostMapper.java（Mapper：帖子表）
│  ├─ TagMapper.java（Mapper：标签表）
│  └─ PostTagMapper.java（Mapper：帖子标签关系表）
├─ entity/（Entity 数据库实体包）
│  ├─ PostEntity.java（Entity：post 表）
│  ├─ TagEntity.java（Entity：tag 表）
│  └─ PostTagEntity.java（Entity：post_tag 表）
└─ utils/（Utils 工具包）
   └─ HtmlUtils.java（Utils：使用 Jsoup 清洗富文本）
```

## API 契约

- `POST /api/posts`：登录用户发帖；作者取当前身份，不接受客户端伪造。
- `GET /api/posts?page=1&size=20&category=...`：只返回允许展示的状态。
- `GET /api/posts/top`：返回置顶帖子。
- `GET /api/posts/{id}`：返回详情和标签；不存在/不可见统一处理。
- `DELETE /api/posts/{id}`：作者或授权管理员删除，Service 复核权限。

页码全站从 1 开始，`offset=(page-1)*size`；size 设置上限。

## 数据设计

- `post(id, author_id, category, title, content, summary, cover_url, status, view_count, like_count, comment_count, favorite_count, is_top, is_essence, created_at, updated_at, deleted_at)`。
- `tag(id, name, use_count, created_at)`，name 唯一。
- `post_tag(post_id, tag_id)`，组合唯一；分别为两列建立查询所需索引。

帖子与标签关系写入同一事务。状态建议使用枚举并在数据库中限制合法值。列表只查摘要，详情再查正文。

## 实现顺序

1. 先写状态机、DDL、PostMapper 和数据库测试。
2. 实现发帖事务：清洗正文、插入帖子、查找/创建标签、插入关系。
3. 实现分页列表，使用批量查询装配作者和标签，禁止 N+1。
4. 实现详情和资源可见性规则。
5. 实现软删除与作者/管理员权限。
6. 最后再加入图片上传、事件发布和缓存；不要把它们混进第一版。

## 必测用例

- 空标题、超长标题、非法分类、重复标签、超量标签。
- `<script>`、事件属性、危险 URL 被清洗，允许的富文本仍保留。
- 非作者删除、已删除详情、待审核帖子对普通用户不可见。
- 发帖事务中标签关系失败时整体回滚。
- 分页边界、排序稳定性、重复翻页、批量查询次数。

## 人工验收

发布带格式正文与多个标签的帖子；列表只展示摘要，详情展示安全 HTML。第二账号不能删除；删除后列表和详情都不可见。检查 `post_tag` 没有重复关系。

## 参考入口

对照 `PostController`、`PostServiceImpl`、`PostMapper`、`TagMapper`、`PostTagMapper`、`PostCreateDTO`、`PostPreviewVO`、`PostDetailVO`、`XssUtil` 和 `post/create.html`、`post/detail.html`。

## 完成标准

你能画出“发帖事务”的表写入顺序，解释 DTO/Entity/VO 的差别，并用 SQL/日志证明列表装配没有 N+1。
