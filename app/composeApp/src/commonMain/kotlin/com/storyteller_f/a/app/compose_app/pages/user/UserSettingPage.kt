package com.storyteller_f.a.app.compose_app.pages.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import com.attafitamim.krop.core.crop.*
import com.attafitamim.krop.core.images.ImageBitmapSrc
import com.attafitamim.krop.ui.ImageCropperDialog
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.LocalToaster
import com.storyteller_f.a.app.compose_app.bus
import com.storyteller_f.a.app.compose_app.compontents.*
import com.storyteller_f.a.app.compose_app.model.OnUserUpdated
import com.storyteller_f.a.app.compose_app.pages.topic.MediaPicker
import com.storyteller_f.a.app.compose_app.pages.topic.uploadPath
import com.storyteller_f.a.app.compose_app.utils.ImageFormat
import com.storyteller_f.a.app.compose_app.utils.androidAllowHardware
import com.storyteller_f.a.app.compose_app.utils.coilImageToImageBitmap
import com.storyteller_f.a.app.compose_app.utils.saveImageBitmap
import com.storyteller_f.a.client.core.SessionManager
import com.storyteller_f.a.client.core.updateUserInfo
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.checkMediaDimensionRatioMatch
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

sealed class SettingOption(open val value: String?) {
    data class Name(override val value: String?) : SettingOption(value)
    data class Aid(override val value: String?) : SettingOption(value)
    data class Icon(override val value: String?) : SettingOption(value)
    data class Poster(override val value: String?) : SettingOption(value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSettingPage() {
    var currentOption by remember {
        mutableStateOf<SettingOption?>(null)
    }
    val sheetState = rememberModalBottomSheetState()
    val userSessionManager = LocalSessionManager.current
    val myInfo by userSessionManager.sessionModel.userHandler.data.collectAsState()
    val my = myInfo
    val showDialog = { option: SettingOption ->
        currentOption = option
    }
    val closeDialog = {
        currentOption = null
    }
    my?.let { m ->
        val globalDialogController = LocalGlobalDialog.current
        Scaffold { padding ->
            UserSettingInternal(padding, showDialog, m)
            val sessionManager = LocalSessionManager.current
            val scope = rememberCoroutineScope()
            ObjectSettingDialog(closeDialog, currentOption, sheetState, {
                scope.launch {
                    globalDialogController.use {
                        val newInfo = sessionManager.updateUserInfo(
                            UpdateUserBody(avatar = it.id)
                        ).getOrThrow()
                        sessionManager.sessionModel.updateUser(newInfo)
                        closeDialog()
                    }
                }
            }) {
                scope.launch {
                    updateUser(currentOption, sessionManager, it, globalDialogController, closeDialog)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectSettingDialog(
    closeDialog: () -> Unit,
    currentOption: SettingOption?,
    sheetState: SheetState,
    onInputMedia: (MediaInfo) -> Unit,
    onInputString: (String) -> Unit,
) {
    val sessionManager = LocalSessionManager.current
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()

    val ratio = when (currentOption) {
        is SettingOption.Poster -> AspectRatio(3, 4)
        else -> AspectRatio(1, 1)
    }
    val imageCropper = rememberImageCropper()
    val cropState = imageCropper.cropState
    if (cropState != null) {
        ImageCropperDialog(
            state = cropState,
            cropperStyle(
                shapes = listOf(RectCropShape),
                aspects = listOf(ratio)
            )
        )
    }

    val userSessionManager = LocalSessionManager.current
    val myInfo by userSessionManager.sessionModel.userHandler.data.collectAsState()
    val my = myInfo
    val mediaTarget = ObjectTuple(my?.id ?: 0, ObjectType.USER)
    val globalDialogController = LocalGlobalDialog.current
    MediaPicker(
        currentOption is SettingOption.Icon || currentOption is SettingOption.Poster,
        sheetState,
        mediaTarget,
        listOf("files"),
        { mediaList ->
            processSelectedMedia(
                mediaList,
                scope,
                context,
                sessionManager,
                imageCropper,
                ratio,
                mediaTarget,
                globalDialogController,
                onInputMedia
            )
        }
    ) {
        closeDialog()
    }

    currentOption?.let {
        InputDialog(it !is SettingOption.Icon && it !is SettingOption.Poster, it.value.orEmpty(), {
            closeDialog()
        }, onInputString)
    }
}

private fun processSelectedMedia(
    mediaList: List<MediaInfo>,
    scope: CoroutineScope,
    context: PlatformContext,
    sessionManager: SessionManager,
    imageCropper: ImageCropper,
    ratio: AspectRatio,
    mediaTarget: ObjectTuple,
    globalDialogController: GlobalDialogController,
    onInputMedia: (MediaInfo) -> Unit,
) {
    val info = mediaList.first()
    val dimension = info.dimension
    if (dimension == null || !info.contentType.startsWith("image/")) {
        scope.launch {
            globalDialogController.showMessage("invalid image: ${info.contentType} $dimension")
        }
    } else {
        if (checkMediaDimensionRatioMatch(
                dimension,
                Dimension(ratio.x, ratio.y)
            )
        ) {
            onInputMedia(info)
        }
        scope.launch {
            globalDialogController.useResult {
                cropImage(
                    context,
                    sessionManager,
                    info,
                    imageCropper,
                    mediaTarget
                )
            }.onSuccess {
                if (it != null) {
                    onInputMedia(it)
                }
            }
        }
    }
}

private suspend fun GlobalDialogController.cropImage(
    context: PlatformContext,
    sessionManager: SessionManager,
    info: MediaInfo,
    imageCropper: ImageCropper,
    mediaTarget: ObjectTuple,
): Result<MediaInfo?> {
    val image = useResult {
        val image = ImageLoader(context)
            .execute(imageRequest(context, sessionManager.client, info).androidAllowHardware(false).build())
            .image
        image?.coilImageToImageBitmap() ?: Result.failure(Exception("download"))
    }
    return image.mapResult {
        when (val result = imageCropper.crop(ImageBitmapSrc(it))) {
            CropResult.Cancelled -> {
                Result.success(null)
            }

            is CropError -> {
                Result.failure(Exception(result.name))
            }

            is CropResult.Success -> {
                useResult {
                    saveImageBitmap(
                        result.bitmap,
                        info.name.substringBeforeLast("."),
                        when (info.contentType) {
                            "image/webp" -> ImageFormat.WEBP
                            "image/jpeg", "image/jpg" -> ImageFormat.JPEG
                            else -> ImageFormat.PNG
                        }
                    )
                }
            }
        }
    }.mapIfNotNull {
        uploadPath(it, sessionManager, mediaTarget).getOrThrow()?.first()
    }
}

@Composable
private fun UserSettingInternal(
    values: PaddingValues,
    showDialog: (SettingOption) -> Unit,
    m: UserInfo,
) {
    val toasterState = LocalToaster.current
    val sessionManager = LocalSessionManager.current
    val scope = rememberCoroutineScope()
    val globalDialogController = LocalGlobalDialog.current
    Column(modifier = Modifier.padding(values).padding(horizontal = 20.dp)) {
        SettingOptionResettableView(
            "Icon",
            m.avatar != null,
            {
                if (it) {
                    scope.launch {
                        globalDialogController.use {
                            val body = UpdateUserBody(avatar = 0)
                            val newInfo = sessionManager.updateUserInfo(body).getOrThrow()
                            bus.emit(
                                OnUserUpdated(
                                    newInfo
                                )
                            )
                        }
                    }
                } else {
                    showDialog(SettingOption.Icon(m.avatar?.fullName))
                }
            },
            {
                UserIcon(m, setClickEvent = false)
            }
        )
        SettingOptionView("Name", {
            showDialog(SettingOption.Name(m.nickname))
        }, {
            Text(m.nickname, textDecoration = TextDecoration.Underline)
        })
        val aid = m.aid
        SettingOptionView("Aid", {
            if (aid == null) {
                showDialog(SettingOption.Aid(aid))
            } else {
                toasterState.show("forbid", duration = 1.seconds)
            }
        }, {
            if (aid == null) {
                Text("undefined", textDecoration = TextDecoration.Underline)
            } else {
                Text(aid)
            }
        })
    }
}

private suspend fun updateUser(
    showInputDialog: SettingOption?,
    sessionManager: SessionManager,
    string: String,
    globalDialogController: GlobalDialogController,
    closeDialog: () -> Unit,
) {
    val body = when (showInputDialog) {
        is SettingOption.Name -> UpdateUserBody(nickname = string)

        is SettingOption.Aid -> UpdateUserBody(aid = string)

        else -> null
    } ?: return
    globalDialogController.use {
        val newInfo = sessionManager.updateUserInfo(body).getOrThrow()
        sessionManager.sessionModel.updateUser(newInfo)
        bus.emit(
            OnUserUpdated(
                newInfo
            )
        )
        closeDialog()
    }
}

@Composable
fun InputDialog(show: Boolean, init: String, dismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember {
        mutableStateOf(init)
    }
    if (show) {
        AlertDialog(dismiss, {
            Button({
                onConfirm(input)
            }) {
                Text("OK")
            }
        }, text = {
            OutlinedTextField(input, {
                input = it
            })
        })
    }
}
