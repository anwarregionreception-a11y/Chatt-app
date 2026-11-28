@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppConstants
import com.example.data.remote.CloudAccountService
import com.example.data.remote.StaffRole
import com.example.data.remote.UserCard
import com.example.domain.AuthorityEngine
import com.example.domain.Countries
import com.example.ui.components.AuthorityBadgesRow
import com.example.ui.components.LevelPill
import com.example.ui.components.UserAvatar

/** Set when the profile is opened from inside a group, to offer group actions. */
data class GroupContext(val groupId: String, val canRemove: Boolean)

/**
 * The full profile of a user, loaded online. Depending on who is looking it also offers
 * Cop reports, Moderator actions and Owner appointments.
 */
@Composable
fun ProfileDialog(
    uid: String,
    fallbackName: String,
    onDismiss: () -> Unit,
    isFriend: Boolean,
    onAddFriend: (() -> Unit)? = null,
    onStartChat: (() -> Unit)? = null,
    group: GroupContext? = null
) {
    val viewModel = LocalAppViewModel.current
    val myUid by viewModel.currentUserId.collectAsStateWithLifecycle()
    val myRole by viewModel.myRole.collectAsStateWithLifecycle()
    val settings by viewModel.mySettings.collectAsStateWithLifecycle()

    val cardState = rememberLive(uid) { viewModel.account.observeCard(uid) }
    val activity = rememberLive(uid) { viewModel.account.observeActivity(uid) }.data
    val staff = rememberLive { viewModel.community.observeStaff() }.data.orEmpty()
    val card = cardState.data

    val isSelf = uid == myUid
    val amOwner = myUid == AppConstants.OWNER_UID
    val targetRole = staff.firstOrNull { it.uid == uid }?.role
    var showReport by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Shown at the very top, as requested
                if (activity != null && activity.groupName.isNotBlank() &&
                    System.currentTimeMillis() - activity.at < CloudAccountService.ACTIVITY_WINDOW_MS
                ) {
                    Text(
                        "● ${activity.groupName} group mein online hai",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val name = card?.name ?: fallbackName
                    UserAvatar(name, null, size = 48.dp)
                    Column {
                        Text(
                            "$name ${Countries.flag(card?.country.orEmpty())}",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (card != null) LevelPill(AuthorityEngine.getLevel(card.authority))
                            val badge = when {
                                uid == AppConstants.OWNER_UID -> "OWNER"
                                else -> targetRole?.title?.uppercase()
                            }
                            if (badge != null) {
                                Text(
                                    badge,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (card == null) {
                    if (cardState.error != null) Text(cardState.error)
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    ProfileDetails(card)
                }

                if (card != null && !isSelf) {
                    if (myRole == StaffRole.COP) {
                        OutlinedButton(onClick = { showReport = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Report to Owner (Cop)")
                        }
                    }
                    if (group != null && uid != AppConstants.OWNER_UID &&
                        (myRole == StaffRole.MODERATOR || amOwner)
                    ) {
                        ModeratorActions(uid, group.groupId)
                    }
                    if (group != null && group.canRemove) {
                        OutlinedButton(
                            onClick = {
                                viewModel.runAction("Member group se nikal diya") {
                                    viewModel.groups.removeMember(group.groupId, uid)
                                }
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Group se nikalein") }
                    }
                    if (amOwner) {
                        OwnerWalletTools(uid, card.name)
                        Text("Staff appoint karein:", style = MaterialTheme.typography.labelLarge)
                        StaffRole.entries.forEach { role ->
                            FilterChip(
                                selected = targetRole == role,
                                onClick = {
                                    viewModel.runAction("${card.name} ab ${role.title} hai") {
                                        viewModel.community.appoint(uid, card.name, role)
                                    }
                                },
                                label = { Text(role.title) }
                            )
                        }
                        if (targetRole != null) {
                            TextButton(onClick = {
                                viewModel.runAction("Staff role hata diya") {
                                    viewModel.community.dismiss(uid)
                                }
                            }) { Text("Role hatayein") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isSelf && onStartChat != null && (isFriend || onAddFriend == null)) {
                Button(onClick = onStartChat) { Text("Chat") }
            } else if (!isSelf && !isFriend && onAddFriend != null) {
                Button(onClick = onAddFriend) { Text("Add Friend") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    if (showReport && card != null) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text("${card.name} ki report") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(1000) },
                    label = { Text("Kya ghair qanooni kaam dekha?") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val text = reason
                    viewModel.runAction("Report Owner ko bhej di gayi") { me ->
                        viewModel.community.sendReport(me, settings?.name.orEmpty(), card, text)
                    }
                    showReport = false
                }) { Text("Send Report") }
            },
            dismissButton = { TextButton(onClick = { showReport = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProfileDetails(card: UserCard) {
    InfoLine("User ID", card.userId.ifBlank { "Abhi nahi banayi" })
    InfoLine(
        "Country",
        if (card.country.isBlank()) "-" else "${Countries.nameOf(card.country)} ${Countries.flag(card.country)}"
    )
    InfoLine("Mobile", card.mobile ?: "Chhupa hua")
    InfoLine("Email", card.email ?: "Chhupi hui")
    InfoLine("Registration", formatDate(card.createdAt))
    InfoLine("Authority", "${card.authority}  (Level ${AuthorityEngine.getLevel(card.authority)})")
    InfoLine("Coins", card.coins?.toString() ?: "Chhupe hue")
    AuthorityBadgesRow(authorityCoins = card.authority)
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModeratorActions(targetUid: String, groupId: String) {
    val viewModel = LocalAppViewModel.current
    var version by remember { mutableIntStateOf(0) }
    // A ban from all groups is only offered after an earlier kick from this group.
    val kickedBefore = rememberLoad(targetUid, groupId, version) {
        viewModel.groups.wasKicked(groupId, targetUid)
    }.data == true

    OutlinedButton(
        onClick = {
            viewModel.runAction("Is group se 2 ghante ke liye kick kar diya") { me ->
                viewModel.groups.kick(groupId, targetUid, me).onSuccess { version++ }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Kick 2 ghante (yeh group)") }

    if (kickedBefore) {
        Button(
            onClick = {
                viewModel.runAction("Sab groups ki chat se 2 ghante ke liye ban") { me ->
                    viewModel.community.moderatorBan(targetUid, me, groupId)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Dobara ghalti: sab groups se 2 ghante ban") }
    }
}

/** Owner only: give coins or authority to this user, or take some away. */
@Composable
private fun OwnerWalletTools(uid: String, name: String) {
    val viewModel = LocalAppViewModel.current
    var coins by remember { mutableStateOf("") }
    var authority by remember { mutableStateOf("") }

    fun apply(sign: Long) {
        val coinChange = (coins.toLongOrNull() ?: 0L) * sign
        val authorityChange = (authority.toLongOrNull() ?: 0L) * sign
        if (coinChange == 0L && authorityChange == 0L) return
        viewModel.runAction("$name ka wallet badal diya. Us ki app dobara kholni hogi.") {
            viewModel.community.adjustWallet(uid, coinChange, authorityChange)
        }
        coins = ""
        authority = ""
    }

    Text("Coins / Authority dein ya kam karein:", style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(
        value = coins,
        onValueChange = { text -> coins = text.filter { it.isDigit() }.take(12) },
        label = { Text("Coins") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = authority,
        onValueChange = { text -> authority = text.filter { it.isDigit() }.take(12) },
        label = { Text("Authority coins") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { apply(1L) }, modifier = Modifier.weight(1f)) { Text("Dein") }
        OutlinedButton(onClick = { apply(-1L) }, modifier = Modifier.weight(1f)) { Text("Kam karein") }
    }
}
