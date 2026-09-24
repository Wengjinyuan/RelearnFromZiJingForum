# 紫荆论坛复现工程

这是根据《紫荆论坛功能倒推复现手册》创建的教学型 Spring Boot 骨架。它采用“按功能纵向分包”，不是把 Controller、Service、Mapper 全部堆在全局分层包里，也没有复制参考项目的现成实现。

## 在 IDEA 中打开

1. `File -> Open`，选择 `E:\REBUILTZIJINGFORUM\pom.xml`。
2. Project SDK 选择 JDK 17 或更高版本；Maven 编译目标固定为 Java 17。
3. 等 Maven 同步完成，运行 `RebuiltZijingForumApplication`。
4. 浏览器访问 `http://localhost:8080/api/health` 和 `http://localhost:8080/api/features`。
5. 运行 `BaselineApiTest`，确认工程基线通过。

命令行也可以执行：

```powershell
mvn test
mvn spring-boot:run
```

## 包与功能一一对应

| 顺序 | 功能包 | 目标功能 | 包内指导 |
| --- | --- | --- | --- |
| 1 | `account` | F01 账号与鉴权 | `account/GUIDE.md` |
| 2 | `profile` | F02 资料与头像 | `profile/GUIDE.md` |
| 3 | `post` | F03 帖子与标签 | `post/GUIDE.md` |
| 4 | `comment` | F04 评论与回复 | `comment/GUIDE.md` |
| 5 | `interaction` | F05 点赞收藏浏览 | `interaction/GUIDE.md` |
| 6 | `follow` | F06 关注与关注流 | `follow/GUIDE.md` |
| 7 | `home` | F09 首页与热门标签 | `home/GUIDE.md` |
| 8 | `notification` | F07 通知中心 | `notification/GUIDE.md` |
| 9 | `search` | F08 搜索 | `search/GUIDE.md` |
| 10 | `admin` | F11 后台治理 | `admin/GUIDE.md` |
| 11 | `message` | F10 私信 | `message/GUIDE.md` |

这里的顺序故意按技术依赖排列，因此编号不是连续的。

## 每个功能包内部的固定结构

第一次看这些名称时，先读 [Java 包与文件类型说明](docs/01-Java包与文件类型说明.md)。项目统一采用常见的 `Controller、DTO、VO、Service、ServiceImpl、Mapper、Entity` 命名，不再引入 Repository、Gateway、Row、Session、View、Request 等额外名称。

开始某个功能时，在对应包中按下面结构创建代码：

```text
feature/
├─ controller/       Controller：HTTP 接口
├─ dto/              DTO：接收请求或后端内部传递数据
├─ vo/               VO：返回给前端的数据
├─ service/          Service：业务接口
│  └─ impl/          ServiceImpl：业务实现和事务
├─ mapper/           Mapper：MyBatis 数据访问接口
├─ entity/           Entity：数据库表实体
├─ config/           Config：Spring 配置
├─ interceptor/      Interceptor：登录拦截器
├─ utils/            Utils：JWT、文件等工具类
└─ GUIDE.md          该功能的一对一复现手册
```

调用方向固定为 `Controller -> Service -> ServiceImpl -> Mapper -> MySQL`，DTO 负责输入，VO 负责输出，Entity 只用于数据库映射。

## 复现纪律

- 每次只做一个纵向闭环，先让 MySQL/同步版本正确，再增加 Redis、RabbitMQ、Elasticsearch。
- Controller 不写业务规则，数据库实体不直接作为 API 响应。
- 每个写操作都回答四个问题：谁能做、重复做怎样、并发怎样、失败后怎样恢复。
- 每完成一包，必须运行该包文档中的自动化与人工验收清单。
- 参考项目用于对照，不直接复制整类；先自己设计，再读参考入口找差异。

## 当前已完成与未完成

已完成：Maven 基线、统一响应、统一异常、健康检查、11 个功能包、功能目录接口、基线测试、每包指导文档，以及 F01 account 的注册、登录、JWT 鉴权、当前用户和修改密码完整样例。

未完成：F02-F11 的数据库表、业务类和中间件接入。这些内容就是你照着 account 样例按包练习的主体；各 `GUIDE.md` 已给出类创建顺序、约束、接口和验收条件。
