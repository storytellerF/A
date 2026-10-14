/*
 * This is a private project. All rights reserved.
 */

package device_based

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.pages.AdaptiveItemGrid
import com.storyteller_f.a.app.pages.AdaptiveSectionScaffold
import com.storyteller_f.a.app.pages.DetailReadingColumn
import com.storyteller_f.a.app.pages.HomeFeedLayout
import com.storyteller_f.a.app.pages.search.DesktopSearchLayout
import com.storyteller_f.a.client.compose_core.components.NavRoute
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeAdaptiveUiTest {
    @Test
    fun resizingPreservesSectionSearchAndScroll() =
        runComposeUiTest {
        val width = mutableStateOf(540.dp)
        val routes =
            listOf(
                "World",
                "Communities",
                "Rooms",
            ).map { NavRoute("/${it.lowercase()}", Icons.Default.Public, it) }
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(width.value, 600.dp)) {
                    AdaptiveSectionScaffold(rememberPagerState { 3 }, routes, false, search = { page, _ ->
                        var query by rememberSaveable { mutableStateOf("") }
                        BasicTextField(query, { query = it }, Modifier.testTag("query-$page"))
                    }) { page ->
                        LazyColumn(Modifier.fillMaxSize().testTag("list-$page")) {
                            items(100) { index -> Text("$page:$index", Modifier.requiredSize(200.dp, 48.dp)) }
                        }
                    }
                }
            }
        }
        onNodeWithTag("Rooms").performClick()
        onNodeWithTag("query-2").performTextReplacement("saved room search")
        onNodeWithTag("list-2").performScrollToIndex(20)
        runOnIdle { width.value = 1000.dp }
        onNodeWithTag("query-2").assertTextEquals("saved room search")
        onNodeWithText("2:20").assertExists()
        runOnIdle { width.value = 540.dp }
        onNodeWithTag("query-2").assertTextEquals("saved room search")
        onNodeWithText("2:20").assertExists()
    }

    @Test
    fun contextAppearsWithoutResettingReadingContent() =
        runComposeUiTest {
        val width = mutableStateOf(900.dp)
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(width.value, 600.dp)) {
                    HomeFeedLayout(context = {
                        Column {
                            Text("Home context")
                            var query by rememberSaveable { mutableStateOf("") }
                            BasicTextField(query, { query = it }, Modifier.testTag("context-state"))
                        }
                    }) {
                        var query by rememberSaveable { mutableStateOf("") }
                        BasicTextField(query, { query = it }, Modifier.testTag("reading-state"))
                    }
                }
            }
        }
        onNodeWithTag("reading-state").performTextReplacement("keep reading")
        onNodeWithText("Home context").assertDoesNotExist()
        runOnIdle { width.value = 1280.dp }
        onNodeWithText("Home context").assertExists()
        onNodeWithTag("reading-state").assertTextEquals("keep reading")
        onNodeWithTag("context-state").performTextReplacement("saved context")
        runOnIdle { width.value = 900.dp }
        onNodeWithText("Home context").assertDoesNotExist()
        onNodeWithTag("reading-state").assertTextEquals("keep reading")
        runOnIdle { width.value = 1280.dp }
        onNodeWithTag("context-state").assertTextEquals("saved context")
    }

    @Test
    fun searchResultsUseMoreWidthAndFitShortViewport() =
        runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(1000.dp, 320.dp).testTag("viewport")) {
                    DesktopSearchLayout("Communities", true, {
                        BasicTextField("Design", {}, Modifier.testTag("search-input"))
                    }, {}) {
                        LazyColumn(Modifier.fillMaxSize().testTag("results")) {
                            items(100) { Text("Result $it", Modifier.requiredSize(200.dp, 48.dp)) }
                        }
                    }
                }
            }
        }
        val inputBounds = onNodeWithTag("search-input").getUnclippedBoundsInRoot()
        val resultsBounds = onNodeWithTag("results").getUnclippedBoundsInRoot()
        val viewportBounds = onNodeWithTag("viewport").getUnclippedBoundsInRoot()
        val inputWidth = inputBounds.right - inputBounds.left
        val resultsWidth = resultsBounds.right - resultsBounds.left
        assertTrue(inputWidth <= 640.dp)
        assertTrue(resultsWidth > inputWidth)
        assertTrue(resultsBounds.bottom <= viewportBounds.bottom)
        onNodeWithTag("results").performScrollToIndex(80)
        onNodeWithText("Result 80").assertExists()
    }

    @Test
    fun communityGridRetainsVisibleItemAcrossColumnChanges() =
        runComposeUiTest {
        val width = mutableStateOf(540.dp)
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(width.value, 600.dp)) {
                    AdaptiveItemGrid {
                        items(100, key = { it }) { Text("Community $it", Modifier.requiredSize(200.dp, 80.dp)) }
                    }
                }
            }
        }
        onNodeWithTag("adaptive-grid").performScrollToIndex(20)
        runOnIdle { width.value = 1000.dp }
        onNodeWithText("Community 20").assertExists()
        runOnIdle { width.value = 540.dp }
        onNodeWithText("Community 20").assertExists()
    }

    @Test
    fun detailReadingAndComposerStayAlignedAcrossResizes() =
        runComposeUiTest {
        val width = mutableStateOf(1280.dp)
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(width.value, 320.dp)) {
                    DetailReadingColumn {
                        LazyColumn(Modifier.weight(1f).fillMaxSize().testTag("detail-list")) {
                            items(100) { Text("Message $it", Modifier.requiredSize(200.dp, 48.dp)) }
                        }
                        var draft by rememberSaveable { mutableStateOf("") }
                        BasicTextField(draft, { draft = it }, Modifier.fillMaxSize().weight(0.2f).testTag("composer"))
                    }
                }
            }
        }
        val reading = onNodeWithTag("detail-list").getUnclippedBoundsInRoot()
        val composer = onNodeWithTag("composer").getUnclippedBoundsInRoot()
        assertTrue(reading.right - reading.left <= 900.dp)
        assertTrue(reading.left == composer.left && reading.right == composer.right)
        onNodeWithTag("composer").performTextReplacement("Saved draft")
        onNodeWithTag("detail-list").performScrollToIndex(20)
        runOnIdle { width.value = 390.dp }
        onNodeWithTag("composer").assertTextEquals("Saved draft")
        onNodeWithText("Message 20").assertExists()
        runOnIdle { width.value = 1280.dp }
        onNodeWithTag("composer").assertTextEquals("Saved draft")
        onNodeWithText("Message 20").assertExists()
    }
}
