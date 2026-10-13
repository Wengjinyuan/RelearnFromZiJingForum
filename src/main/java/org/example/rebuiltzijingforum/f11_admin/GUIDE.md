# F11 admin：后台用户与帖子治理

从需求到技术选型、替代方案与缺陷账本：[DesignFromZeroF11.md](DesignFromZeroF11.md)。

逐方法阅读当前实现：[NeedToUnderstandingF11.md](NeedToUnderstandingF11.md)。下文是功能清单与练习路线；状态转换与审计以当前代码为准。

> 本包已按目录图拆出 AdminUserController/AdminPostController、三个 Service 接口/Impl、DTO、VO、Entity、处罚与审计 Mapper；共用用户/帖子 SQL 仍在 AdminMapper。先读 [F04–F11 从前端到数据库](../../../../../../../docs/F04-F11-从前端到数据库.md) 的 F11 章节。F03 新帖仍默认发布，不是默认待审核流程。

## 这一包交付什么

完成用户查询、禁言/禁用/解除、处罚历史，以及帖子审核、驳回、删除、置顶和精华。核心是三层授权、状态机、事务审计和跨系统副作用。

## 在 IDEA 中按顺序创建

```text
f11_admin/（后台治理功能根包：负责用户处罚、帖子审核和审计）
├─ controller/（Controller 包：管理员 HTTP 接口）
│  ├─ AdminUserController.java（Controller：用户查询、禁言、禁用和解除）
│  └─ AdminPostController.java（Controller：帖子审核、驳回、删除、置顶和精华）
├─ dto/（DTO 包：接收管理员操作参数）
│  ├─ PunishmentDTO.java（DTO：期限和原因；目标用户/动作从 URL 获取）
│  └─ PostAuditDTO.java（DTO：原因和可选 enabled；帖子 id/动作从 URL 获取）
├─ vo/（VO 包：后台列表和审计输出）
│  ├─ AdminUserVO.java（VO：后台用户信息）
│  ├─ AdminPostVO.java（VO：后台帖子信息）
│  └─ AuditLogVO.java（VO：谁在何时因何原因做了什么）
├─ service/（Service 业务接口包）
│  ├─ AdminUserService.java（Service：定义用户治理方法）
│  ├─ AdminPostService.java（Service：定义帖子治理方法）
│  ├─ PunishmentService.java（Service：定义处罚到期处理方法）
│  └─ impl/（ServiceImpl 业务实现包）
│     ├─ AdminUserServiceImpl.java（ServiceImpl：角色权限、处罚状态和审计事务）
│     ├─ AdminPostServiceImpl.java（ServiceImpl：帖子状态转换和审计事务）
│     ├─ PunishmentServiceImpl.java（ServiceImpl：使用 @Scheduled 定时解除到期处罚）
│     └─ AdminChecks.java（共用管理员/分页/原因校验）
├─ mapper/（Mapper 数据库访问包）
│  ├─ AdminMapper.java（Mapper：后台用户/帖子查询和状态更新）
│  ├─ PunishmentMapper.java（Mapper：处罚表）
│  └─ AuditLogMapper.java（Mapper：审计日志表）
├─ entity/（Entity 数据库实体包）
│  ├─ PunishmentEntity.java（Entity：处罚表）
│  └─ AuditLogEntity.java（Entity：审计日志表）
└─ config/（Config 定时任务配置包）
   └─ AdminScheduleConfig.java（Config：启用 @Scheduled）
```

## API 契约

- `/api/admin/users/**`：查询、禁言、禁用、解除、处罚历史。
- `/api/admin/posts/**`：待审列表、通过、驳回、删除、置顶、精华。

危险写操作要求 reason，普通用户直接构造请求得到 403。`AdminChecks.admin` 在 Service 再检查操作者角色；用户处罚禁止对自己或其他管理员操作。当前没有“最后一个超级管理员”模型，因为只有 USER/ADMIN 两种角色。

## 状态机先于代码

用户至少有 NORMAL/MUTED/BANNED；帖子至少有 PENDING/PUBLISHED/REJECTED/DELETED。为每条允许转换写明：操作者、前置状态、结果状态、原因、缓存清理、搜索同步、通知和审计。非法转换直接拒绝，不通过随意赋整数状态实现。

## 数据与事务

- `user_account.status` 保存快速判定状态；最新 MUTE 的 `expires_at` 在处罚历史里。
- `user_punishment` 记录每次处罚与解除。
- `admin_audit_log(operator_id, action, target_type, target_id, before_state, after_state, reason, created_at)`。

状态修改、历史记录、审计日志同一事务。ES 开启时在提交后回查并同步；ES 失败只记录日志，需全量重建补偿。当前未做治理通知事件或跨系统统一 Outbox。

## 实现顺序

1. 写状态转换表、角色层级和数据库约束。
2. 实现管理员认证及 Service 二次授权。
3. 实现用户处罚、解除、历史和审计事务。
4. 实现帖子状态转换及置顶/精华规则。
5. 实现到期处罚幂等解除；多实例用数据库抢占或分布式调度。
6. 最后接缓存失效、通知和搜索同步，并监控失败补偿。

## 必测用例

- 普通用户调用、管理员操作自己/更高角色、最后一个超级管理员。
- 重复处罚、非法状态转换、缺失原因、到期边界。
- DB 中途失败全部回滚，审计不能缺失。
- 到期任务重复执行、多实例执行都只解除一次。
- 缓存清理失败、MQ/ES 延迟后最终可补偿。
- 治理后前台列表、详情、搜索、旧 token 行为一致。

## 人工验收

普通用户直接构造管理请求必须 403。管理员禁言 A 后，A 可浏览但不能发帖/评论；到期后恢复。帖子驳回/删除后所有前台和搜索入口一致。任意动作都能从审计回答“谁、何时、为何、把什么从什么改为什么”。

## 参考入口

对照后台用户 Controller、后台帖子 Controller、对应的 ServiceImpl、处罚 Entity、处罚 Mapper、定时处罚实现和 `admin/admin.html`。

## 完成标准

能解释身份、角色、资源所有权三层授权，并能画出“治理事务提交 -> 缓存/通知/搜索副作用 -> 失败补偿”的链路。
