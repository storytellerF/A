package jvm_based

import androidx.compose.ui.test.*
import com.storyteller_f.a.app.AppInternal
import com.storyteller_f.a.app.compontents.TopicContentField
import com.storyteller_f.shared.model.TopicInfo
import kotlin.test.Test


class TopicContentTest : UsingContextTest() {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun myTest() = jvmBasedTest {

        runComposeUiTest {
            setContent {
                AppInternal("http://localhost:8811", "ws://localhost:8811")
            }

            onNodeWithTag("me").performClick()
        }
    }
}

