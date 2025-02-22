package jvm_based

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.storyteller_f.a.app.AppInternal
import kotlin.test.Test


class TopicContentTest : UsingContextTest() {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testApp() = jvmBasedTest {
        onActivity {
            runComposeUiTest {
                setContent {
                    AppInternal(it, it.replace("http", "ws"))
                }

                onNodeWithTag("me").performClick()
            }
        }

    }
}

