# LaTeX Migration Review — Commit `e83173b2`

**Date:** 2026-04-26
**Commit:** `refactor: replace jlatexmath with huarangmeng/latex KMP library`
**Reviewer:** Qoder

---

## Summary of Changes

| Category | Details |
|----------|---------|
| **Files changed** | 11 files |
| **Lines** | +125 / -328 (net -203) |
| **Dependencies removed** | `jlatexmath`, `jlatexmath-android`, `jlatexmath-android-font-cyrillic`, `jlatexmath-android-font-greek` |
| **Dependencies added** | `latex-base`, `latex-parser`, `latex-renderer` (io.github.huarangmeng v1.3.0) |
| **Files deleted** | `Tex.kt` (expect), `Tex.android.kt` (actual), `Tex.jvm.kt` (actual) |
| **Key changes** | Replaced platform-specific PNG pre-rendering with Compose-native `Latex` component; removed `generateMathIfNeed` logic from 6 ViewModels; removed `TextStyle`/`Density` params from ViewModel constructors and factory functions |

---

## Issues Found

### HIGH — Unused Imports (Compilation Warnings)

#### Issue #1: Unused `TextStyle` and `Density` imports in `Model.kt`
**Location:** `app/composeApp/src/commonMain/kotlin/com/storyteller_f/a/app/common/Model.kt:3-4`

**Issue:**
```kotlin
import androidx.compose.ui.text.TextStyle       // line 3 — unused
import androidx.compose.ui.unit.Density          // line 4 — unused
```
All ViewModel constructors (`WorldViewModel`, `TopicsViewModel`, `IdTopicViewModel`, `AidTopicViewModel`, `SubscriptionsViewModel`, `UserCommentsViewModel`) had their `TextStyle`/`Density` parameters removed, and no other code in this file uses these types.

**Impact:** Unused imports will produce compiler warnings (or errors if the project has `-Werror` enabled).

**Fix:** Remove lines 3-4.

---

#### Issue #2: Unused `TopicContent` import in `MathInlineContent.kt`
**Location:** `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/MathInlineContent.kt:31`

**Issue:**
```kotlin
import com.storyteller_f.shared.model.TopicContent   // line 31 — unused
```
The old `generateMathIfNeed` body destructured `TopicContent.Plain`, but the new body is just `return info`. No other code in this file references `TopicContent`.

**Impact:** Unused import warning.

**Fix:** Remove line 31.

---

### MEDIUM — Dead Code

#### Issue #3: `buildByMarkdown` is dead code
**Location:** `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/MathInlineContent.kt:192-203`

**Issue:**
The `buildByMarkdown` function was previously called from `Build.kt` to construct ViewModels with typography/density context. The import was removed from `Build.kt` (confirmed via diff), and grep shows **zero callers** remain across the entire codebase.

```kotlin
@Composable
fun <T> buildByMarkdown(block: @Composable (typography: MarkdownTypography, density: Density) -> T): T {
    // ... 11 lines, never called
}
```

**Impact:** Dead code increases maintenance burden and confuses readers.

**Fix:** Delete the entire function (lines 191-203).

---

#### Issue #4: `generateMathIfNeed` is effectively dead code
**Location:** `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/MathInlineContent.kt:82-91`

**Issue:**
The function still exists with its full 4-parameter signature, but:
- **Zero callers** remain across the codebase (all 6+ callers in `Model.kt` were removed).
- The body is a no-op: `return info`.

```kotlin
fun generateMathIfNeed(
    info: TopicInfo,
    textStyle: TextStyle,
    inlineCodeTextStyle: TextStyle,
    density: Density
): TopicInfo {
    // No longer pre-renders LaTeX to PNG images.
    // Math is now rendered directly via Compose Latex component at render time.
    return info
}
```

**Impact:** Dead code. The misleading parameter list suggests it does something.

**Fix:** Either:
- **Option A (recommended):** Delete the function entirely — no callers exist.
- **Option B:** Keep it for API stability but remove the unused parameters and mark it `@Deprecated("No-op since LaTeX migration", ReplaceWith("info"))`.

---

### MEDIUM — Naming & Semantics

#### Issue #5: `mathImageInlineContent` is misnamed
**Location:** `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/MathInlineContent.kt:54-80`

**Issue:**
The function was renamed from `imageInlineContent` to `mathImageInlineContent`, but it is **still used for regular images** in `imageAnnotator`:

```kotlin
MarkdownElementTypes.IMAGE -> {
    // ...
    inlineContentMap[id] = mathImageInlineContent(   // line 131 — this is NOT math, it's a regular image
        uri = name,
        mediaMap = dimensionMap,
        // ...
    )
}
```

The name implies math-specific behavior, but the function handles generic image inline content (dimension calculation, transformer invocation).

**Impact:** Misleading name makes code harder to read.

**Fix:** Rename back to `imageInlineContent` (or to a more neutral name like `mediaInlineContent`).

---

### MEDIUM — Missing Dark Mode Differentiation

#### Issue #6: `darkColor` is always set to the same value as `color`
**Locations:**
- `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/CodeFence.kt:150`
- `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/MathInlineContent.kt:109`

**Issue:**
Both usages pass `style.color` for both `color` and `darkColor`:

```kotlin
// CodeFence.kt:147-151
config = LatexConfig(
    fontSize = textStyle.fontSize,
    color = textStyle.color,
    darkColor = textStyle.color      // same as color
)

// MathInlineContent.kt:106-110
config = LatexConfig(
    fontSize = style.fontSize,
    color = style.color,
    darkColor = style.color          // same as color
)
```

The `huarangmeng/latex` library was designed with automatic dark mode support via `darkColor`. Setting both to the same value means the LaTeX formula will look identical in light and dark mode.

**Impact:** In dark mode, dark-colored LaTeX formulas on a dark background will have poor readability (and vice versa for light mode).

**Fix:** Use theme-aware colors. Verify whether `style.color` is already theme-aware in your Material 3 setup. If not, use `MaterialTheme.colorScheme.onBackground` for `darkColor` in dark mode contexts.

---

### MEDIUM — Potential Visual Regression

#### Issue #7: No `backgroundColor` passed to `LatexConfig`
**Locations:**
- `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/CodeFence.kt:145-152`
- `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/MathInlineContent.kt:104-112`

**Issue:**
The old jlatexmath pipeline explicitly passed `backgroundColor`:
```kotlin
// Old code (deleted Tex.kt)
val backgroundColor = style.background.toArgb()
val textColor = style.color.toArgb()
saveLatexToImage(tex, backgroundColor, textColor, size, outputStream)
```

The new `LatexConfig` does **not** have a `backgroundColor` parameter. For inline math, the background was previously baked into the PNG. Now the `Latex` component renders with no explicit background.

The `InlineTextContent` placeholder has no background set, so inline math formulas will render against whatever background the surrounding text has. The old code specifically set the background from `style.background` (for inline math: `colors.inlineCodeBackground`).

**Impact:** Inline math may render against a different background than before.

**Fix:** If the visual difference is undesirable, wrap `LatexInlineContent` in a `Surface` with the appropriate background:
```kotlin
Surface(color = style.background) {
    LatexInlineContent(tex, style)
}
```

---

### LOW — Heuristic Width Estimation

#### Issue #8: Inline math placeholder width uses a crude character-count heuristic
**Location:** `app/core/src/commonMain/kotlin/com/storyteller_f/a/app/core/components/MathInlineContent.kt:159-163`

**Issue:**
```kotlin
val estimatedWidth = if (child.type == GFMElementTypes.INLINE_MATH) {
    fontSizePx * (1 + tex.length * 0.6f) // rough width estimate
}
```

Character count does not correlate well with actual LaTeX rendering width. For example, `\sum_{i=0}^{n}` (16 chars) renders much wider than `a+b+c+d+e+f` (12 chars).

**Impact:** The placeholder may be too narrow (text overlap during loading) or too wide (blank space). Once `Latex` renders, it self-sizes correctly, so this is only a transient layout shift.

**Fix:** Accept the tradeoff, or use a wider safety factor (e.g., `1.0f` instead of `0.6f`).

---

### LOW — Dependency Question

#### Issue #9: `latex-parser` dependency may be redundant
**Locations:**
- `app/composeApp/build.gradle.kts:180-182`
- `app/core/build.gradle.kts:127-129`

**Issue:**
The code only imports from `latex-base` and `latex-renderer`:
```kotlin
import com.hrm.latex.renderer.Latex
import com.hrm.latex.renderer.model.LatexConfig
```

The renderer likely depends on the parser internally.

**Impact:** Negligible — at worst an extra explicit dependency.

**Fix:** Verify compilation works without explicit `latex-parser`. If so, remove it.

---

### LOW — Panel Module Dependency Note

#### Issue #10: Panel module removed jlatexmath without adding latex deps
**Location:** `panel/composeApp/build.gradle.kts`

**Issue:**
The panel module had jlatexmath removed but no new latex deps added. Panel depends on `projects.app.core` which transitively provides the new latex dependencies. Grep confirms panel has no direct latex references.

**Impact:** None — this is correct.

**Fix:** None needed.

---

## Architecture Assessment

### What is Good
1. **Eliminated expect/actual pattern** — Old `Tex.kt` / `Tex.android.kt` / `Tex.jvm.kt` required platform-specific PNG rendering. New `Latex` component works uniformly across all KMP targets.
2. **Removed PNG cache layer** — Eliminated file I/O, image metadata reading (via Kim), and cache management complexity.
3. **ViewModel simplification** — Removing `TextStyle`/`Density` from ViewModel constructors significantly simplifies the factory pattern in `Build.kt` (from nested `buildByMarkdown` blocks to direct `customViewModel` calls).
4. **Dead import cleanup in Build.kt** — The `buildByMarkdown` import was correctly removed from Build.kt.
5. **Dependency cleanup** — jlatexmath references are fully removed from all modules (confirmed via grep across all `.kt`, `.kts`, `.toml` files).

### What to Watch
1. **Performance** — Old approach pre-rendered images during data loading (ViewModel layer). New approach renders LaTeX at compose time. First render of a formula incurs parsing + rendering cost inline. Monitor performance on lists with many formulas.
2. **State preservation** — Verify that scrolling lists of formulas does not cause unnecessary re-parsing.

---

## Recommended Actions

### Before Next Commit
1. **Remove unused imports** in `Model.kt` (lines 3-4) and `MathInlineContent.kt` (line 31)
2. **Delete dead code**: `buildByMarkdown` (MathInlineContent.kt:191-203) and `generateMathIfNeed` (MathInlineContent.kt:82-91)
3. **Rename** `mathImageInlineContent` to `imageInlineContent` (revert the rename since it handles all images, not just math)

### Before Merge
4. **Test dark mode** — Verify LaTeX formulas have adequate contrast in both light and dark themes (Issue #6)
5. **Visual regression test** — Compare inline math rendering before/after to ensure background handling is correct (Issue #7)
6. **Performance test** — Scroll through a topic list with many inline and block math formulas to check for jank

### Future
7. Consider whether the inline math width estimation (Issue #8) needs improvement based on real-world usage
