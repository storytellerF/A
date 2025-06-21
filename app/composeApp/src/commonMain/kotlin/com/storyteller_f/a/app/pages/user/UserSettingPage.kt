package com.storyteller_f.a.app.pages.user

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
import com.storyteller_f.a.app.LocalSessionManager
import com.storyteller_f.a.app.LocalToaster
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.compontents.SettingOptionResettableView
import com.storyteller_f.a.app.compontents.SettingOptionView
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.compontents.imageRequest
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.model.OnUserUpdated
import com.storyteller_f.a.app.pages.room.getCurrentUserInfo
import com.storyteller_f.a.app.pages.topic.MediaPicker
import com.storyteller_f.a.app.pages.topic.uploadPath
import com.storyteller_f.a.app.utils.ImageFormat
import com.storyteller_f.a.app.utils.androidAllowHardware
import com.storyteller_f.a.app.utils.coilImageToImageBitmap
import com.storyteller_f.a.app.utils.saveImageBitmap
import com.storyteller_f.a.client_lib.SessionManager
import com.storyteller_f.a.client_lib.UserSessionManager
import com.storyteller_f.a.client_lib.updateUserInfo
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.checkMediaDimensionRatioMatch
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.ObjectType
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
    val my = getCurrentUserInfo()
    val showDialog = { option: SettingOption ->
        currentOption = option
    }
    val closeDialog = {
        currentOption = null
    }
    my?.let { m ->
        Scaffold { padding ->
            UserSettingInternal(padding, showDialog, m)
            val sessionManager = LocalSessionManager.current
            val scope = rememberCoroutineScope()
            ObjectSettingDialog(closeDialog, currentOption, sheetState, {
                scope.launch {
                    globalDialogState.use {
                        val newInfo = sessionManager.updateUserInfo(
                            UpdateUserBody(avatar = it.id)
                        ).getOrThrow()
                        sessionManager.sessionModel.updateUser(newInfo)
                        closeDialog()
                    }
                }
            }) {
                scope.launch {
                    updateUser(currentOption, sessionManager, it, closeDialog)
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
    onInputString: (String) -> Unit
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

    val my = getCurrentUserInfo()
    val mediaTarget = ObjectTuple(my?.id ?: 0, ObjectType.USER)
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
    sessionManager: UserSessionManager,
    imageCropper: ImageCropper,
    ratio: AspectRatio,
    mediaTarget: ObjectTuple,
    onInputMedia: (MediaInfo) -> Unit
) {
    val info = mediaList.first()
    val dimension = info.dimension
    if (dimension == null || !info.contentType.startsWith("image/")) {
        globalDialogState.showMessage("invalid image: ${info.contentType} $dimension")
    } else {
        scope.launch {
            cropImageIfNeed(
                context,
                sessionManager,
                info,
                imageCropper,
                dimension,
                ratio,
                mediaTarget
            ).onSuccess {
                if (it != null) {
                    onInputMedia(it)
                }
            }.onFailure {
                globalDialogState.showErrorState(it)
            }
        }
    }
}

private suspend fun cropImageIfNeed(
    context: PlatformContext,
    sessionManager: SessionManager,
    info: MediaInfo,
    imageCropper: ImageCropper,
    dimension: Dimension,
    aspectRatio: AspectRatio,
    mediaTarget: ObjectTuple
): Result<MediaInfo?> {
    return if (checkMediaDimensionRatioMatch(dimension, Dimension(aspectRatio.x, aspectRatio.y))) {
        Result.success(info)
    } else {
        cropImage(context, sessionManager, info, imageCropper, mediaTarget)
    }
}

private suspend fun cropImage(
    context: PlatformContext,
    sessionManager: SessionManager,
    info: MediaInfo,
    imageCropper: ImageCropper,
    mediaTarget: ObjectTuple
): Result<MediaInfo?> {
    val image = globalDialogState.use {
        ImageLoader(context)
            .execute(imageRequest(context, sessionManager.client, info).androidAllowHardware(false).build())
            .image?.coilImageToImageBitmap()
    }
    return image.mapResult {
        if (it == null) {
            Result.failure(Exception("download failed"))
        } else {
            when (val result = imageCropper.crop(ImageBitmapSrc(it))) {
                CropResult.Cancelled -> {
                    Result.success(null)
                }

                is CropError -> {
                    Result.failure(Exception(result.name))
                }

                is CropResult.Success -> {
                    globalDialogState.use {
                        val data = saveImageBitmap(
                            result.bitmap,
                            info.name.substringBeforeLast("."),
                            when (info.contentType) {
                                "image/webp" -> ImageFormat.WEBP
                                "image/jpeg", "image/jpg" -> ImageFormat.JPEG
                                else -> ImageFormat.PNG
                            }
                        )
                        uploadPath(data, sessionManager, mediaTarget).getOrThrow()?.first()
                    }
                }
            }
        }
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
    Column(modifier = Modifier.padding(values).padding(horizontal = 20.dp)) {
        SettingOptionResettableView("Icon", m.avatar != null, {
            if (it) {
                scope.launch {
                    globalDialogState.use {
                        val body = UpdateUserBody(avatar = 0)
                        val newInfo = sessionManager.updateUserInfo(body).getOrThrow()
                        bus.emit(OnUserUpdated(newInfo))
                    }
                }
            } else {
                showDialog(SettingOption.Icon(m.avatar?.fullName))
            }
        }, {
            UserIcon(m, setClickEvent = false)
        })
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
    closeDialog: () -> Unit
) {
    val body = when (showInputDialog) {
        is SettingOption.Name -> UpdateUserBody(nickname = string)

        is SettingOption.Aid -> UpdateUserBody(aid = string)

        else -> null
    } ?: return
    globalDialogState.use {
        val newInfo = sessionManager.updateUserInfo(body).getOrThrow()
        sessionManager.sessionModel.updateUser(newInfo)
        bus.emit(OnUserUpdated(newInfo))
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
