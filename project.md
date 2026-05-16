# project

## 上传（files）

- API 类型：Ktor HTTP 路由，端点定义在 api 模块的 CustomApi。
- 非分片上传：`POST files/upload`（multipart），服务端先写入本地临时文件并校验请求参数 sha256；随后上传到对象存储并确认返回对象名；FileRecord.sha256 按请求参数保存。
- 分片上传：`POST files/chunk/init` → `POST files/chunk/{id}/{index}/upload?hash=...`（每片 sha256 校验）→ `POST files/chunk/{id}/complete`（对象存储 compose 合并；写入 FileRecord 并清理 chunks）。
- 存储实现：统一走 ObjectStorageService。
  - `MEDIA_SERVICE=minio`：MinIO/S3 兼容对象存储（支持 compose）。
  - `MEDIA_SERVICE=filesystem`：落本地文件系统（或内存 FS），并通过 `GET /a_file/{path...}` 提供读取。

## 资源管理

- 新建 `InputStream`/`OutputStream` 并交给函数消费时，调用处用 `use {}` 明确关闭；例如计算 `sha256` 时先 `inputStream().buffered().use { input -> sha256(input.asSource().buffered()) }`。
- 工具函数如果接收调用方创建的流，不在函数内部关闭；调用方创建流时用 `use {}` 包住函数调用。

## Panel / Worker

- Worker 执行记录存储在后端 `TaskRecords` 表，记录类型是 `TaskRecordType`，`processedId` 指向该任务处理到的业务对象。
- Panel 通过 `/admin/task-records` 分页查询 worker 执行记录；支持按任务类型筛选，未传类型时返回全部记录。
