# A

[Download app](https://nightly.link/storytellerF/A/workflows/alpha/alpha?preview)

## 项目概述

A 是一个基于 Kotlin Multiplatform 的多平台应用程序框架，支持 Android、Desktop、CLI 和 Web 多个目标平台。项目采用模块化架构设计，集成了云服务、后端存储、机器人功能等完整的企业级应用组件。

## 技术栈

- **开发语言**: Java 21 (Eclipse Temurin), Kotlin
- **多平台架构**: Android + Desktop + CLI + Web
- **UI 框架**: Compose Multiplatform
- **网络框架**: Ktor (JetBrains)
- **数据库/存储**: Exposed ORM, Redis, MinIO, Lucene, Elasticsearch, Filesystem
- **PDF 处理**: Apache PDFBox, OpenPDF
- **代码质量**: Detekt 静态检查, Kover 覆盖率测试

## 项目结构

### 核心模块

- **App 模块** (`app/`) - Android/Desktop/CLI 客户端应用
  - `composeApp` - Compose Multiplatform 共享 UI
  - `android` - Android 应用壳
  - `desktopApp` - 桌面应用入口
  - `cliApp` - 命令行应用
  - `core` - 核心业务逻辑

- **Cloud 模块** (`cloud/`) - 云服务端
  - `server` - Ktor HTTP 服务器
  - `service` - 云服务和 TOTP 实现
  - `worker` - 后台任务处理
  - `pdf/pdfbox/openpdf` - PDF 处理服务
  - `cli` - 云端管理 CLI

- **Backend 模块** (`backend/`) - 数据存储层
  - `exposed` - SQL 数据库 ORM
  - `redis` - Redis 缓存
  - `minio` - S3 兼容对象存储
  - `lucene` / `elastic` - 搜索引擎
  - `filesystem` - 文件系统存储

- **Client 模块** (`client/`) - 客户端库
  - `core` - 客户端核心
  - `bot-lib` - 机器人库
  - `room` - 房间功能
  - `model-storage` - 模型存储
  - `ascii-parser` - ASCII 解析器

- **Panel 模块** (`panel/`) - 管理面板
  - `composeApp` - 管理界面 UI
  - `android` / `desktopApp` / `cliApp` - 多平台入口

- **API 模块** (`api/`) - 接口定义和服务路由

- **Bot 模块** (`bot/`) - 内置机器人功能

- **Dev 模块** (`dev/`) - 开发和测试工具
  - `server` - 开发服务器
  - `cli` - 开发工具
  - `appium` - Appium 自动化测试（可选）

## 主要功能

### 文件上传
- 支持普通上传 (`POST /files/upload`)
- 支持分片上传 (`POST /files/chunk/*`)
- SHA256 完整性校验
- 可配置存储后端（MinIO 或本地文件系统）

### 用户认证
- 私钥签名登录 (`/accounts/sign-in`)
- TOTP 双因素认证（2FA）
- 用户注册控制（`ENABLE_SIGN_UP` 环境变量）

### 任务管理
- Worker 执行记录存储和查询
- Panel 管理界面分页查看任务记录
- 按任务类型筛选

### 媒体播放
- 媒体播放列表管理
- 跨平台媒体播放器服务

### Cloud CLI
- 预置账号密钥生成
- Dilithium 签名和 Kyber 加密密钥管理
- Dev-data 初始化工具

## 构建和运行

### 环境要求
- Java 21 (Eclipse Temurin)
- Gradle 8.x

### 构建命令

```bash
# 构建所有模块
./gradlew build

# 构建 Cloud 服务器
./gradlew :cloud:server:build

# 构建 Android 应用
./gradlew :app:android:assembleDebug

# 构建 Desktop 应用
./gradlew :app:desktopApp:packageDistributionForCurrentOS

# 运行测试
./gradlew test

# 运行特定模块测试
./gradlew :cloud:service:test :backend:minio:test
```

### Docker 部署

```bash
# 构建镜像
docker build -t a-server .

# 启动服务
sh ./bin/server
```

### 环境变量

- `HOST_TYPE`: 主机类型（如 `docker`）
- `BUILD_TYPE`: 构建类型
- `FLAVOR`: 构建风味（dev, alpha 等）
- `BUILD_ON`: 构建平台
- `APP_UID/GID`: 容器用户权限（默认 1000）
- `ENABLE_SIGN_UP`: 是否允许用户注册（默认 true）
- `MEDIA_SERVICE`: 媒体存储后端（`minio` 或 `filesystem`）

### Gradle 属性

在 `~/.gradle/gradle.properties` 中配置：

```properties
gpr.user=<GitHub username>
gpr.key=<GitHub personal access token>
```

## 开发工具

### Gradle 依赖清理

```bash
# 自动修剪未使用的 implementation 依赖
./scripts/build_scripts/gradle-prune-implementations.sh

# 指定构建风味
./scripts/build_scripts/gradle-prune-implementations.sh -Pserver.flavor=alpha
```

### Appium 测试

```bash
# 启用 Appium 测试模块
./gradlew build -Pappium=true

# 需要同时构建两个 APK
./gradlew :app:android:assembleDebug :panel:android:assembleDebug
```

## CI/CD

Alpha Server CI 在启动远端服务前会执行以下测试：
- `:backend:minio:test`
- `:cloud:cli:test`
- `:cloud:service:test`
- `:cloud:server:test`

启用 Testcontainers 路径覆盖：
```bash
ENABLE_TEST_CONTAINER=true ./gradlew test
```

## 架构说明

### RefCell 模式
- `TopicRefCell`, `RoomRefCell`, `CommunityRefCell`, `UserRefCell` 通过 `LocalRefCellHandlerProvider` 获取 LoadingHandler
- 不直接创建 ViewModel，使用依赖注入方式

### Android 模块划分
- `app/android` - 应用壳模块，包含 Activity 和 Manifest
- `app/composeApp` - 共享 Compose UI 和 actual 实现
- composeApp 不能编译期依赖 android 模块，使用显式 Intent 启动 Activity

### Panel / Worker 通信
- Worker 执行记录存储在 `TaskRecords` 表
- Panel 通过 `/admin/task-records` API 查询和管理

## 更多文档

详细的项目设计和实现细节请参考 [project.md](project.md)

## License

本项目为私有项目，保留所有权利。
