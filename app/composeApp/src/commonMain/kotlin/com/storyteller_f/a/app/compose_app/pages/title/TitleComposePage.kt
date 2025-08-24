package com.storyteller_f.a.app.compose_app.pages.title

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.bus
import com.storyteller_f.a.app.compose_app.compontents.BaseSheet
import com.storyteller_f.a.app.compose_app.compontents.TopicContentField
import com.storyteller_f.a.app.compose_app.model.OnTitleCreated
import com.storyteller_f.a.app.compose_app.model.createMemberSearchViewModel
import com.storyteller_f.a.app.compose_app.model.createRoomSearchViewModel
import com.storyteller_f.a.app.compose_app.model.createSearchCommunitiesViewModel
import com.storyteller_f.a.app.compose_app.pages.community.CommunityList
import com.storyteller_f.a.app.compose_app.pages.community.CommunityRefCell
import com.storyteller_f.a.app.compose_app.pages.room.RoomList
import com.storyteller_f.a.app.compose_app.pages.room.RoomRefCell
import com.storyteller_f.a.app.compose_app.pages.user.MemberList
import com.storyteller_f.a.app.compose_app.pages.user.UserRefCell
import com.storyteller_f.a.client.core.SessionManager
import com.storyteller_f.a.client.core.createTitle
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.NewTitle
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch

@Composable
fun TitleComposePage() {
    val userSessionManager = LocalSessionManager.current
    val myInfo by userSessionManager.sessionModel.userHandler.data.collectAsState()
    val user = myInfo
    user?.let {
        TitleComposeInternal()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleComposeInternal() {
    var name by remember {
        mutableStateOf("")
    }
    var showSheet by remember {
        mutableStateOf("")
    }
    var titleScope by remember {
        mutableStateOf<ObjectTuple?>(null)
    }
    var titleType by remember {
        mutableStateOf(TitleType.REGULAR)
    }
    var receiver by remember {
        mutableStateOf<PrimaryKey?>(null)
    }
    val appNav = LocalAppNav.current
    val sessionManager = LocalSessionManager.current
    val content by remember {
        mutableStateOf("")
    }
    val scope = rememberCoroutineScope()
    val globalDialogController = LocalGlobalDialog.current
    CommonComposePage({
        scope.launch {
            globalDialogController.useResult {
                createTitle(titleType, receiver, titleScope, sessionManager, name, content)
            }.onSuccess {
                appNav.back()
            }
        }
    }) {
        TitleComposeInternal2(name, {
            name = it
        }, titleType, {
            titleType = it
        }, titleScope, {
            titleScope = null
        }, receiver, content) {
            showSheet = it
        }
    }
    val sheetState = rememberModalBottomSheetState()
    ObjectPicker(showSheet.isNotBlank(), sheetState, {
        showSheet = ""
    }, if (showSheet == "scope") listOf(ObjectType.COMMUNITY) else listOf(ObjectType.USER)) {
        showSheet.let { s ->
            if (s == "scope") {
                titleScope = it
                showSheet = ""
            } else if (s == "receiver") {
                receiver = it.objectId
                showSheet = ""
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonComposePage(onCheck: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = {
        TopAppBar({
        }, actions = {
            IconButton(onClick = {
                onCheck()
            }) {
                Icon(Icons.Filled.Check, contentDescription = null)
            }
        })
    }) { paddingValues ->
        val direction = LocalLayoutDirection.current
        Box(
            Modifier.padding(
                top = paddingValues.calculateTopPadding(),
                start = paddingValues.calculateStartPadding(direction),
                end = paddingValues.calculateEndPadding(direction)
            ).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            content()
        }
    }
}

@Composable
fun TitleComposeInternal2(
    name: String,
    updateName: (String) -> Unit,
    titleType: TitleType,
    updateTitleType: (TitleType) -> Unit,
    titleScope: ObjectTuple?,
    updateTitleScope: () -> Unit,
    receiver: PrimaryKey?,
    content: String,
    showSheet: (String) -> Unit
) {
    Column(
        modifier = Modifier.width(300.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(name, updateName, label = {
            Text("name")
        }, modifier = Modifier.fillMaxWidth())

        TitleTypeSelector(titleType, updateTitleType)

        val shape = RoundedCornerShape(10.dp)
        TitleScopeEditor(shape, showSheet, titleScope, updateTitleScope)

        ReceiverEditor(shape, showSheet, receiver)

        DescriptionEditor(content)
    }
}

@Composable
private fun DescriptionEditor(content: String) {
    val descriptionShape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.clip(descriptionShape)
            .background(MaterialTheme.colorScheme.primaryContainer, descriptionShape)
            .clickable {
            }
            .padding(10.dp)
    ) {
        Row {
            Text("Description", modifier = Modifier.weight(1f))
            Icon(Icons.Default.Edit, "edit")
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (content.isEmpty()) {
            Text("No Description", fontStyle = FontStyle.Italic, fontSize = 10.sp)
        } else {
            TopicContentField(TopicInfo.EMPTY.copy(content = TopicContent.Plain(content)))
        }
    }
}

@Composable
private fun ReceiverEditor(
    shape: RoundedCornerShape,
    showSheet: (String) -> Unit,
    receiver: PrimaryKey?
) {
    Row(
        modifier = Modifier.height(78.dp).fillMaxWidth().clip(shape).clickable {
            showSheet("receiver")
        }.background(MaterialTheme.colorScheme.primaryContainer, shape)
            .padding(8.dp)
    ) {
        receiver.let {
            if (it != null) {
                UserRefCell(it)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("接受人")
                }
            }
        }
    }
}

@Composable
private fun TitleScopeEditor(
    shape: RoundedCornerShape,
    showSheet: (String) -> Unit,
    titleScope: ObjectTuple?,
    updateTitleScope: () -> Unit
) {
    Row(
        modifier = Modifier.height(90.dp).fillMaxWidth().clip(shape).clickable {
            showSheet("scope")
        }.background(MaterialTheme.colorScheme.primaryContainer, shape)
            .padding(8.dp)
    ) {
        titleScope.let {
            if (it != null) {
                when (it.objectType) {
                    ObjectType.COMMUNITY -> CommunityRefCell(it.objectId) {
                        updateTitleScope()
                    }

                    ObjectType.ROOM -> RoomRefCell(it.objectId) {
                        updateTitleScope()
                    }

                    ObjectType.TOPIC -> TODO()
                    ObjectType.USER -> UserRefCell(it.objectId)

                    ObjectType.TITLE -> TODO()
                    ObjectType.File -> TODO()
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("作用范围")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TitleTypeSelector(
    titleType: TitleType,
    updateTitleType: (TitleType) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }
    ExposedDropdownMenuBox(expanded, {
        expanded = true
    }, modifier = Modifier) {
        TextField(
            // The `menuAnchor` modifier must be passed to the text field to handle
            // expanding/collapsing the menu on click. A read-only text field has
            // the anchor type `PrimaryNotEditable`.
            titleType.name,
            {
            },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            readOnly = true,
            label = { Text("Title Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
        )
        ExposedDropdownMenu(expanded, {
            expanded = false
        }, modifier = Modifier) {
            DropdownMenuItem({
                Text("Regular")
            }, {
                expanded = false
                updateTitleType(TitleType.REGULAR)
            })
            DropdownMenuItem({
                Text("Join")
            }, {
                expanded = false
                updateTitleType(TitleType.JOIN)
            })
        }
    }
}

private suspend fun createTitle(
    titleType: TitleType?,
    receiver: PrimaryKey?,
    titleScope: ObjectTuple?,
    sessionManager: SessionManager,
    name: String,
    content: String,
): Result<TitleInfo> {
    check(titleType != null) {
        "titleType is empty"
    }
    check(receiver != null) {
        "receiver is empty"
    }
    check(titleScope != null) {
        "titleScope is empty"
    }
    return sessionManager.createTitle(
        NewTitle(name, titleType, receiver, titleScope.objectId, titleScope.objectType, content)
    ).onSuccess { title ->
        bus.emit(OnTitleCreated(title))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectPicker(
    showSheet: Boolean,
    sheetState: SheetState,
    hideSheet: () -> Unit,
    supportObjectType: List<ObjectType>,
    onCheck: (ObjectTuple) -> Unit
) {
    BaseSheet(showSheet, sheetState, hideSheet) {
        var input by remember {
            mutableStateOf("")
        }
        Column(modifier = Modifier.height(300.dp).padding(top = 20.dp)) {
            var currentType by remember {
                mutableStateOf(supportObjectType[0])
            }
            TypeSelector(supportObjectType, currentType, {
                currentType = it
            }, input) {
                input = it
            }
            ObjectList(input, currentType, {
                onCheck(it.id ob ObjectType.COMMUNITY)
            }, {
                onCheck(it.id ob ObjectType.ROOM)
            }, {
                onCheck(it.id ob ObjectType.USER)
            })
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TypeSelector(
    supportObjectType: List<ObjectType>,
    currentType: ObjectType,
    setType: (ObjectType) -> Unit,
    input: String,
    setInput: (String) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }
    val closeTypeSelector = {
        expanded = false
    }
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val shape = RoundedCornerShape(10.dp)
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape)
                .clip(shape)
                .clickable {
                    expanded = true
                }.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                when (currentType) {
                    ObjectType.COMMUNITY -> Icons.Default.Diversity1
                    ObjectType.ROOM -> Icons.AutoMirrored.Default.Chat
                    ObjectType.TOPIC -> Icons.Default.Topic
                    ObjectType.USER -> Icons.Default.AccountBox
                    ObjectType.TITLE -> TODO()
                    ObjectType.File -> TODO()
                },
                "icon"
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        OutlinedTextField(input, setInput, modifier = Modifier.weight(1f))
    }

    DropdownMenu(expanded, closeTypeSelector) {
        supportObjectType.forEach {
            DropdownMenuItem({
                Text(it.name)
            }, {
                closeTypeSelector()
                setType(it)
            })
        }
    }
}

@Composable
fun ObjectList(
    input: String,
    currentType: ObjectType?,
    onClickCommunity: (CommunityInfo) -> Unit,
    onClickRoom: (RoomInfo) -> Unit,
    onClickUser: (UserInfo) -> Unit
) {
    if (input.isNotBlank() && currentType != null) {
        when (currentType) {
            ObjectType.COMMUNITY -> {
                val communitiesViewModel =
                    createSearchCommunitiesViewModel(JoinStatusSearch.JOINED, input)
                CommunityList(
                    communitiesViewModel.flow.collectAsLazyPagingItems(),
                    onClickCommunity
                )
            }

            ObjectType.ROOM -> {
                val roomsViewModel = createRoomSearchViewModel(JoinStatusSearch.JOINED, input)
                RoomList(roomsViewModel.flow.collectAsLazyPagingItems(), onClickRoom)
            }

            ObjectType.TOPIC -> TODO()
            ObjectType.USER -> {
                val membersViewModel = createMemberSearchViewModel(input)
                MemberList(membersViewModel.flow.collectAsLazyPagingItems(), onClickUser)
            }

            ObjectType.TITLE -> TODO()
            ObjectType.File -> TODO()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeMenu(
    showSheet: Boolean,
    sheetState: SheetState,
    hideSheet: () -> Unit,
    onCheck: (ObjectType) -> Unit
) {
    BaseSheet(showSheet, sheetState, hideSheet) {
        Column(
            modifier = Modifier.height(300.dp).padding(top = 20.dp).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ComposeMenuItem(Icons.Default.Diversity3, "create community") {
                onCheck(ObjectType.COMMUNITY)
            }

            ComposeMenuItem(Icons.Default.ChatBubble, "create room") {
                onCheck(ObjectType.ROOM)
            }

            ComposeMenuItem(Icons.Default.Title, "create title") {
                onCheck(ObjectType.TITLE)
            }
        }
    }
}

@Composable
fun ComposeMenuItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, shape).clip(shape)
            .clickable {
                onClick()
            }.padding(12.dp)
    ) {
        Icon(icon, title)
        Text(title)
    }
}
