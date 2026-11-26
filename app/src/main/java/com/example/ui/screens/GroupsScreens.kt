package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.domain.AuthorityEngine
import com.example.ui.components.LevelPill
import com.example.ui.components.UserAvatar
import com.example.ui.theme.ChatSentBubble
import java.util.concurrent.TimeUnit

@Composable
fun GroupsTab(
    currentUser: UserEntity,
    groups: List<GroupEntity>,
    allMembers: List<GroupMemberEntity>,
    onOpenGroup: (String) -> Unit,
    onJoinGroup: (String, Boolean) -> Unit,
    onBuyGroup: (String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBuyDialog by remember { mutableStateOf(false) }
    val userLevel = AuthorityEngine.getLevel(currentUser.authorityCoins)
    val now = System.currentTimeMillis()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Community Groups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Buy a group for 200 Coins (50 seats, 30 days). Level 14+ can enter Private groups.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        LevelPill(userLevel)
                    }
                }
            }

            items(groups, key = { it.id }) { group ->
                val members = allMembers.filter { it.groupId == group.id && !it.isBlacklisted }
                val myMembership = allMembers.firstOrNull { it.groupId == group.id && it.userId == currentUser.id }
                val daysLeft = TimeUnit.MILLISECONDS.toDays((group.expiresAt - now).coerceAtLeast(0L))
                val isBlacklisted = myMembership?.isBlacklisted == true
                val hasActiveBypass = isBlacklisted && (myMembership?.paidBypassUntil ?: 0L) > now
                val isJoined = (myMembership != null && !isBlacklisted) || hasActiveBypass

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isJoined) onOpenGroup(group.id)
                            else onJoinGroup(group.id, false)
                        }
                        .testTag("group_card_${group.id}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (group.isPublic) Icons.Filled.Public else Icons.Filled.Lock,
                                    contentDescription = if (group.isPublic) "Public" else "Private",
                                    tint = if (group.isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (daysLeft <= 5L) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${daysLeft}d left",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = group.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Members: ${members.size}/${group.memberLimit}  •  Fund: ${group.currentFund} Coins",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            when {
                                isBlacklisted && !hasActiveBypass && userLevel >= 16 -> {
                                    Button(
                                        onClick = { onJoinGroup(group.id, true) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("50 Coins (1h Entry)", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                isBlacklisted && !hasActiveBypass -> {
                                    Text("Blacklisted", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                }
                                isJoined -> {
                                    FilledTonalButton(onClick = { onOpenGroup(group.id) }) {
                                        Text("Open Chat")
                                    }
                                }
                                else -> {
                                    Button(onClick = { onJoinGroup(group.id, false) }) {
                                        Text(if (group.isPublic) "Join" else "Enter (L14+)")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(76.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = { showBuyDialog = true },
            icon = { Icon(Icons.Filled.AddCircle, contentDescription = null) },
            text = { Text("Buy Group (200 Coins)") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("buy_group_fab")
        )
    }

    if (showBuyDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var isPublic by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showBuyDialog = false },
            title = { Text("Buy New Group (200 Coins)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "200 Coins me group milega: 50 Members limit aur 30 Days life. Available: ${currentUser.walletCoins} Coins.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_group_name_input")
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isPublic) "Public Group" else "Private Group")
                        Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBuyGroup(name, desc, isPublic)
                        showBuyDialog = false
                    },
                    modifier = Modifier.testTag("confirm_buy_group_button")
                ) { Text("Buy (200 Coins)") }
            },
            dismissButton = {
                TextButton(onClick = { showBuyDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupChatScreen(
    currentUser: UserEntity,
    group: GroupEntity?,
    members: List<GroupMemberEntity>,
    messages: List<GroupMessageEntity>,
    donations: List<GroupDonationEntity>,
    allUsers: List<UserEntity>,
    onBack: () -> Unit,
    onSendMessage: (String, String) -> Unit,
    onRenewGroup: () -> Unit,
    onAddFund: (Long) -> Unit,
    onKickMember: (String, String) -> Unit,
    onBanOwnerInGroup: () -> Unit,
    onFileComplaint: () -> Unit,
    onToggleSupervisor: (String, Boolean) -> Unit,
    onAddMember: (String) -> Unit,
    onRemoveMember: (String, Boolean) -> Unit,
    onUnblacklistMember: (String) -> Unit,
    onUpdateSettings: (Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onOpenDirectChat: (String) -> Unit,
    onSendFriendRequest: (String) -> Unit
) {
    if (group == null) return

    var input by remember { mutableStateOf("") }
    val selectedTags = remember { mutableStateListOf<String>() }
    var lastTappedUser by remember { mutableStateOf<UserEntity?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showFundDialog by remember { mutableStateOf(false) }
    var showModerationTarget by remember { mutableStateOf<UserEntity?>(null) }
    var showKickComplaintBanner by remember { mutableStateOf(true) }

    val now = System.currentTimeMillis()
    val myMember = members.firstOrNull { it.userId == currentUser.id }
    val isOwner = group.ownerId == currentUser.id
    val isSupervisor = myMember?.isSupervisor == true
    val userLevel = AuthorityEngine.getLevel(currentUser.authorityCoins)
    val daysLeft = TimeUnit.MILLISECONDS.toDays((group.expiresAt - now).coerceAtLeast(0L))
    val showRenewalPrompt = isOwner && daysLeft <= (AppConstants.GROUP_LIFE_DAYS - AppConstants.GROUP_RENEWAL_PROMPT_DAY)
    val isCurrentlyKicked = (myMember?.kickedUntil ?: 0L) > now

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column(modifier = Modifier.clickable { showInfoDialog = true }) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            text = "${members.count { !it.isBlacklisted }}/${group.memberLimit} members • Fund: ${group.currentFund} • Tap info",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (userLevel >= 14 && !isOwner && !(myMember?.isBlacklisted == true && (myMember.paidBypassUntil > now))) {
                        IconButton(onClick = onBanOwnerInGroup) {
                            Icon(Icons.Filled.Gavel, contentDescription = "Ban Owner in Group")
                        }
                    }
                    IconButton(onClick = { showFundDialog = true }) {
                        Icon(Icons.Filled.VolunteerActivism, contentDescription = "Group Fund")
                    }
                    IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.testTag("group_info_button")) {
                        Icon(Icons.Filled.Info, contentDescription = "Group Info")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    AnimatedVisibility(visible = selectedTags.isNotEmpty() || lastTappedUser != null) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            selectedTags.forEach { tag ->
                                InputChip(
                                    selected = true,
                                    onClick = { selectedTags.remove(tag) },
                                    label = { Text("@$tag", style = MaterialTheme.typography.labelSmall) },
                                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove tag", modifier = Modifier.size(14.dp)) }
                                )
                            }
                            lastTappedUser?.let { peer ->
                                if (peer.id != currentUser.id) {
                                    AssistChip(
                                        onClick = { onOpenDirectChat(peer.id) },
                                        label = { Text("Chat ${peer.name}", style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = { Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = {
                                Text(
                                    if (selectedTags.isNotEmpty()) "Reply to ${selectedTags.joinToString { "@$it" }}..."
                                    else "Message group (tap any name to tag)..."
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("group_msg_input")
                        )
                        FilledIconButton(
                            onClick = {
                                if (input.isNotBlank()) {
                                    val tags = selectedTags.joinToString(", ") { "@$it" }
                                    onSendMessage(input, tags)
                                    input = ""
                                    selectedTags.clear()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("group_send_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (showRenewalPrompt) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Group Life Update Required (${daysLeft}d left)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "25 din guzar chuke hain. Group ki life 30 days ke liye 200 Coins me update karein.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(onClick = onRenewGroup) {
                                Text("Update Group Life (200 Coins)")
                            }
                        }
                    }
                }
            }

            if (isCurrentlyKicked && showKickComplaintBanner) {
                item {
                    val minsLeft = (((myMember?.kickedUntil ?: 0L) - now) / 60_000L).coerceAtLeast(1L)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "You Were Kicked ($minsLeft min left)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Reason: ${myMember?.kickReason ?: "Abusive language"}. Click below to send last 10 lines of chat to Group Owner's (System) Inbox.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onFileComplaint()
                                        showKickComplaintBanner = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("File Complaint (10-Line Chat)")
                                }
                                TextButton(onClick = { showKickComplaintBanner = false }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderId == currentUser.id
                val senderUser = allUsers.firstOrNull { it.id == msg.senderId }
                val senderMember = members.firstOrNull { it.userId == msg.senderId }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isMine) ChatSentBubble else MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.widthIn(max = 310.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = msg.senderName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        if (msg.senderName !in selectedTags) {
                                            selectedTags.add(msg.senderName)
                                        }
                                        lastTappedUser = senderUser
                                    }
                                )
                                if (msg.senderId == group.ownerId) {
                                    Text("Owner", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                } else if (senderMember?.isSupervisor == true) {
                                    Text("Supervisor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                                if (senderUser != null && senderUser.id != currentUser.id) {
                                    IconButton(
                                        onClick = { showModerationTarget = senderUser },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            contentDescription = "Member actions",
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                            if (msg.taggedUserNames.isNotBlank()) {
                                Text(
                                    text = msg.taggedUserNames,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(text = msg.text, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = formatTime(msg.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
    }

    // Member Kick / Profile / Supervisor Sheet
    showModerationTarget?.let { target ->
        val targetMember = members.firstOrNull { it.userId == target.id }
        val targetLevel = AuthorityEngine.getLevel(target.authorityCoins)
        var kickReason by remember { mutableStateOf("Abusive language") }

        AlertDialog(
            onDismissRequest = { showModerationTarget = null },
            title = { Text(target.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LevelPill(targetLevel)
                        if (targetMember?.isSupervisor == true) {
                            Text("Supervisor", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    OutlinedTextField(
                        value = kickReason,
                        onValueChange = { kickReason = it },
                        label = { Text("Kick Reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            showModerationTarget = null
                            onOpenDirectChat(target.id)
                        }) { Text("1-on-1 Chat") }
                        OutlinedButton(onClick = { onSendFriendRequest(target.id) }) { Text("Add Friend") }
                    }
                    if (isOwner && target.id != group.ownerId) {
                        OutlinedButton(
                            onClick = {
                                onToggleSupervisor(target.id, !(targetMember?.isSupervisor == true))
                                showModerationTarget = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (targetMember?.isSupervisor == true) "Remove Supervisor" else "Appoint Supervisor (L2+)")
                        }
                    }
                    if ((isOwner || (isSupervisor && group.supervisorCanRemoveMembers)) && target.id != group.ownerId) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onRemoveMember(target.id, false)
                                    showModerationTarget = null
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Remove") }
                            if (isOwner || (isSupervisor && group.supervisorCanBlacklist)) {
                                OutlinedButton(
                                    onClick = {
                                        onRemoveMember(target.id, true)
                                        showModerationTarget = null
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Blacklist") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (target.id != group.ownerId) {
                    Button(
                        onClick = {
                            onKickMember(target.id, kickReason)
                            showModerationTarget = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        val durText = if (isOwner || isSupervisor) "24h"
                        else AuthorityEngine.formatDurationMinutes(AuthorityEngine.kickDurationMinutes(userLevel))
                        Text("Kick ($durText)")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showModerationTarget = null }) { Text("Close") }
            }
        )
    }

    // Group Fund & Donators Dialog
    if (showFundDialog) {
        var amountText by remember { mutableStateOf(if (group.renewalCount > 0) "50" else "100") }
        var donatorProfile by remember { mutableStateOf<UserEntity?>(null) }

        AlertDialog(
            onDismissRequest = { showFundDialog = false },
            title = { Text("Group Fund & Donators") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Current Fund: ${group.currentFund} Coins", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Total Fund Used Over Time: ${group.totalFundUsed} Coins", style = MaterialTheme.typography.bodySmall)
                            Text("Current Member Limit: ${group.memberLimit} seats", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val minAdd = if (group.renewalCount > 0) AppConstants.MIN_GROUP_FUND_RENEWAL_USE else AppConstants.MIN_GROUP_FUND_INITIAL
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Add Coins (Min $minAdd)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val amt = amountText.toLongOrNull() ?: 0L
                            onAddFund(amt)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Donate & Increase Member Limit") }

                    Text("Donators (${donations.size}):", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(donations, key = { it.id }) { don ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        donatorProfile = allUsers.firstOrNull { it.id == don.donorId }
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = don.donorName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("+${don.amount} Coins (+${don.seatsAdded} seats)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFundDialog = false }) { Text("Close") }
            }
        )

        donatorProfile?.let { dp ->
            UserProfileModal(
                user = dp,
                isFriend = false,
                onDismiss = { donatorProfile = null },
                onAddFriend = { onSendFriendRequest(dp.id) },
                onStartChat = {
                    donatorProfile = null
                    showFundDialog = false
                    onOpenDirectChat(dp.id)
                }
            )
        }
    }

    // Group Info & Supervisor Permissions Dialog
    if (showInfoDialog) {
        var pub by remember(group.isPublic) { mutableStateOf(group.isPublic) }
        var canAdd by remember(group.supervisorCanAddMembers) { mutableStateOf(group.supervisorCanAddMembers) }
        var canRem by remember(group.supervisorCanRemoveMembers) { mutableStateOf(group.supervisorCanRemoveMembers) }
        var canBlk by remember(group.supervisorCanBlacklist) { mutableStateOf(group.supervisorCanBlacklist) }
        var canPriv by remember(group.supervisorCanTogglePrivacy) { mutableStateOf(group.supervisorCanTogglePrivacy) }
        val blacklisted = members.filter { it.isBlacklisted }

        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Group Info & Settings") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text("Life Remaining: $daysLeft days", style = MaterialTheme.typography.bodyMedium)
                        Text("Current Fund: ${group.currentFund} Coins | Total Used: ${group.totalFundUsed} Coins", style = MaterialTheme.typography.bodySmall)
                        Text("Supervisors: ${members.count { it.isSupervisor }}/5", style = MaterialTheme.typography.bodySmall)
                    }

                    if (isOwner || (isSupervisor && group.supervisorCanTogglePrivacy)) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Public Group")
                                Switch(checked = pub, onCheckedChange = { pub = it })
                            }
                        }
                    }

                    if (isOwner) {
                        item {
                            Text("Supervisor Permissions:", style = MaterialTheme.typography.labelLarge)
                            SettingSwitchRow("Add Members", canAdd) { canAdd = it }
                            SettingSwitchRow("Remove Members", canRem) { canRem = it }
                            SettingSwitchRow("Blacklist Add/Remove", canBlk) { canBlk = it }
                            SettingSwitchRow("Public / Private Toggle", canPriv) { canPriv = it }
                        }
                    }

                    if ((isOwner || (isSupervisor && group.supervisorCanAddMembers))) {
                        val nonMembers = allUsers.filter { u -> members.none { it.userId == u.id } }
                        if (nonMembers.isNotEmpty()) {
                            item {
                                Text("Add Member:", style = MaterialTheme.typography.labelLarge)
                                nonMembers.forEach { candidate ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(candidate.name, style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = { onAddMember(candidate.id) }) { Text("Add") }
                                    }
                                }
                            }
                        }
                    }

                    if (blacklisted.isNotEmpty() && (isOwner || (isSupervisor && group.supervisorCanBlacklist))) {
                        item {
                            Text("Blacklisted Users:", style = MaterialTheme.typography.labelLarge)
                            blacklisted.forEach { blk ->
                                val u = allUsers.firstOrNull { it.id == blk.userId }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(u?.name ?: blk.userId, style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { onUnblacklistMember(blk.userId) }) { Text("Unblacklist") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isOwner || (isSupervisor && group.supervisorCanTogglePrivacy)) {
                    Button(onClick = {
                        onUpdateSettings(pub, canAdd, canRem, canBlk, canPriv)
                        showInfoDialog = false
                    }) { Text("Save") }
                } else {
                    TextButton(onClick = { showInfoDialog = false }) { Text("Close") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
