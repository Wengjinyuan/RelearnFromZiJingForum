# F12 图片存储复现指南

F12 解决的不是“传一个文件”，而是让 F02 头像和 F03 富文本图片共用同一套校验、路径、存储切换与错误语义。本地模式可以直接学习；OSS 模式保留原项目使用的 Aliyun OSS SDK 3.18.4，但密钥只从配置进入。

## 先按顺序读文件

1. `ImageStorage`：业务层只依赖的接口。
2. `ImageValidator`：在写入任何存储前做大小、MIME 和真实图片检查。
3. `LocalImageStorage`：写入 `forum.storage.local-root`，返回 `/uploads/...`。
4. `AliyunOssImageStorage`：使用 bucket + object key 上传/删除，返回公开 URL。
5. `StorageConfiguration`：由 `forum.storage.type` 在两个实现中只选一个。
6. F02 `ProfileServiceImpl.uploadOwnAvatar` 和 F03 `PostImageController.upload`：看真实使用方。

## 配置与运行

默认配置已经可运行：

```yaml
forum:
  storage:
    type: local
    local-root: ./uploads
```

切换 OSS 前填写 `ALIYUN_OSS_ENDPOINT`、`ALIYUN_OSS_REGION`、`ALIYUN_OSS_BUCKET`、`ALIYUN_OSS_ACCESS_KEY_ID`、`ALIYUN_OSS_ACCESS_KEY_SECRET`，然后设 `FORUM_STORAGE_TYPE=oss`。自定义 CDN 域名时设 `ALIYUN_OSS_PUBLIC_BASE_URL`。少一项就启动失败，不会悄悄回退本地盘。

## 验收清单

- `ImageValidatorTest`：真实 PNG 通过，伪图片、超大文件被拒绝。
- `LocalImageStorageTest`：服务端生成路径、可读取、可删除且防路径越界。
- `AliyunOssImageStorageTest`：mock SDK 验证 bucket/key/公开 URL/关闭客户端和稳定 503。
- `AvatarStorageCompensationTest`：资料表更新失败时删除新图片。
- `AccountRecoveryApiTest`：真正经过 JWT、multipart、Controller 和本地文件。

没有提供真实阿里云凭据时，上述 OSS 证据是 mock 合同测试，不等于真实 bucket 已联调。
