# 为 dev/cli 增加 --status 命令

## 1. 仓库研究结论

### 现有结构
- **dev/cli**: CLI 工具入口，位于 [CliMain.kt](file:///home/kx/Projects/A/dev/cli/src/main/kotlin/com/storyteller_f/a/app/dev_cli/CliMain.kt)
- **dev/core**: 包含 RPC 服务接口定义 [DevControlService.kt](file:///home/kx/Projects/A/dev/core/src/main/kotlin/com/storyteller_f/a/app/dev/DevControlService.kt) 和进程管理工具
- **dev/server**: DevControlService 的实现 [DevServerApplication.kt](file:///home/kx/Projects/A/dev/server/src/main/kotlin/com/storyteller_f/a/app/dev_server/DevServerApplication.kt)

### 技术栈
- Kotlin 21
- kotlinx.cli (命令行参数解析)
- kotlinx.rpc (RPC 通信)
- Ktor (网络通信)

## 2. 需要修改的文件
1. [dev/core/src/main/kotlin/com/storyteller_f/a/app/dev/DevControlService.kt](file:///home/kx/Projects/A/dev/core/src/main/kotlin/com/storyteller_f/a/app/dev/DevControlService.kt) - 添加获取状态的新 RPC 方法
2. [dev/server/src/main/kotlin/com/storyteller_f/a/app/dev_server/DevServerApplication.kt](file:///home/kx/Projects/A/dev/server/src/main/kotlin/com/storyteller_f/a/app/dev_server/DevServerApplication.kt) - 实现状态获取方法
3. [dev/cli/src/main/kotlin/com/storyteller_f/a/app/dev_cli/CliMain.kt](file:///home/kx/Projects/A/dev/cli/src/main/kotlin/com/storyteller_f/a/app/dev_cli/CliMain.kt) - 添加 --status 命令并实现相应逻辑

## 3. 实现步骤

### 步骤 1: 定义新的 RPC 接口
- 在 `DevControlService` 中添加 `getStatus()` 方法
- 返回一个包含 server 和 worker 状态的数据对象

### 步骤 2: 实现服务器端状态获取
- 在 `DevControlServiceImpl` 中实现 `getStatus()` 方法
- 通过检查 `specialProcessMap` 中的 `cloud-server` 和 `cloud-worker` 键确定状态

### 步骤 3: 实现客户端 --status 命令
- 在 `CliMain.kt` 中添加 `--status` 选项
- 实现检查 8888 端口是否有进程监听的功能
- 如果有进程，连接到 RPC 并获取状态
- 打印友好的状态信息

## 4. 潜在依赖与考虑
- 使用 JDK 内置的网络库或系统命令检查 8888 端口
- 确保跨平台兼容性（Windows/Mac/Linux）
- 保持与现有代码风格一致

## 5. 风险处理
- 8888 端口检查失败时提供清晰的错误信息
- RPC 连接失败时提供友好提示
