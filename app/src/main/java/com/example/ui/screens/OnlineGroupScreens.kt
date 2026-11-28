@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.local.AppConstants
import com.example.data.local.FriendRelationEntity
import com.example.data.remote.CloudGroup
import com.example.data.remote.StaffRole
import com.example.ui.theme.ChatSentBubble
import kotlinx.coroutines.delay

/** All public and private groups: the Owner's first, then the biggest. */
@Composable
fun OnlineGroupsTab(onOpenGroup: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel = LocalAppViewModel.current
    val myUid by viewModel.currentUserId.collectAsStateWithLifecycle()
    val live = rememberLive(myUid) { viewModel.groups.observeGroups() }
    val groups = live.data.orEmpty().sortedWith(
        compareByDescending<CloudGroup> { it.ownerUid == AppConstants.OWNER_UID }
            .thenByDescending { it.memberCount }
            .thenBy { it.name.lowercase() }
    )
    val isOwner = myUid == AppConstants.OWNER_UID
    var showCreate by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            liveStatus(live, "Abhi koi group nahi. Neeche se naya group banayen.")
            items(groups, key = { it.id }) { group ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenGroup(group.id) }
                        .testTag("group_card_${group.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (group.isPublic) Icons.Filled.Public else Icons.Filled.Lock,
                            contentDescription = if (group.isPublic) "Public" else "Private",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(group.name, style = MaterialTheme.typography.titleMedium)
                            if (group.description.isNotBlank()) {
                                Text(group.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            Text(
                                (if (group.isPublic) "Public" else "Private") +
                                    " • ${group.memberCount} members" +
                                    if (group.ownerUid == AppConstants.OWNER_UID) " • Owner ka group" else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
        ExtendedFloatingActionButton(
            onClick = { showCreate = true },
            icon = { Icon(Icons.Filled.AddCircle, contentDescription = null) },
            text = { Text(if (isOwner) "Naya Group" else "Naya Group (200 Coins)") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("buy_group_fab")
        )
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var isPublic by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Naya Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("Group ka naam") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(200) },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    SwitchRow(if (isPublic) "Public group" else "Private group", isPublic) { isPublic = it }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val a = name
                        val b = description
                        val c = isPublic
                        viewModel.runAction("Group ban gaya") { me ->
                            viewModel.groups.createGroup(me, a, b, c, if (isOwner) 0L else GROUP_PRICE)
                        }
                        showCreate = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Banayen") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}

private const val GROUP_PRICE = AppConstants.GROUP_PURCHASE_COINS

@Composable
fun OnlineGroupScreen(
    groupId: String,
    friendRelations: List<FriendRelationEntity>,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val viewModel = LocalAppViewModel.current
    val myUid = viewModel.currentUserId.collectAsStateWithLifecycle().value ?: return
    val myRole by viewModel.myRole.collectAsStateWithLifecycle()

    val groupState = rememberLive(groupId) { viewModel.groups.observeGroup(groupId) }
    val group = groupState.data?.value
    val membership = rememberLive(groupId, myUid) { viewModel.groups.observeMembership(groupId, myUid) }.data
    val member = membership?.value
    val isAppOwner = myUid == AppConstants.OWNER_UID
    val isModerator = myRole == StaffRole.MODERATOR
    val manages = group?.ownerUid == myUid || isAppOwner

    var showMembers by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Tell others which public group this user is in while the screen is open
    if (group != null && group.isPublic && member != null) {
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(groupId, myUid, lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    while (true) {
                        viewModel.account.setActivity(myUid, groupId, group.name)
                        delay(5L * 60L * 1000L)
                    }
                } finally {
                    viewModel.account.clearActivity(myUid)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(group?.name ?: "Group", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        if (group != null) {
                            Text("${group.memberCount} members", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                actions = {
                    if (member != null || isAppOwner || isModerator) {
                        IconButton(onClick = { showMembers = true }) {
                            Icon(Icons.Filled.Group, contentDescription = "Members")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                groupState.error != null -> Text(groupState.error, modifier = Modifier.padding(16.dp))
                groupState.data == null || membership == null ->
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                group == null -> Text("Yeh group ab mojood nahi", modifier = Modifier.padding(16.dp))
                member == null && !isAppOwner && !isModerator -> JoinPanel(group, myUid)
                else -> GroupMessages(
                    group = group,
                    memberName = member?.name,
                    isStaff = isAppOwner || isModerator,
                    canDelete = manages || isModerator,
                    onOpenProfile = { uid, name ->
                        viewModel.rememberPerson(uid, name)
                        selected = uid to name
                    }
                )
            }
        }
    }

    if (showMembers && group != null) {
        MembersDialog(
            group = group,
            myUid = myUid,
            manages = manages,
            amMember = member != null,
            onSelect = { uid, name ->
                viewModel.rememberPerson(uid, name)
                selected = uid to name
                showMembers = false
            },
            onLeft = {
                showMembers = false
                onBack()
            },
            onDismiss = { showMembers = false }
        )
    }

    selected?.let { (uid, name) ->
        UserProfileFor(
            uid = uid,
            name = name,
            friendRelations = friendRelations,
            onDismiss = { selected = null },
            group = GroupContext(groupId, canRemove = manages && uid != group?.ownerUid && uid != myUid),
            onChat = {
                selected = null
                onOpenChat(uid)
            }
        )
    }
}

@Composable
private fun JoinPanel(group: CloudGroup, myUid: String) {
    val viewModel = LocalAppViewModel.current
    val request = rememberLive(group.id, myUid) { viewModel.groups.observeOwnRequest(group.id, myUid) }.data
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(group.name, style = MaterialTheme.typography.headlineSmall)
        if (group.description.isNotBlank()) Text(group.description)
        Text("Group banane wala: ${group.ownerName}", style = MaterialTheme.typography.bodySmall)
        when {
            group.isPublic -> Button(onClick = {
                viewModel.runAction("Aap group mein shamil ho gaye") { me -> viewModel.groups.join(group.id, me) }
            }) { Text("Join Group") }
            request?.value != null -> Text("Request bhej di gayi. Group banane wale ke approve ka intezar karein.")
            else -> Button(onClick = {
                viewModel.runAction("Request bhej di gayi") { me ->
                    viewModel.groups.requestToJoin(group.id, me)
                }
            }) { Text("Join Request bhejein (Private)") }
        }
    }
}

@Composable
private fun GroupMessages(
    group: CloudGroup,
    memberName: String?,
    isStaff: Boolean,
    canDelete: Boolean,
    onOpenProfile: (String, String) -> Unit
) {
    val viewModel = LocalAppViewModel.current
    val myUid by viewModel.currentUserId.collectAsStateWithLifecycle()
    val ban by viewModel.myBan.collectAsStateWithLifecycle()
    val kickedUntil = rememberLive(group.id, myUid) {
        viewModel.groups.observeKick(group.id, myUid.orEmpty())
    }.let { live ->
        // Staff may always read, so only ordinary members are held back.
        if (isStaff) 0L else live.data ?: 0L
    }

    // Re-check once the penalty has run out
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            now = System.currentTimeMillis()
        }
    }

    if (kickedUntil > now) {
        Text(
            "Moderator ne aap ko is group ki chat se ${formatDateTime(kickedUntil)} tak kick kiya hai.",
            modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.error
        )
        return
    }

    val messages = rememberLive(group.id, kickedUntil) { viewModel.groups.observeMessages(group.id) }
    var input by remember { mutableStateOf("") }
    val banned = ban?.let { it.until > now } == true

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = true
        ) {
            liveStatus(messages, "Abhi koi message nahi")
            items(messages.data.orEmpty(), key = { it.id }) { message ->
                val mine = message.senderId == myUid
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (mine) ChatSentBubble else MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            if (!mine) {
                                Text(
                                    message.senderName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        onOpenProfile(message.senderId, message.senderName)
                                    }
                                )
                            }
                            Text(message.text, color = MaterialTheme.colorScheme.onSurface)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    formatTime(message.sentAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (canDelete && !mine) {
                                    IconButton(
                                        onClick = {
                                            viewModel.runAction("Message delete ho gaya") {
                                                viewModel.groups.deleteMessage(group.id, message.id)
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        when {
            memberName == null -> Text(
                "Message bhejne ke liye pehle group join karein.",
                modifier = Modifier.padding(16.dp).navigationBarsPadding()
            )
            banned -> Text(
                "Aap sab groups ki chat se ${formatDateTime(ban?.until ?: 0L)} tak ban hain.",
                modifier = Modifier.padding(16.dp).navigationBarsPadding(),
                color = MaterialTheme.colorScheme.error
            )
            else -> Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Message...") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f).testTag("group_msg_input")
                )
                FilledIconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            val text = input
                            viewModel.runAction { me ->
                                viewModel.sendGroupMessage(me, memberName, group.id, text)
                            }
                            input = ""
                        }
                    },
                    modifier = Modifier.size(48.dp).testTag("group_send_button")
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
            }
        }
    }

    // Join button for staff who are only visiting
    if (memberName == null && group.isPublic) {
        Box(modifier = Modifier.fillMaxSize()) {
            TextButton(
                onClick = {
                    viewModel.runAction("Aap group mein shamil ho gaye") { me ->
                        viewModel.groups.join(group.id, me)
                    }
                },
                modifier = Modifier.align(Alignment.TopEnd)
            ) { Text("Join") }
        }
    }
}

@Composable
private fun MembersDialog(
    group: CloudGroup,
    myUid: String,
    manages: Boolean,
    amMember: Boolean,
    onSelect: (String, String) -> Unit,
    onLeft: () -> Unit,
    onDismiss: () -> Unit
) {
    val viewModel = LocalAppViewModel.current
    val members = rememberLive(group.id) { viewModel.groups.observeMembers(group.id) }
    val requests = if (manages && !group.isPublic) {
        rememberLive(group.id) { viewModel.groups.observeRequests(group.id) }.data.orEmpty()
    } else {
        emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Members (${group.memberCount})") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (requests.isNotEmpty()) {
                    item { Text("Join requests", style = MaterialTheme.typography.titleSmall) }
                    items(requests, key = { "request_${it.uid}" }) { person ->
                        PersonRow(person.name, "", onClick = { onSelect(person.uid, person.name) }) {
                            TextButton(onClick = {
                                viewModel.runAction("Request reject") {
                                    viewModel.groups.reject(group.id, person.uid)
                                }
                            }) { Text("No") }
                            Button(onClick = {
                                viewModel.runAction("${person.name} shamil ho gaya") {
                                    viewModel.groups.approve(group.id, person.uid)
                                }
                            }) { Text("Yes") }
                        }
                    }
                    item { HorizontalDivider() }
                }
                liveStatus(members, "Koi member nahi")
                items(members.data.orEmpty(), key = { it.uid }) { person ->
                    PersonRow(
                        name = person.name,
                        subtitle = when {
                            person.uid == group.ownerUid -> "Group banane wala"
                            person.uid == myUid -> "Aap"
                            else -> ""
                        },
                        onClick = { onSelect(person.uid, person.name) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (amMember && group.ownerUid != myUid) {
                TextButton(onClick = {
                    viewModel.runAction("Aap ne group chhor diya") { me ->
                        viewModel.groups.removeMember(group.id, me)
                    }
                    onLeft()
                }) { Text("Group chhorein") }
            }
        }
    )
}
