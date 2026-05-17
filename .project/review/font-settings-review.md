# Code Review Findings - Community Font Settings Feature

**Date:** 2026-04-26
**Commit:** 8ec8bdaa
**Files Changed:** 26 (24 modified, 2 added)
**Findings:** 7 issues (1 critical, 2 high, 3 medium, 1 low)

---

## 🔴 Critical Issues

### Issue #1: Font Settings File Selection Is Completely Broken
**Location:** `app/composeApp/src/commonMain/kotlin/com/storyteller_f/a/app/pages/user/UserSettingPage.kt:193-195` and `UserSettingPage.kt:210-230`
**Category:** Logic / UX

**Issue:**
`FontSettingsPage` reuses `ObjectSettingDialog` for font selection, but two independent bugs prevent it from working:

1. `showFilePicker()` only returns `true` for `Icon`, `Poster`, and `RoomIcon`. The new `ContentFont`, `CodeFont`, and `FallbackFont` options are excluded, so `FilePicker` never opens.
2. Even if the picker opened, `processSelectedMedia()` unconditionally validates `contentType.startsWith("image/")`. Font files (`.ttf`, `.otf`) are not images and will be rejected with `"invalid image: ..."`.

**Impact:**
Users can **never assign fonts** to a community via the new Font Settings screen. The only working interaction is "Reset" (clearing a font). The feature is effectively shipped dead.

**Fix:**
1. Extended `showFilePicker()` to include the three font option types.
2. In `ObjectSettingDialog`, detect font options and bypass image validation/cropping, directly passing the selected file to `onInputMedia`.

**Status:** ✅ Fixed

---

## 🟠 High Priority

### Issue #2: Conditional Composable Calls Violate Compose Rules
**Location:** `app/composeApp/src/commonMain/kotlin/com/storyteller_f/a/app/pages/community/CommunityPage.kt:154-156, 171-174`
**Category:** Architecture / Logic

**Issue:**
`getFontSettings()` calls `getDownloadViewModel()` and `collectAsState()` inside conditional `?.let` blocks:

```kotlin
val contentDownloadViewModel = fontSettings?.settings?.contentFontId?.let { getDownloadViewModel(it) }
```

Compose requires composable functions to be called unconditionally in the same order on every recomposition. When `contentFontId` moves from `null` to non-null, `getDownloadViewModel()` appears at a new call site, which can cause Compose to lose state tracking or crash.

**Fix:**
Call `getDownloadViewModel` unconditionally with the nullable ID:
```kotlin
val contentDownloadViewModel = getDownloadViewModel(fontSettings?.settings?.contentFontId)
```
`getDownloadViewModel` already accepts `PrimaryKey?` and handles null correctly. Also fixed unused `id` parameter in `LaunchedEffect` lambdas.

**Status:** ✅ Fixed

---

### Issue #3: Database Schema Breaking Change Without Migration
**Location:** `backend/exposed/src/main/kotlin/com/storyteller_f/a/backend/exposed/tables/Communities.kt:18` and `ExposedDatabaseFactory.kt:260-291`
**Category:** Architecture / Logic

**Issue:**
The `Communities` table replaced `fontId = customPrimaryKey("font_id").nullable()` with `fontSettings = text("font_settings").nullable()`. `ExposedDatabaseFactory.init()` only calls `SchemaUtils.create(*tables)`, which executes `CREATE TABLE IF NOT EXISTS` — it **does not add missing columns** to existing tables.

Additionally, `migration()` generates migration statements via `MigrationUtils` but **does not execute or log them**.

**Impact:**
Existing deployments will throw column-not-found errors when querying `COMMUNITIES.FONT_SETTINGS`. The old `font_id` data is orphaned and lost.

**Fix:**
Implemented programmatic migration in `ExposedDatabaseFactory.migration()` that executes the returned statements and logs each one.

**Status:** ✅ Fixed

---

## 🟡 Medium Priority

### Issue #4: Backend Does Not Validate Font File Types
**Location:** `cloud/service/src/main/kotlin/com/storyteller_f/a/cloud/core/service/CommunityService.kt:345-368`
**Category:** Security / Validation

**Issue:**
`checkCommunityFontSettingsForUpdate()` only verifies that the referenced file IDs exist in the database. There is no check for content type, file ownership/permission, or object status.

**Impact:**
An admin could reference an arbitrary non-font file (e.g., an executable or private document) as a "font," which would then be downloaded to clients.

**Fix:**
Added content type validation after fetching files:
```kotlin
val invalidFile = files.firstOrNull {
    !it.contentType.startsWith("font/") && !it.contentType.startsWith("application/font-")
}
```

**Status:** ✅ Fixed

---

### Issue #5: JsonPreview Opens a Non-Dismissing Input Dialog
**Location:** `app/composeApp/src/commonMain/kotlin/com/storyteller_f/a/app/pages/community/FontSettingsPage.kt:188-198`
**Category:** UX / Logic

**Issue:**
Tapping "Preview JSON" sets `currentOption = SettingOption.JsonPreview(null)`, which triggers `ObjectSettingDialog`. Because `JsonPreview` is not a file-picker option, `InputDialog` is shown instead. The `onInputString` callback is an empty lambda `{}`. When the user taps **OK**, the dialog does **not** dismiss itself.

**Fix:**
Removed the `showDialog` call from the "Preview JSON" row. The JSON is already displayed inline, so no dialog is needed.

**Status:** ✅ Fixed

---

### Issue #6: Silent JSON Deserialization Failure
**Location:** `backend/exposed/src/main/kotlin/com/storyteller_f/a/backend/exposed/tables/Communities.kt:27-30`
**Category:** Logic / Error Handling

**Issue:**
```kotlin
val fontSettings = fontSettingsJson?.let {
    runCatching { json.decodeFromString<FontSettings>(it) }.getOrNull()
}
```
If `font_settings` contains malformed JSON, it is silently swallowed and treated as `null`. No log or metric is emitted.

**Fix:**
Added error logging:
```kotlin
runCatching { ... }
    .onFailure { Napier.e("Failed to parse font_settings", it) }
    .getOrNull()
```

**Status:** ✅ Fixed

---

## 🔵 Low Priority

### Issue #7: Unused Variables and Minor Cleanup
**Location:** `app/composeApp/src/commonMain/kotlin/com/storyteller_f/a/app/pages/community/CommunityPage.kt:160-168` and `panel/CommunityDetailPage.kt`
**Category:** Other

**Issue:**
In `LaunchedEffect(fontSettings)`, three `let` blocks bind `id` but never use it. Also, `panel/CommunityDetailPage.kt` instantiates `Json { prettyPrint = false }` on every recomposition instead of reusing a singleton.

**Fix:**
Removed unused `id` parameter in `LaunchedEffect` lambdas. Moved the `Json` instance to a file-level constant in `CommunityDetailPage.kt`.

**Status:** ✅ Fixed
