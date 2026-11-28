package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.domain.AuthorityEngine
import com.example.ui.components.AuthorityBadgesRow
import com.example.ui.components.LevelPill
import com.example.ui.components.RolePill
import com.example.ui.components.UserAvatar
import com.example.ui.theme.ChatSentBubble
import com.example.ui.FriendSearchState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatsTab(
    currentUser: UserEntity,
    allUsers: List<UserEntity>,
    friendRelations: List<FriendRelationEntity>,
    directMessages: List<DirectMessageEntity>,
    friendSearch: FriendSearchState,
    onSearchUsers: (String) -> Unit,
    onClearSearch: () -> Unit,
    onViewProfile: (String) -> Unit,
    onOpenDirectChat: (String) -> Unit,
    onOpenSystemInbox: () -> Unit,
    onAcceptFriend: (String) -> Unit,
    onDeclineFriend: (String) -> Unit,
    onSendFriendRequest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var search by remember { mutableStateOf("") }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var selectedProfileUser by remember { mutableStateOf<UserEntity?>(null) }

    val acceptedFriendIds = remember(friendRelations, currentUser.id) {
        friendRelations.filter { it.status == "ACCEPTED" }.map {
            if (it.userA == currentUser.id) it.userB else it.userA
        }.toSet()
    }
    val incomingRequests = remember(friendRelations, currentUser.id) {
        friendRelations.filter { it.status == "PENDING" && it.requestedBy != currentUser.id }
    }
    val friends = remember(allUsers, acceptedFriendIds, search) {
        allUsers.filter { it.id in acceptedFriendIds && it.id != currentUser.id }
            .filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search friends chat...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Messages from the Owner, pinned
            item {
                PersonRow(
                    name = "(System) Official",
                    subtitle = "Owner ke messages aur notifications",
                    onClick = onOpenSystemInbox
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Incoming Friend Requests
            if (incomingRequests.isNotEmpty()) {
                item {
                    Text(
                        text = "Friend Requests (${incomingRequests.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(incomingRequests, key = { it.id }) { req ->
                    val sender = allUsers.firstOrNull { it.id == req.requestedBy }
                    if (sender != null) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                UserAvatar(sender.name, sender.profileImageUri, size = 40.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sender.name, style = MaterialTheme.typography.titleSmall)
                                    LevelPill(AuthorityEngine.getLevel(sender.authorityCoins))
                                }
                                OutlinedButton(onClick = { onDeclineFriend(req.id) }) { Text("Decline") }
                                Button(onClick = { onAcceptFriend(req.id) }) { Text("Accept") }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Friends (${friends.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (friends.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.PersonAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text("No friend chats yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap the + button to find people by User ID, name or email.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(friends, key = { it.id }) { friend ->
                    val latestMsg = directMessages.firstOrNull {
                        (it.senderId == currentUser.id && it.receiverId == friend.id) ||
                            (it.senderId == friend.id && it.receiverId == currentUser.id)
                    }
                    val level = AuthorityEngine.getLevel(friend.authorityCoins)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDirectChat(friend.id) }
                            .testTag("friend_chat_${friend.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            UserAvatar(
                                name = friend.name,
                                imageUri = friend.profileImageUri,
                                modifier = Modifier.clickable { selectedProfileUser = friend }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = friend.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    LevelPill(level)
                                    RolePill(friend)
                                }
                                Text(
                                    text = latestMsg?.text ?: "Tap to start chatting",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (latestMsg != null) {
                                Text(
                                    text = formatTime(latestMsg.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }

        FloatingActionButton(
            onClick = { showAddFriendDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_friend_fab"),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = "Add Friend")
        }
    }

    if (showAddFriendDialog) {
        var query by remember { mutableStateOf("") }
        val closeDialog = {
            showAddFriendDialog = false
            onClearSearch()
        }
        AlertDialog(
            onDismissRequest = closeDialog,
            title = { Text("Find Friends") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("User ID, poori email ya naam") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_search_input")
                    )
                    Button(
                        onClick = { onSearchUsers(query) },
                        enabled = !friendSearch.isSearching,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (friendSearch.isSearching) "Searching..." else "Search")
                    }
                    friendSearch.message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(friendSearch.results, key = { it.id }) { person ->
                            val relation = friendRelations.firstOrNull {
                                it.userA == person.id || it.userB == person.id
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        closeDialog()
                                        selectedProfileUser = person
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                UserAvatar(person.name, person.profileImageUri, size = 36.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(person.name, style = MaterialTheme.typography.titleSmall)
                                    if (person.email.isNotBlank()) {
                                        Text(person.email, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                when {
                                    relation == null -> Button(onClick = { onSendFriendRequest(person.id) }) {
                                        Text("Add")
                                    }
                                    relation.status == "ACCEPTED" -> TextButton(
                                        onClick = {
                                            closeDialog()
                                            onOpenDirectChat(person.id)
                                        }
                                    ) { Text("Chat") }
                                    relation.requestedBy == currentUser.id ->
                                        Text("Sent", style = MaterialTheme.typography.labelMedium)
                                    else -> Button(onClick = { onAcceptFriend(relation.id) }) {
                                        Text("Accept")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = closeDialog) { Text("Done") }
            }
        )
    }

    selectedProfileUser?.let { selected ->
        LaunchedEffect(selected.id) { onViewProfile(selected.id) }
        // Show the stored record so freshly loaded details appear in the open dialog
        val profile = allUsers.firstOrNull { it.id == selected.id } ?: selected
        UserProfileModal(
            user = profile,
            isFriend = profile.id in acceptedFriendIds,
            onDismiss = { selectedProfileUser = null },
            onAddFriend = { onSendFriendRequest(profile.id) },
            onStartChat = {
                selectedProfileUser = null
                onOpenDirectChat(profile.id)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectChatScreen(
    currentUser: UserEntity,
    peer: UserEntity?,
    messages: List<DirectMessageEntity>,
    isFriend: Boolean,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendFriendRequest: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var showProfile by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    if (peer != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.clickable { showProfile = true }
                        ) {
                            UserAvatar(peer.name, peer.profileImageUri, size = 38.dp)
                            Column {
                                Text(peer.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    text = "Level ${AuthorityEngine.getLevel(peer.authorityCoins)} • Tap for profile",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Message...") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dm_input")
                    )
                    FilledIconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                onSendMessage(input)
                                input = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("dm_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
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
            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderId == currentUser.id
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMine) 16.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 16.dp
                        ),
                        color = if (isMine) ChatSentBubble else MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
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

    if (showProfile && peer != null) {
        UserProfileModal(
            user = peer,
            isFriend = isFriend,
            onDismiss = { showProfile = false },
            onAddFriend = { onSendFriendRequest(peer.id) },
            onStartChat = { showProfile = false }
        )
    }
}

@Composable
fun UserProfileModal(
    user: UserEntity,
    isFriend: Boolean,
    onDismiss: () -> Unit,
    onAddFriend: () -> Unit,
    onStartChat: () -> Unit
) {
    ProfileDialog(
        uid = user.id,
        fallbackName = user.name,
        onDismiss = onDismiss,
        isFriend = isFriend,
        onAddFriend = onAddFriend,
        onStartChat = onStartChat
    )
}

fun formatTime(ts: Long): String {
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ts))
}
