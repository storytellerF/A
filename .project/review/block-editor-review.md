# Block 编辑器 Code Review 报告

**提交**: `feat: 实现 Block 编辑器（类似 Notion 的结构化编辑）`
**提交哈希**: `bf942871`
**Review 日期**: 2026-04-26
**文件数**: 9 | **代码变更**: +2040, -3
**发现问题**: 12 个 (3 Critical, 4 High, 4 Medium, 1 Low)

---

## 🔴 Critical Issues (阻塞合并)

### Issue #1: BlockToolbar 的 onInsertBlock 回调是空操作，工具栏完全失效

**位置**: `app/composeApp/src/commonMain/kotlin/com/storyteller_f/a/app/pages/topic/TopicComposePage.kt:466-470`
**类别**: 逻辑错误

**问题描述**:
```kotlin
BlockToolbar(
    onInsertBlock = { block ->
        // 工具栏插入的块会通过 BlockEditor 内部状态处理
    }
)
```
`BlockToolbar` 被传入一个空回调。当用户点击工具栏按钮（如插入标题、列表、代码块等）时，什么也不会发生。注释声称 "工具栏插入的块会通过 BlockEditor 内部状态处理"，但 `BlockEditor` 没有任何外部 API 可以接收插入请求——它只管理内部 `mutableStateListOf`。

**影响**: 工具栏的所有按钮都是装饰性的，用户无法通过工具栏插入任何 Block 类型，这是一个完全损坏的功能。

**修复建议**: 需要建立 `BlockToolbar` 和 `BlockEditor` 之间的通信。推荐方案：将 `blocks` 列表提升到 `BlockEditTopicPage` 层，由页面同时传递给 Toolbar 和 Editor。

**相关代码**: `BlockEditor.kt:32-36` — blocks 是内部 `remember` 状态，外部无法访问

---

### Issue #2: ContentBlock 缺少 @SerialName 注解，多态序列化可能崩溃

**位置**: `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/block/BlockModel.kt:9-124`
**类别**: 类型安全 / 架构

**问题描述**:
`ContentBlock` 是一个 `@Serializable sealed interface`，其所有子类都标记了 `@Serializable`，但**没有任何一个子类有 `@SerialName` 注解**。项目的 `commonJson` 配置是 `Json { ignoreUnknownKeys = true }`，没有配置多态序列化。

对比项目中其他 sealed interface 的写法（如 `TopicContent`），所有子类都明确标注了 `@SerialName`。

**影响**: 如果将来有任何代码尝试将 `List<ContentBlock>` 持久化（如本地存储、网络传输），序列化/反序列化将失败。这是定时炸弹。

**修复建议**: 为每个子类添加 `@SerialName` 注解：
```kotlin
@Serializable
@SerialName("paragraph")
data class Paragraph(...) : ContentBlock
```

**参考**: `shared/src/commonMain/kotlin/com/storyteller_f/shared/model/TopicInfo.kt:74-108`

---

### Issue #3: LaunchedEffect(markdown) 在每次 recomposition 时都会触发回调

**位置**: `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/block/BlockEditor.kt:42-48`
**类别**: 性能 / 逻辑

**问题描述**:
```kotlin
val markdown by derivedStateOf {
    generateMarkdownFromBlocks(blocks.toList())
}

LaunchedEffect(markdown) {
    onMarkdownChange(markdown)
}
```

`derivedStateOf` 在 `blocks` 列表内容变化时产生新的 `markdown` 值，触发 `LaunchedEffect`。

**问题**: `BlockEditor` 的 `initialMarkdown` 参数只在 `remember { }` 块中读取一次。但如果父组件的 `input` 变化（例如从另一个 Tab 切换回来时），`initialMarkdown` 会有新值但 `BlockEditor` 不会重新解析。

**影响**:
- 用户在不同 Tab 之间切换时，Block 编辑器的内容可能与预期不一致
- 大量 block 时，`derivedStateOf` + `LaunchedEffect` 组合可能在快速编辑时产生竞态条件

**修复建议**: 使用 `key(initialMarkdown)` 来在初始内容变化时重置 blocks，或在 `LaunchedEffect` 中加入防抖逻辑。

---

## 🟠 High Priority Issues (合并前修复)

### Issue #4: parseQuote 的正则只移除第一个 > 符号

**位置**: `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/block/MarkdownToBlockParser.kt:147-155`
**类别**: 逻辑

**问题**: `replace(Regex("^>\\s*"), "")` 只会替换**第一个匹配**。多行引用后续行的 `>` 符号不会被移除。

**修复**: 使用 `replace(Regex("(?m)^>\\s*"), "")`（多行模式）

---

### Issue #5: extractListItemText 过滤掉子节点的方式不够健壮

**位置**: `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/block/MarkdownToBlockParser.kt:135-145`
**类别**: 逻辑

**问题**: 只处理**直接子节点**，嵌套列表或内联格式会丢失。`indent` 始终为 `0`。

**修复**: 递归处理子节点，或至少添加注释说明当前限制。

---

### Issue #6: parseCodeFence 中 getLang 可能返回 "null" 字符串

**位置**: `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/block/MarkdownToBlockParser.kt:157-186`
**类别**: 逻辑

**问题**: 当没有语言标记时，`getLang` 返回 `"null"` 字符串（`null?.toString()` 的结果）。导致代码块被错误地标记为 `language = "null"`。

**修复**:
```kotlin
val lang = getLang(node, content).lowercase().takeIf { it != "null" && it.isNotBlank() } ?: ""
```

---

### Issue #7: setBlockEditorContent 暴露了内部可变状态

**位置**: `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/block/BlockEditor.kt:126-132`
**类别**: 架构

**问题**: 函数接受 `MutableList<ContentBlock>` 但外部无法知道必须是 `mutableStateListOf`。且与 `BlockEditor` 内部管理模式矛盾。

**修复**: 删除此函数，或改为受控方法。

---

## 🟡 Medium Priority Issues

### Issue #8: EditableImageBlock、EditableObjectBlock、EditableRefBlock 没有编辑能力

**位置**: `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/block/EditableBlock.kt:431-504`
**类别**: UX

**问题**: 这三种 Block 在 `isFocused = true` 时只显示 "TODO" 文本，不提供任何编辑入口。

**修复**: 添加基础文本输入框让用户可以手动输入 URL/path。

---

### Issue #9: DividerBlock 使用 Spacer 而非 HorizontalDivider

**位置**: `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/block/EditableBlock.kt:563-576`
**类别**: 代码质量

**问题**: `fillMaxWidth()` 后跟 `.width(100.dp)` 矛盾。应使用 `HorizontalDivider`。

**修复**: 使用 `HorizontalDivider` Composable。

---

### Issue #10: onContentChange 的语义对不同类型 Block 不一致

**位置**: `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/block/BlockEditor.kt:109-121`
**类别**: 架构

**问题**: ImageBlock → `alt`, ObjectBlock → `title`, RefBlock → `refPath`，但这些分支永远不会被调用（因为这三种类型没有 `onContentChange` 回调）。

**修复**: 添加注释标明这些分支目前不会被触发，或移除死代码。

---

### Issue #11: 测试缺少边界情况和异常场景

**位置**: `app/core/src/commonTest/kotlin/com/storyteller_f/a/app/core/components/block/BlockParserTest.kt`
**类别**: 测试

**缺少的测试场景**:
1. object block 解析失败时的降级（无效 JSON）
2. 特殊字符处理（包含 `#`、`>`、`*` 等）
3. 空行和多余空白
4. 多行 quote 的生成
5. `generateImageMarkdown` 中所有字段为空的边界情况

---

## 🔵 Low Priority Issues

### Issue #12: BlockToolbar 中的 Heading 按钮使用了 FormatBold 图标

**位置**: `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/block/BlockToolbar.kt:58-60`
**类别**: UX

**问题**: 插入的是 Heading (level=1)，但图标是 `FormatBold`（加粗）。

**修复**: 使用 `Icons.Default.Title` 图标。

---

## ✅ 修复优先级

### 立即修复（阻塞合并）:
1. **Issue #1** — 修复 BlockToolbar 和 BlockEditor 之间的通信
2. **Issue #2** — 为 ContentBlock 所有子类添加 @SerialName

### 合并前修复:
3. **Issue #3** — 修复 LaunchedEffect 状态同步问题
4. **Issue #4** — 修复多行 quote 解析错误
5. **Issue #6** — 修复 getLang 返回 "null" 字符串的 bug

### 后续迭代:
6. **Issue #8** — 完成 Image/Object/Ref Block 的编辑功能
7. **Issue #11** — 补充边界情况测试
8. **Issue #7** — 重构 setBlockEditorContent 的 API 设计
9. **Issue #9** — 使用 HorizontalDivider
10. **Issue #12** — 修复图标

### 架构建议:
- 当前设计对 MVP 可接受，但未来建议引入 ViewModel 或专门的 BlockDocument 状态管理类
- 考虑引入 kotlinx-uuid 替代 java.util.UUID
