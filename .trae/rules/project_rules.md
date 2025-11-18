# trae 项目开发规则（基于 AGENTS.md）

## 全局原则
- 使用 JDK 21，全模块 Kotlin/JVM 目标版本为 21
- 优先采用 KMP 可共享代码；平台差异使用 expect/actual 或平台源码目录处理
- 开启配置缓存（org.gradle.configuration-cache=true），避免配置期副作用与 IO
- 日志：使用 Napier（shared/app），在测试或调试场景按需初始化日志树
- 静态检查：使用 Detekt 按 config/detekt/detekt.yml 运行，修复或抑制需给出理由
- 覆盖率：Kover 可按需生成报告
- 环境/密钥：app/composeApp 通过 BuildKonfig 从 {flavor}.env 注入；务必传入 -Pserver.flavor 与 -Pserver.buildType
- 提交信息：使用动词开头的英文/中文短语，标注模块与范围（例如：client/core: add getAllUsers request）
- 不能用中文做方法名
- 使用表达式来取代var，例如：val x = if (a) b else c
- 不要使用裸的 InputStream/OutputStream，而要使用 BufferedInputStream/BufferedOutputStream
- 代码编辑之后不要用build 任务检查编译错误，要使用compileKotlin 任务检查错误，如果要执行test 任务，要使用test 任务，不要执行detekt 任务
- 不要在代码中使用 println 或 System.out.println
- 编辑文件时需要注意不要改变当前文件的换行符

## 模块职责概览
- **api**: 定义 REST API 端点、查询/路径模型；包含管理员 AdminApi（/admin/*）
- **client/core**: Session 管理、请求封装（统一 serviceCatching）
- **client/model-storage**: 抽象存储接口与集合标识
- **client/room**: 基于 Room 的存储实现与聚合入口
- **shared**: 跨端业务模型与工具
- **app/composeApp**: Android/Compose Desktop UI
- **panel/composeApp**: 管理后台 Panel 应用
- **backend/***: 提供后端的核心功能、存储、搜索等服务
- **cloud/***: 核心服务模块，整合 backend 功能
- **bot/***: 机器人模块

## 代码风格与实践
- **Kotlin**: 禁止在公共 API 泄露可变集合；优先使用 data class、sealed interface/class；使用 suspend + Result<T>；命名规范（函数动宾结构）
- **架构**: UI → SessionManager 扩展请求 → api endpoint；可选落地本地存储与分页
- **分页**: 游标统一保存到 RemoteKeyStorage（PRE_COLLECTION/NEXT_COLLECTION）
- **日志**: 对外网络请求使用 serviceCatching 打点；避免在热路径打印大量日志
- **测试**: 优先将测试写在 src/headlessTest/kotlin

## 新增功能指南

### A) 新增后端 API 功能
1. **定义 API 接口 (`api`)**
    - 位置: `api/src/main/kotlin/com/storyteller_f/a/api/CustomApi.kt` (或 `AdminApi.kt`)
    - 步骤: 定义 `safeApi` 或 `mutationApi`，并为请求/响应创建数据类

2. **定义和实现数据库操作 (`backend/core` & `backend/exposed`)**
    - 接口 (DAO in `backend/core`): 在 `backend/core/src/.../Database.kt` 下
    - 表定义 (Table in `backend/exposed`): 在 `backend/exposed/src/.../table/` 包下
    - 实现 (DAO Impl in `backend/exposed`): 在 `backend/exposed/src/.../database` 包下

3. **实现服务逻辑 (`cloud/service`)**
    - 位置: 在 `cloud/service/src/...` 下创建或修改服务类

4. **编写测试**
    - 位置: 在`cloud/server/src/test/` 包下

### B) 客户端接入已有 API
1. **同步 API 端点定义 (`api`)**
    - 确认 `api` 模块中已包含需要接入的 API 定义

2. **新增客户端请求 (`client/core`)**
    - 用户端: 在 `client/core/AppRequest.kt` 添加 `UserSessionManager` 的扩展函数
    - 面板端: 在 `client/core/PanelRequest.kt` 添加 `PanelSessionManager` 的扩展函数
    - 实现: 统一调用 `serviceCatching { CustomApi.* 或 AdminApi.*.invoke(...) }` 返回 `Result<T>`

3. **扩展/接入本地存储 (可选)**
    - 抽象: 在 `client/model-storage` 定义新的 `*Storage` 接口
    - 实现: 在 `client/room` 新增 `*RoomStorage` 实现

4. **UI 接入**
    - 在 `ViewModel` 中通过对应 `SessionManager` 调用请求函数
    - 需要分页则使用 `Pager` + `PagingSource/RemoteMediator`

## 构建与运行
- Desktop 运行（dev）: `gradlew :app:composeApp:run -Pserver.flavor=dev.win -Pserver.buildType=dev`
- Desktop 打包（release）: `gradlew :app:composeApp:packageRelease -Pserver.flavor=dev.win -Pserver.buildType=release`
- Android 单测: `gradlew :shared:testDebugUnitTest`、`gradlew :app:composeApp:testDebugUnitTest`
- JVM 测试: `gradlew :shared:jvmTest`、`gradlew :app:composeApp:desktopTest`

## 与 AI 协作的额外规则
- 仅在必要处最小改动，优先在 client/core 与 api 扩展，不随意改动公共模型
- 不创建仓库外文件；敏感文件（*.env）已在 .aiexclude 中排除
- 涉及配置缓存的改动避免在 Gradle 配置期读取外部环境
- 对已有测试保持兼容，如需更新，给出迁移理由与步骤

## 故障排查速查
- 网络异常: 查看 client/core/*Request.kt 的 serviceCatching 日志与 AdminApi/CustomApi 路由
- 分页异常: 检查 RemoteKeyStorage/RemoteKeyRoomStorage 的保存与读取
- 数据未落地: 检查 *RoomStorage.save/observeData 实现与 commonJson 序列化

## 命名/路径参考
- Admin API 示例: `AdminApi.Users.get.invoke(PaginationQuery(...))`
- 用户 API 示例: `CustomApi.Rooms.Id.get(query, CommonPath(id))`
- 存储集合: `UserCollection/TopicCollection/TitleCollection/RoomCollection/CommunityCollection/ReactionCollection/UploadCollection/MediasCollection`

model 命名
表名：复数形式，比如Users
表中记录对应的模型类名：单数形式，比如User
表中记录以及其他补充信息：Raw加上记录名，比如RawUser
返回给客户端的模型类名：单数形式，比如UserInfo
