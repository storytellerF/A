package com.storyteller_f.a.app.pages.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import com.attafitamim.krop.core.crop.AspectRatio
import com.attafitamim.krop.core.crop.CropError
import com.attafitamim.krop.core.crop.CropResult
import com.attafitamim.krop.core.crop.ImageCropper
import com.attafitamim.krop.core.crop.RectCropShape
import com.attafitamim.krop.core.crop.crop
import com.attafitamim.krop.core.crop.cropperStyle
import com.attafitamim.krop.core.crop.rememberImageCropper
import com.attafitamim.krop.core.images.ImageBitmapSrc
import com.attafitamim.krop.ui.ImageCropperDialog
import com.storyteller_f.a.app.AppGlobalDialogController
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.LocalUserInfo
import com.storyteller_f.a.app.common.OnUserUpdated
import com.storyteller_f.a.app.components.SettingOptionResettableView
import com.storyteller_f.a.app.components.SettingOptionView
import com.storyteller_f.a.app.core.CoreStrings
import com.storyteller_f.a.app.core.components.CustomAlertDialog
import com.storyteller_f.a.app.core.components.CustomAlertDialogController
import com.storyteller_f.a.app.core.components.LocalToaster
import com.storyteller_f.a.app.core.components.emitEvent
import com.storyteller_f.a.app.core.components.rememberAlertDialogController
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.app.core.utils.ImageFormat
import com.storyteller_f.a.app.core.utils.getRemoteImageBitmap
import com.storyteller_f.a.app.core.utils.saveImageBitmap
import com.storyteller_f.a.app.pages.topic.FilePicker
import com.storyteller_f.a.app.pages.topic.uploadPath
import com.storyteller_f.a.client.core.updateUserInfo
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.checkMediaFileDimensionRatioMatch
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class SettingOption(open val value: String?) {
    data class Name(override val value: String?) : SettingOption(value)
    data class Aid(override val value: String?) : SettingOption(value)
    data class Icon(override val value: String?) : SettingOption(value)
    data class Poster(override val value: String?) : SettingOption(value)
    data class RoomIcon(override val value: String?, val roomId: PrimaryKey?) : SettingOption(value)
    data class ContentFont(override val value: String?) : SettingOption(value)
    data class CodeFont(override val value: String?) : SettingOption(value)
    data class FallbackFont(override val value: String?) : SettingOption(value)
    data class JsonPreview(override val value: String?) : SettingOption(value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSettingPage() {
    var currentOption by remember {
        mutableStateOf<SettingOption?>(null)
    }
    val sheetState = rememberModalBottomSheetState()
    val my = LocalUserInfo.current ?: return
    val showDialog = { option: SettingOption ->
        currentOption = option
    }
    val closeDialog = {
        currentOption = null
    }
    val globalDialogController = LocalGlobalDialog.current
    Scaffold { padding ->
        UserSettingInternal(padding, showDialog, my)
        val scope = rememberCoroutineScope()
        ObjectSettingDialog(closeDialog, currentOption, sheetState, {
            scope.launch {
                globalDialogController.useResult {
                    request {
                        updateUserInfo(UpdateUserBody(avatar = it.id))
                    }
                }.onSuccess { newInfo ->
                    globalDialogController.context.sessionManager.model.updateUser(newInfo)
                    closeDialog()
                }
            }
        }) {
            scope.launch {
                updateUser(currentOption, it, globalDialogController, closeDialog)
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
    onInputMedia: (FileInfo) -> Unit,
    onInputString: (String) -> Unit,
) {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()

    val ratio = getRatio(currentOption)
    val imageCropper = rememberImageCropper()
    val cropState = imageCropper.cropState
    if (cropState != null) {
        ImageCropperDialog(state = cropState, cropperStyle(shapes = listOf(RectCropShape), aspects = listOf(ratio)))
    }

    val myInfo = LocalUserInfo.current
    val mediaTarget = getMediaTarget(currentOption, myInfo)
    val globalDialogController = LocalGlobalDialog.current
    val alertDialogController = rememberAlertDialogController()
    val showSheet = showFilePicker(currentOption)
    FilePicker(
        showSheet,
        sheetState,
        mediaTarget,
        listOf("files"),
        requiredDimension = Dimension(ratio.x, ratio.y),
        { mediaList ->
            processSelectedMedia(
                mediaList,
                scope,
                context,
                imageCropper,
                ratio,
                mediaTarget,
                globalDialogController,
                alertDialogController,
                onInputMedia
            )
        }
    ) {
        closeDialog()
    }

    currentOption?.let {
        val showInput = !showSheet
        InputDialog(showInput, it.value.orEmpty(), {
            closeDialog()
        }, onInputString)
    }
    CustomAlertDialog(alertDialogController, {
        alertDialogController.close()
    }) {
    }
}

private fun getMediaTarget(
    currentOption: SettingOption?,
    my: UserInfo?
): ObjectTuple = if (currentOption is SettingOption.RoomIcon && currentOption.roomId != null) {
    ObjectTuple(currentOption.roomId, ObjectType.ROOM)
} else {
    ObjectTuple(my?.id ?: 0, ObjectType.USER)
}

private fun getRatio(currentOption: SettingOption?): AspectRatio {
    val dimension = when (currentOption) {
        is SettingOption.RoomIcon -> Dimension.ROOM_DIMENSION
        is SettingOption.Poster -> Dimension.COMMUNITY_POSTER
        else -> Dimension.DEFAULT_DIMENSION
    }
    return AspectRatio(dimension.width, dimension.height)
}

private fun showFilePicker(currentOption: SettingOption?): Boolean = currentOption is SettingOption.Icon ||
    currentOption is SettingOption.Poster ||
    currentOption is SettingOption.RoomIcon

private fun processSelectedMedia(
    mediaList: List<FileInfo>,
    scope: CoroutineScope,
    context: PlatformContext,
    imageCropper: ImageCropper,
    ratio: AspectRatio,
    mediaTarget: ObjectTuple,
    globalDialogController: AppGlobalDialogController,
    alertDialogController: CustomAlertDialogController,
    onInputMedia: (FileInfo) -> Unit,
) {
    val info = mediaList.first()
    val dimension = info.dimension
    if (dimension == null || !info.contentType.startsWith("image/")) {
        alertDialogController.showTitle("invalid image: ${info.contentType} $dimension")
        return
    }
    if (checkMediaFileDimensionRatioMatch(
            dimension,
            Dimension(ratio.x, ratio.y)
        )
    ) {
        onInputMedia(info)
        return
    }
    scope.launch {
        globalDialogController.useResult {
            cropImage(context, info, imageCropper, mediaTarget)
        }.onSuccess {
            if (it != null) {
                onInputMedia(it)
            }
        }
    }
}

private suspend fun AppGlobalDialogController.cropImage(
    context: PlatformContext,
    info: FileInfo,
    imageCropper: ImageCropper,
    mediaTarget: ObjectTuple,
): Result<FileInfo?> {
    val image = useResult {
        getRemoteImageBitmap(this.context.sessionManager, context, info)
            ?: Result.failure(Exception("download"))
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
                    val name = info.name.substringBeforeLast(".")
                    saveImageBitmap(
                        result.bitmap,
                        "tmpImage/$name-cropped.png",
                        when (info.contentType) {
                            "image/webp" -> ImageFormat.WEBP
                            "image/jpeg", "image/jpg" -> ImageFormat.JPEG
                            else -> ImageFormat.PNG
                        },

                    )
                }
            }
        }
    }.mapIfNotNull {
        uploadPath(it, mediaTarget).getOrThrow()?.first()
    }
}

@Composable
private fun UserSettingInternal(
    values: PaddingValues,
    showDialog: (SettingOption) -> Unit,
    m: UserInfo,
) {
    val toasterState = LocalToaster.current
    val scope = rememberCoroutineScope()
    val globalDialogController = LocalGlobalDialog.current
    Column(modifier = Modifier.padding(values).padding(horizontal = 20.dp)) {
        SettingOptionResettableView(
            "Icon",
            m.avatar != null,
            {
                if (it) {
                    scope.launch {
                        globalDialogController.useResult {
                            val body = UpdateUserBody(avatar = 0)
                            request { updateUserInfo(body) }
                        }.onSuccess { newInfo ->
                            globalDialogController.emitEvent(OnUserUpdated(newInfo))
                        }
                    }
                } else {
                    showDialog(SettingOption.Icon(m.avatar?.fullName))
                }
            },
            {
                UserIconWithDialog(m, setClickEvent = false)
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
                toasterState.showMessage("forbid")
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
    string: String,
    globalDialogController: AppGlobalDialogController,
    closeDialog: () -> Unit,
) {
    val body = when (showInputDialog) {
        is SettingOption.Name -> UpdateUserBody(nickname = string)

        is SettingOption.Aid -> UpdateUserBody(aid = string)

        else -> null
    } ?: return
    globalDialogController.useResult {
        request {
            updateUserInfo(body)
        }
    }.onSuccess { newInfo ->
        globalDialogController.context.sessionManager.model.updateUser(newInfo)
        globalDialogController.emitEvent(OnUserUpdated(newInfo))
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
                Text(CoreStrings.ok())
            }
        }, text = {
            OutlinedTextField(input, {
                input = it
            })
        })
    }
}
