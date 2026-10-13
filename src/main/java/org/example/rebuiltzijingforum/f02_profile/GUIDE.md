# F02 profile：个人资料与头像

从需求到技术选型、替代方案与缺陷账本：[DesignFromZeroF02.md](DesignFromZeroF02.md)。

逐方法阅读当前实现：[NeedToUnderstandingF02.md](NeedToUnderstandingF02.md)。下文是功能清单与练习路线；实现边界以代码和逐方法笔记为准。

## 这一包交付什么

完成公开资料查看、本人资料修改和头像上传。核心训练是 DTO/VO 边界、资源所有权校验和安全文件上传；依赖 F01 提供的当前登录用户。

## 在 IDEA 中按顺序创建

```text
profile/（个人资料功能根包：负责资料查看、本人修改和头像）
├─ controller/（Controller 包：资料 HTTP 接口）
│  └─ ProfileController.java（Controller：查看资料、修改本人和上传头像）
├─ dto/（DTO 包：接收资料修改和头像上传参数）
│  ├─ UpdateProfileDTO.java（DTO：允许本人修改的资料字段）
│  └─ AvatarUploadDTO.java（DTO：头像文件信息）
├─ vo/（VO 包：返回给前端的资料数据）
│  └─ ProfileVO.java（VO：公开资料和本人可见资料）
├─ service/（Service 业务接口包）
│  ├─ ProfileService.java（Service：定义查询、修改和上传头像方法）
│  └─ impl/（ServiceImpl 业务实现包）
│     └─ ProfileServiceImpl.java（ServiceImpl：所有权校验、文件保存和数据库事务）
├─ mapper/（Mapper 数据库访问包）
│  └─ UserProfileMapper.java（Mapper：个人资料表增删改查）
├─ entity/（Entity 数据库实体包）
│  └─ UserProfileEntity.java（Entity：对应 user_profile 表）
└─ utils/（Utils 工具包）
   └─ AvatarFileUtils.java（Utils：文件名生成、类型和大小校验）
```

## API 契约

- `GET /api/profiles/{userId}`：公开字段可匿名读，敏感字段永不返回。
- `PUT /api/profiles/me`：只更新当前用户；URL 不接受任意 userId。
- `POST /api/profiles/me/avatar`：`multipart/form-data` 上传头像并返回受控访问 URL。

若为了练习保留 `PUT /{userId}`，Service 仍必须比较 currentUserId 与 path userId；不能只依靠前端隐藏按钮。

## 数据与文件规则

资料字段可与账号共表，也可拆为 `user_profile(user_id PK/FK, display_name, avatar_url, gender, signature, major, updated_at)`。请求 DTO 只包含允许修改的字段，禁止客户端提交 role、status、计数等管理字段。

头像上传必须校验大小、真实文件类型和图片解码结果；服务端生成文件名；路径不拼接用户原始文件名；存储根目录不可执行；旧文件删除应在新文件保存且数据库更新成功之后进行。第一版用本地存储，后续再替换 OSS 适配器。

## 实现顺序

1. 列出公开字段、本人字段、管理员字段三张清单。
2. 实现 ProfileVO 映射测试，保证 password/mobile/email/role 等不会误出现在公开响应。
3. 实现 `/me` 修改和 Service 所有权校验。
4. 实现本地头像存储接口与文件安全测试。
5. 处理“文件已写但 DB 失败”和“DB 已更新但旧文件删除失败”的补偿与日志。

## 必测用例

- A 修改 B、未登录修改、缺失用户、超长昵称/签名。
- DTO 越权提交 role/status/计数不会生效。
- 双扩展名、伪造 MIME、超大文件、非图片、路径穿越文件名。
- 存储失败、数据库失败、同一用户并发上传。
- 公开资料响应不含敏感字段。

## 人工验收

准备 A/B 两个账号。A 能查看 B 公开资料但不能修改 B；A 修改自己后立即可见。上传合法 PNG/JPEG 成功，上传改后缀的文本文件失败。检查磁盘没有使用原始文件名。

## 参考入口

对照 `UserController` 的资料和头像接口、`UserServiceImpl`、`UserInfoUpdateDTO`、`UserInfoVO`、`AliyunOSSOperator` 以及前端 `user/profile.html`、`user/edit-profile.html`。

## 完成标准

你能解释为什么“已登录”不等于“有权修改该资源”，并能说清文件、数据库和对象存储之间无法直接做本地事务时如何补偿。
