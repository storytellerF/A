/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import kotlin.test.Test

class AppAndroidAppiumTest :
    AppiumTestBase<AppAppiumHelper, AndroidAppiumHelper>(
        AppAppiumHelper(),
        AndroidAppiumHelper(),
    ) {
    @Test
    fun `test explicit sign in and sign out survive restart`() = testSignoutPersistenceByHelper()

    @Test
    fun `test child sign in stays in memory across restart`() = testChildSigninByHelper()

    @Test
    fun `test sign in survives restart and revocation requires explicit login`() = testSigninLifecycleByHelper()

    @Test
    fun `test sign up`() = testSignUpByHelper()

    @Test
    fun `test sign in as system user`() = testSignInAsSystemUserByHelper()

    @Test
    fun `test sign in by injected private session`() = testInjectedSessionByHelper()

    @Test
    fun `test publish topic in user space`() = testPublishTopicInUserSpaceByHelper()

    @Test
    fun `test favorite topic from topic page`() = testFavoriteTopicByHelper()

    @Test
    fun `test opens asciidoc preview`() = testOpenAsciidocPreviewByHelper()

    @Test
    fun `test subscribe topic from community page`() = testSubscribeTopicByHelper()

    @Test
    fun `test community profile actions from joined community`() = testCommunityProfileActionsByHelper()

    @Test
    fun `test publish topic in joined community`() = testPublishTopicInCommunityByHelper()

    @Test
    fun `test activity previews track reading sending and navigation`() = testActivityPreviewByHelper()

    @Test
    fun `test private room activity previews decrypt and clear unread`() =
        testActivityPreviewByHelper(privateRoom = true)

    @Test
    fun `test room refresh clears unread after another session reads`() = testRoomActivityRefreshByHelper()

    @Test
    fun `test publish topic in community room`() = testPublishTopicInCommunityRoomByHelper()
}
