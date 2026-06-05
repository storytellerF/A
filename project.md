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

## Topic Compose / Block 编辑

- `TopicComposePage` 不再暴露整页 `RichEditTopicPage`；富文本编辑只作为 Block 编辑器中 `ContentBlock.Paragraph` 的编辑能力使用。
- Compose Multiplatform UI 测试在 desktop/JVM 目标使用 `androidx.compose.ui.test.runComposeUiTest`（v1 API）；当前 `v2.runComposeUiTest` 的 Skiko desktop 实现会抛 NotImplemented。测试依赖已在 `app/composeApp` 和 `app/core` 的 `commonTest` 配置为 `libs.ui.test`；Block 编辑器相关 UI 测试优先放在 `app/composeApp/src/headlessTest/kotlin`。

## RefCell

- `TopicRefCell`、`RoomRefCell`、`CommunityRefCell`、`UserRefCell` 不直接创建 ViewModel；通过 `LocalRefCellHandlerProvider` 获取 `LoadingHandler`，默认 provider 再适配现有 `create*ViewModel` 工厂。

## Media Player

- 媒体播放列表不在点击播放时解析；播放器 UI 通过 `LocalMediaPlayListHandlerProvider` 获取 `LoadingHandler<List<ConstPlayItem>>`，加载完成后再调用 `MediaPlayerService.start`。
- `app/desktopApp` 入口也需要注入 `LocalMediaPlayerService`；common `App()` 启动时会读取该 CompositionLocal。

## Android App

- `app/android` 是应用壳模块，承载 `MainActivity`、`UploadActivity`、`MediaPlayerActivity`、`BubbleActivity`、`RTCActivity` 和 manifest 入口；`app/composeApp` 保留可复用 Compose UI、actual 实现、服务与 Android helper。
- `app/composeApp` 不能编译期依赖 `app/android`。需要从 composeApp 内启动应用 Activity 时，使用稳定的显式 class name 常量创建 `Intent` 或 `ComponentName`。
- `app/desktopApp` 是桌面 JVM 应用壳模块，承载 Compose Desktop `main` 与 `compose.desktop` 打包配置；`app/composeApp` 保留共享 UI 和 JVM actual 实现。
- `panel/desktopApp` 是 Panel 桌面 JVM 应用壳模块，承载 Compose Desktop `main` 与 `compose.desktop` 打包配置；`panel/composeApp` 保留共享 UI 和 JVM actual 实现。
- `app/android` 和 `panel/android` 入口类继承 `ComponentActivity`，媒体服务继承 Media3 `MediaSessionService`；release lint 对这些 Kotlin/Compose/Media3 组件存在 `Instantiatable` 误报，两个应用壳模块均禁用该 lint 检查。

## Appium

- `dev/appium` 通过 `-Pappium=true` 才会被 include；测试脚本运行 Appium 前需要同时构建 `app:android:assembleDebug` 和 `panel:android:assembleDebug`，panel 的启动入口是 `com.storyteller_f.a.panel.MainActivity`。
- `app/android` 和 `panel/android` 可用 Robolectric 覆盖部分 Appium 前置/启动逻辑；例如把同格式 session JSON 写入 `filesDir/appium-session/session.json` 后调用 `restoreFromStorage`，可验证“注入私有 session 后恢复登录态”的非设备部分。

## Gradle 工具脚本

- `scripts/build_scripts/gradle-prune-implementations.sh` 通过 `./gradlew projects` 获取当前构建实际包含的模块，只处理这些模块的 `build.gradle.kts`，避免误删未 include 模块的依赖。
- prune 检查默认运行 `assemble`（可用 `GRADLE_PRUNE_TASK` 覆盖），脚本参数会继续传给 Gradle，例如 `-Pserver.flavor=...`。
- 每个候选 implementation 会在“已确认可删”的累计状态上继续验证；失败时只回滚当前候选，避免最终删除未被组合验证过的依赖。

## 账号 2FA

- 用户登录原本是 `/accounts/sign-in` 一步私钥签名；开启 TOTP 后，该接口返回 `SignInResponse.RequiresTotp` 并把 Ktor session 标记为 `UserSession.TwoFactorPending`，客户端再调用 `/accounts/sign-in/totp` 完成登录。
- 用户 2FA 设置走受保护的 `CustomApi.Users.TwoFactor` 接口；后端数据保存在 `UserTwoFactors` 表，迁移由 Exposed `MigrationUtils.statementsRequiredForDatabaseMigration` 自动补表。
- TOTP 后端实现放在 `cloud/service`，使用 JDK `SecureRandom`、Base32 和 `HmacSHA1` 自行实现 RFC 6238，生成标准 `otpauth://totp/...` URI 供 Google Authenticator 等客户端导入。

## Server 配置

- 用户注册开关由服务端环境变量 `ENABLE_SIGN_UP` 控制；未配置时默认开启，只有显式设置为 `false` 才会拒绝 `/accounts/sign-up`。

## CI

- `Alpha Server CI` 在启动远端 alpha 服务前执行后端/服务端测试：`:backend:minio:test`、`:cloud:cli:test`、`:cloud:service:test`、`:cloud:server:test`，并启用 `ENABLE_TEST_CONTAINER=true` 覆盖 Testcontainers 路径。
