package com.storyteller_f.a.app.pages.community

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.aid
import com.storyteller_f.a.app.common.OnCommunityCreated
import com.storyteller_f.a.app.core.components.emitEvent
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.app.member_join_policy
import com.storyteller_f.a.app.name
import com.storyteller_f.a.app.pages.title.CommonComposePage
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.shared.model.MemberPolicy
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun CommunityComposePage() {
    var name by remember {
        mutableStateOf("")
    }
    var aid by remember {
        mutableStateOf("")
    }
    var memberJoinPolicy by remember { mutableStateOf(MemberPolicy.OPEN) }

    val appNavFactory = LocalAppNavFactory.current
    val scope = rememberCoroutineScope()
    val globalDialogController = LocalGlobalDialog.current
    CommonComposePage({
        scope.launch {
            globalDialogController.useResult {
                request {
                    createCommunity(
                        NewCommunity(
                            name,
                            aid,
                            memberPolicy = memberJoinPolicy
                        )
                    )
                }
            }.onSuccess {
                globalDialogController.emitEvent(
                    OnCommunityCreated(
                        it
                    )
                )
                appNavFactory.newAppNav().back()
            }
        }
    }) {
        CommunityComposeInternal(name, aid, memberJoinPolicy, {
            name = it
        }, {
            memberJoinPolicy = it
        }, {
            aid = it
        })
    }
}

class MemberJoinPolicy(
    val title: String,
    val policy: MemberPolicy,
    val description: String
)

@Preview
@Composable
fun CommunityComposeInternal(
    name: String = "",
    aid: String = "",
    memberJoinPolicy: MemberPolicy = MemberPolicy.OPEN,
    onNameChange: (String) -> Unit = {},
    onMemberJoinPolicyChange: (MemberPolicy) -> Unit = {},
    onAidChange: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier.width(300.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(name, onValueChange = {
            onNameChange(it)
        }, label = {
            Text(stringResource(Res.string.name))
        })
        OutlinedTextField(aid, onValueChange = {
            onAidChange(it)
        }, label = {
            Text(stringResource(Res.string.aid))
        })
        val radioOptions = getMemberPolicies()
        CommunityMemberPolicyRadioGroup(radioOptions, memberJoinPolicy, onMemberJoinPolicyChange)
        SelectedMemberPolicyDescription(radioOptions, memberJoinPolicy)
    }
}

@Composable
private fun SelectedMemberPolicyDescription(
    radioOptions: List<MemberJoinPolicy>,
    memberJoinPolicy: MemberPolicy
) {
    val selectedDescription =
        radioOptions.firstOrNull { it.policy == memberJoinPolicy }?.description ?: ""
    Text(selectedDescription, modifier = Modifier.padding(horizontal = 10.dp))
}

private fun getMemberPolicies(): List<MemberJoinPolicy> {
    return listOf(
        MemberJoinPolicy(
            "Open",
            MemberPolicy.OPEN,
            "Anyone can join this community"
        ),
        MemberJoinPolicy(
            "Invite Only",
            MemberPolicy.INVITE_ONLY,
            "Only invite members can join this community"
        )
    )
}

@Composable
private fun CommunityMemberPolicyRadioGroup(
    radioOptions: List<MemberJoinPolicy>,
    memberJoinPolicy: MemberPolicy,
    onMemberJoinPolicyChange: (MemberPolicy) -> Unit
) {
    Column(
        Modifier.padding(horizontal = 10.dp).border(1.dp, MaterialTheme.colorScheme.primary)
            .padding(10.dp)
    ) {
        Text(stringResource(Res.string.member_join_policy))
        // Note that Modifier.selectableGroup() is essential to ensure correct accessibility behavior
        Column(Modifier.selectableGroup()) {
            radioOptions.forEach { text ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = (text.policy == memberJoinPolicy),
                            onClick = { onMemberJoinPolicyChange(text.policy) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (text.policy == memberJoinPolicy),
                        onClick = null // null recommended for accessibility with screen readers
                    )
                    Text(
                        text = text.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
    }
}
