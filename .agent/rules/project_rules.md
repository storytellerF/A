---
trigger: always_on
---

# 项目开发规则

## 全局原则
- 使用 JDK 21，全模块 Kotlin/JVM 目标版本为 21
- 优先采用 KMP 可共享代码；平台差异使用 expect/actual 或平台源码目录处理
- 开启配置缓存（org.gradle.configuration-cache=true），避免配置期副作用与 IO
- 日志：使用 Napier（shared/app），在测试或调试场景按需初始化日志树
- 静态检查：使用 Detekt 按 config/detekt/detekt.yml 运行，修复或抑制需给出理由
- 覆盖率：Kover 可按需生成报告
- 环境/密钥：app/composeApp 通过 BuildKonfig 从 deploy/{flavor}.env 注入；务必传入 -Pserver.flavor 与 -Pserver.buildType
- 提交信息：使用动词开头的英文/中文短语，标注模块与范围（例如：client/core: add getAllUsers request）
- 不能用中文做方法名
- 使用表达式来取代var，例如：val x = if (a) b else c
- 不要使用裸的 InputStream/OutputStream，而要使用 BufferedInputStream/BufferedOutputStream
- 代码编辑之后不要用build 任务检查编译错误，要使用assemble 任务检查错误，如果要执行test 任务，单纯执行test 任务，不要执行detekt 任务
- 不要在代码中使用 println 或 System.out.println
- 编辑文件时需要注意不要改变当前文件的换行符
- 添加依赖时通过github 或者对应仓库获取正确的group 和artifact，找不到依赖也禁止降低版本
- 一个文件代码超过1000 行需要拆分复杂部分代码到其他文件
- 遇到问题找本质原因，避免无意义的fallback和检查。

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
- **测试**: 尽可能的编写测试，对于client 端并且是无ui 写到src/headlessTest/kotlin 中，如果是compose 正常按照https://kotlinlang.org/docs/multiplatform/compose-test.html 编写测试，如果是端到端测试写道appium 模块中

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

### C) 确保没有编译错误
- 代码完成之后，运行`./gradlew assemble --console=plain` 检查是否存在编译错误
- 编译错误检查之后，运行`./scripts/tool_scripts/exec-until-success.sh ./gradlew detekt --console=plain` 进行静态代码风格检查
  - 如果是windows 环境需要通过git bash 执行

### C) 测试
- 对于受到影响的模块要主动进行测试工作
    * 如果是影响范围小使用 `./gradlew module:test --console=plain`
    * 执行完整的普通单元测试使用`./scripts/test_scripts/build-and-test.sh --unit --console=plain` (不包含依赖设备的compose 和appium 测试)
    * 执行端到端appium 测试使用`./scripts/test_scripts/build-and-test.sh --appium --console=plain`
    * 如果是compose common 中在device_based 中的测试需要使用真实设备`./gradlew :module:connectedAndroidTest` 或者`./gradlew :module:jvmTest`
- 测试的目的是发现问题，如果发现了问题应该全力修复，而不是在测试用例上绕过去。
- 除非有前后依赖关系，否则不要把多余的测试步骤加到一个测试用例里面
- 重复性的测试步骤可以提取成单独的方法
- appium 测试需要真是设备或者模拟器，无法并行

## 与 AI 协作的额外规则
- 仅在必要处最小改动，优先在 client/core 与 api 扩展，不随意改动公共模型
- 不创建仓库外文件；敏感文件（deploy/*.env）已在 .aiexclude 中排除
- 涉及配置缓存的改动避免在 Gradle 配置期读取外部环境
- 对已有测试保持兼容，如需更新，给出迁移理由与步骤
- 编写代码或者扫描项目获取到的知识保存到project.md 中，对于project.md 中过时或者错误知识也可以进行编辑
- 编写代码或者扫描项目获取到的rule 相关的可以编辑到本文档

## 故障排查速查
- 网络异常: 查看 client/core/*Request.kt 的 serviceCatching 日志与 AdminApi/CustomApi 路由
- 分页异常: 检查 RemoteKeyStorage/RemoteKeyRoomStorage 的保存与读取
- 数据未落地: 检查 *RoomStorage.save/observeData 实现与 commonJson 序列化

## 命名/路径参考
* Admin API 示例: `AdminApi.Users.get.invoke(PaginationQuery(...))`
* 用户 API 示例: `CustomApi.Rooms.Id.get(query, CommonPath(id))`
* 存储集合: `UserCollection/TopicCollection/TitleCollection/RoomCollection/CommunityCollection/ReactionCollection/UploadCollection/MediasCollection`
* model 命名
    - 表名：复数形式，比如Users
    - 表中记录对应的模型类名：单数形式，比如User
    - 表中记录以及其他补充信息：Raw加上记录名，比如RawUser
    - 返回给客户端的模型类名：单数形式，比如UserInfo