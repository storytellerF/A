package com.storyteller_f.a.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.storyteller_f.a.app.*

@Preview(showSystemUi = true)
@Composable
private fun PreviewLogin() {
    SelectLoginPage(loginNav = LoginNav.DEFAULT)
}

@Preview
@Composable
private fun PreviewPrivateKey() {
    InputPrivateKeyPage {

    }
}
