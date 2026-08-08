/*
 * This is a private project. All rights reserved.
 */

import kotlin.test.Test

class AppAndroidAppiumTest : AppiumTestBase() {
    private val targetHelper = AppAppiumHelper()
    private val platformHelper = AndroidAppiumHelper()

    @Test
    fun `test sign up`() = testSignUpByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test sign in as system user`() =
        testSignInAsSystemUserByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test sign in by injected private session`() =
        testInjectedSessionByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test publish topic in user space`() =
        testPublishTopicInUserSpaceByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test favorite topic from topic page`() =
        testFavoriteTopicByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test opens asciidoc preview`() =
        testOpenAsciidocPreviewByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test subscribe topic from community page`() =
        testSubscribeTopicByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test community profile actions from joined community`() =
        testCommunityProfileActionsByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test publish topic in joined community`() =
        testPublishTopicInCommunityByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test publish topic in community room`() =
        testPublishTopicInCommunityRoomByHelper(name.methodName, targetHelper, platformHelper)
}
