package jvm_based

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.storyteller_f.a.app.compontents.TopicContentField
import com.storyteller_f.shared.model.TopicInfo
import kotlin.test.Test


class TopicContentTest : UsingContextTest() {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun myTest() = jvmBasedTest {
        runComposeUiTest {
            setContent {
                TopicContentField(TopicInfo.EMPTY.copy(content = com.storyteller_f.shared.model.TopicContent.Plain("hello")))
            }

            onNodeWithTag("content").assertTextEquals("hello")
        }
    }
}

