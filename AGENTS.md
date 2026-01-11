# .airules — 项目 AI/协作开发规则（类似 .cursorrules）

目的
- 为 AI 助手与贡献者提供统一的模块职责、代码风格、开发与变更流程指引。

适用范围
- 本仓库所有模块（Kotlin Multiplatform，Android/Compose Desktop/Backend/Panel/Bot 等）。

全局原则
- JDK 21，全模块 Kotlin/JVM 目标 21。
- 优先采用 KMP 可共享代码；平台差异用 expect/actual 或平台源码目录处理。
- 配置缓存开启（org.gradle.configuration-cache=true），避免配置期副作用与 IO。
- 日志：使用 Napier（shared/app），在测试或调试场景按需初始化日志树。
- 静态检查：Detekt 按 config/detekt/detekt.yml 运行，修复或抑制需给出理由。
- 覆盖率：Kover 可按需生成报告。
- 环境/密钥：app/composeApp 通过 BuildKonfig 从 deploy/{flavor}.env 注入；务必传入 -Pserver.flavor 与 -Pserver.buildType。
- 提交信息：使用动词开头的英文/中文短语，标注模块与范围（例如：client/core: add getAllUsers request）。

模块职责概览（路径见 settings.gradle.kts）
- api（api/src/.../CustomApi.kt）
  - 定义 REST API 端点、查询/路径模型；包含管理员 AdminApi（/admin/*）。
  - 通过 route4k invoke 调用（示例：AdminApi.Users.get.invoke(query)）。
- client/core
  - Session 管理（SessionManager.kt）：UserSessionManager/PanelSessionManager 及工厂；基于私钥的一键登录流程。
  - 请求封装（AppRequest.kt/PanelRequest.kt）：统一 serviceCatching，封装用户端与面板端请求，调用 api。
- client/model-storage
  - 抽象存储接口与集合标识（*Collection，*Storage，RemoteKeyStorage 等）。不含具体实现。
- client/room
  - 基于 Room 的存储实现（*RoomInfoStorage，RemoteKeyRoomStorage）与聚合入口 RoomModelStorage。
- shared
  - 跨端业务模型与工具（shared/src/commonMain/...）。
    根据情况确定是否需要添加model，对于新增的model 是否需要添加ObjectType 取决这个对象是实体还是关联关系。示例：UserInfo 是实体，UserFavoriteInfo 是关联关系。
- app/composeApp
  - Android/Compose Desktop UI，BuildKonfig 注入 SERVER_URL/WS_SERVER_URL/BUILD_TYPE/FLAVOR；打包配置、Proguard。
- panel/composeApp
  - 管理后台 Panel 应用，主要使用 PanelSessionManager 与分页（Pager/RemoteMediator）。
- backend/*
  - `backend/core`: 提供后端的核心功能，包括协程、序列化和日期时间等基础库。
  - `backend/minio`: 集成 MinIO，提供对象存储服务。
  - `backend/redis`: 集成 Redis，用于缓存和键值存储。
  - `backend/lucene`: 提供基于 Lucene 的全文搜索功能。
  - `backend/simple`: 提供一个基础的后端实现。
  - `backend/elastic`: 集成 Elasticsearch，提供高级搜索和分析功能。
  - `backend/exposed`: 提供基于 Exposed 框架的数据库访问功能，支持 H2 和 PostgreSQL。
  - `backend/filesystem`: 提供文件系统相关的功能，包括内存文件系统和 URL 构建。
- cloud/*
  - `cloud/cli`: 提供一个命令行界面，用于管理和与云服务交互。
  - `cloud/pdf`: 定义 PDF 操作的核心接口和模型。
  - `cloud/pdfbox`: 使用 PDFBox 实现 PDF 创建和处理，支持 Markdown。
  - `cloud/server`: 项目的主后端服务器，基于 Ktor，使用 Route4k 进行路由，Exposed 进行数据库操作，并集成 Sentry 进行错误监控。
  - `cloud/worker`: 一个后台工作进程，用于执行数据库和服务相关的任务。
  - `cloud/openpdf`: 使用 OpenPDF 提供另一种 PDF 生成方案。
  - `cloud/service`: 核心服务模块，整合了 `backend` 中的多个模块，为 `cloud/server`、`cloud/worker` 和 `cloud/cli` 提供统一的服务接口。
    如果返回的是分页的结果，返回PaginationResult对象，有pagination 扩展函数变成ServerResponse。
- bot/*
  - 机器人模块。

代码风格与实践
- Kotlin
  - 禁止在公共 API 泄露可变集合；优先使用 data class、sealed interface/class 描述模型与状态。
  - 协程：使用 suspend + Result<T>（通过 serviceCatching 包装）；避免在 ViewModel 之外持久化作用域。
  - 命名：函数动宾结构，对象/文件名与职责一致；扩展函数以接收者类型为前缀命名（如 PanelSessionManager.getAllUsers）。
  - 错误处理：仅在调用边界捕获并转为 Result；业务分支返回明确错误类型或消息。
- 架构
  - UI → SessionManager 扩展请求 → api endpoint；可选落地本地存储与分页。
  - 分页游标统一保存到 RemoteKeyStorage（PRE_COLLECTION/NEXT_COLLECTION）。
- 日志
  - 对外网络请求使用 serviceCatching 打点；避免在热路径打印大量日志。
- 测试
  - 优先将测试写在 src/headlessTest/kotlin；桥接到 jvmTest/desktopTest/androidUnitTest。

新增功能指南（强制遵循）

本指南分为两部分：后端 API 开发和客户端 API 接入。

**A) 新增后端 API 功能**

1.  **定义 API 接口 (`api`)**
    *   **位置**: `api/src/main/kotlin/com/storyteller_f/a/api/CustomApi.kt` (或 `AdminApi.kt`)
    *   **步骤**: 定义 `safeApi` 或 `mutationApi`，并为请求/响应创建数据类。

2.  **定义和实现数据库操作 (`backend/core` & `backend/exposed`)**
    *   **说明**: 数据库操作遵循分层设计。`backend/core` 定义了通用的数据库事务函数，`backend/exposed` 负责具体的表结构定义和数据访问逻辑。
    *   **接口 (DAO in `backend/core`)**:
        *   **位置**: 在 `backend/core/src/.../Database.kt` 下。
    *   **表定义 (Table in `backend/exposed`)**:
        *   **位置**: 在 `backend/exposed/src/.../table/` 包下 (例如 `Users.kt`)。
        *   **步骤**: 创建继承自 `Table` 的 `object`，定义表结构。
        *   如果是包含Id 的使用BaseTable，否则使用Table
    *   **实现 (DAO Impl in `backend/exposed`)**:
        *   **位置**: 在 `backend/exposed/src/.../database` 包下 (例如 `ExposedUserDatabase.kt`)。
        *   **步骤**: 实现 DAO 接口。方法中只包含纯粹的 `Exposed` DSL 查询逻辑。

3.  **实现服务逻辑 (`cloud/service`)**
    *   **位置**: 在 `cloud/service/src/...` 下创建或修改服务类 (例如 `UserService.kt`)。

4. **编写测试**
    *   **位置**: 在`cloud/server/src/test/` 包下。

---

**B) 客户端接入已有 API**

1.  **同步 API 端点定义 (`api`)**
    *   **位置**: `api/src/main/kotlin/com/storyteller_f/a/api/CustomApi.kt`
    *   **说明**: 此步骤通常由后端开发人员完成。客户端开发人员需确认 `api` 模块中已包含需要接入的 API 定义。若未定义，请遵循 **A) 新增后端 API 功能** 的第一步。

2.  **新增客户端请求 (`client/core`)**
    *   **用户端**: 在 `client/core/AppRequest.kt` 添加 `UserSessionManager` 的扩展函数。
    *   **面板端**: 在 `client/core/PanelRequest.kt` 添加 `PanelSessionManager` 的扩展函数。
    *   **实现**: 统一调用 `serviceCatching { CustomApi.* 或 AdminApi.*.invoke(...) }` 返回 `Result<T>`。命名需规范（如 `getAllUsers`）。

3.  **扩展/接入本地存储 (可选, `client/model-storage`, `client/room`)**
    *   **抽象**: 在 `client/model-storage` 定义新的 `*Storage` 接口。
    *   **实现**: 在 `client/room` 新增 `*RoomStorage` 实现，并在 `RoomModelStorage` 中聚合。分页游标使用 `RemoteKeyRoomStorage`。

4.  **UI 接入 (`app/composeApp`, `panel/composeApp`)**
    *   在 `ViewModel` 中通过对应 `SessionManager` 调用请求函数。
    *   需要分页则使用 `Pager` + `PagingSource/RemoteMediator`，通过 `ModelStorage` 读写缓存。

5.  **测试与验证**
    *   **headlessTest**: 优先在 `src/headlessTest/kotlin` 中添加测试，覆盖主要逻辑。
    *   **Android/Unit**: 涉及 Android 资源的测试放在 `testDebugUnitTest`。

6.  **提交与变更记录**
    *   遵循提交信息规范，清晰标注模块与范围。
    *   **示例**:
        *   `cloud/service: implement user search logic`
        *   `cloud/server: add /users/search endpoint`
        *   `client/core: add searchUsers request`

构建与运行（常用）
- Desktop 运行（dev）：gradlew -Pserver.flavor=dev.win -Pserver.buildType=dev :app:composeApp:run
- Desktop 打包（release）：gradlew -Pserver.flavor=dev.win -Pserver.buildType=release :app:composeApp:packageRelease
- Android 单测：
  - gradlew :shared:testDebugUnitTest
  - gradlew :app:composeApp:testDebugUnitTest
- JVM 测试：
  - gradlew :shared:jvmTest
  - gradlew :app:composeApp:desktopTest
- Wasm（可选）：gradlew :shared:wasmJsBrowserTest -Ptarget.wasm=true

与 AI 协作的额外规则
- 仅在必要处最小改动，优先在 client/core 与 api 扩展，不随意改动公共模型。
- 不创建仓库外文件；敏感文件（deploy/*.env）已在 .aiexclude 中排除，不要泄露其内容。
- 涉及配置缓存的改动避免在 Gradle 配置期读取外部环境；读取 env 统一交给 BuildKonfig 逻辑。
- 对已有测试保持兼容，如需更新，给出迁移理由与步骤。

故障排查速查
- 网络异常：查看 client/core/*Request.kt 的 serviceCatching 日志与 AdminApi/CustomApi 路由。
- 分页异常：检查 RemoteKeyStorage/RemoteKeyRoomStorage 的保存与读取；确认 PRE/NEXT 语义与后端对齐。
- 数据未落地：检查 *RoomStorage.save/observeData 实现与 commonJson 序列化。

附录：命名/路径参考
- Admin API 示例：AdminApi.Users.get.invoke(PaginationQuery(...))
- 用户 API 示例：CustomApi.Rooms.Id.get(query, CommonPath(id))
- 存储集合：UserCollection/TopicCollection/TitleCollection/RoomCollection/CommunityCollection/ReactionCollection/UploadCollection/MediasCollection
