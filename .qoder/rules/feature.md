---
trigger: model_decision
description: 增加新功能
---
# 增加新功能

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
    - 实现: 在 `client/room` 新增 `Room*Storage` 实现

4. **UI 接入**
    - 在 `ViewModel` 中通过对应 `SessionManager` 调用请求函数
    - 需要分页则使用 `buildPager` 和PagingViewModel
    - 在compose 中使用StateView