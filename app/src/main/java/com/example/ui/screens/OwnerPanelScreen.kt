package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.UserEntity
import com.example.data.remote.CloudPresenceService
import com.example.data.remote.PresenceUser
import com.example.domain.AuthorityEngine
import com.example.ui.OwnerPanelState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerPanelScreen(
    currentUser: UserEntity,
    state: OwnerPanelState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSaveBalance: (Long, Long) -> Unit,
    onShowDevice: (String) -> Unit,
    onCloseDevice: () -> Unit
) {
    var coins by remember(currentUser.walletCoins) { mutableStateOf(currentUser.walletCoins.toString()) }
    var authority by remember(currentUser.authorityCoins) { mutableStateOf(currentUser.authorityCoins.toString()) }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { onRefresh() }

    val perDevice = remember(state.users) { state.users.groupingBy { it.deviceId }.eachCount() }
    val shown = state.users.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Owner Admin Panel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Meri Coins aur Authority", style = MaterialTheme.typography.titleMedium)
                        NumberField("Coins", coins) { coins = it }
                        NumberField("Authority coins", authority) { authority = it }
                        Text(
                            "Level ${AuthorityEngine.getLevel(authority.toLongOrNull() ?: 0L)}. " +
                                "Level 16 ke liye ${AuthorityEngine.levelThresholds.first().minCoins}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                val newCoins = coins.toLongOrNull()
                                val newAuthority = authority.toLongOrNull()
                                if (newCoins != null && newAuthority != null) {
                                    onSaveBalance(newCoins, newAuthority)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save") }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Registered Users: ${state.total}", style = MaterialTheme.typography.titleMedium)
                        Text("Online: ${state.online}", color = MaterialTheme.colorScheme.primary)
                        Text("Offline: ${(state.total - state.online).coerceAtLeast(0L)}")
                        Text(
                            "Sirf woh users ginay jate hain jinhon ne nayi app kholi hai.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Naam se list mein dhoondein") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (state.isLoading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            state.error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            items(shown, key = { it.uid }) { person ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowDevice(person.deviceId) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        PresenceDetails(person)
                        Text(
                            "Is device par IDs: ${perDevice[person.deviceId] ?: 1} (tap karein)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (state.hasMore) {
                item {
                    OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                        Text("Mazeed users dikhayen")
                    }
                }
            }
        }
    }

    state.deviceAccounts?.let { accounts ->
        AlertDialog(
            onDismissRequest = onCloseDevice,
            title = { Text("Is device par ${accounts.size} ID") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(accounts, key = { it.uid }) { Column { PresenceDetails(it) } }
                }
            },
            confirmButton = { TextButton(onClick = onCloseDevice) { Text("Close") } }
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() }.take(15)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PresenceDetails(person: PresenceUser) {
    val online = System.currentTimeMillis() - person.lastSeen < CloudPresenceService.ONLINE_WINDOW_MS
    Text(person.name, style = MaterialTheme.typography.titleSmall)
    Text(
        text = if (online) "● Online" else "Offline, aakhri dafa ${formatDateTime(person.lastSeen)}",
        style = MaterialTheme.typography.bodySmall,
        color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text("Registration: ${formatDateTime(person.registeredAt)}", style = MaterialTheme.typography.bodySmall)
    Text("ID: ${person.uid}", style = MaterialTheme.typography.labelSmall)
}

private fun formatDateTime(time: Long): String =
    if (time <= 0L) "-" else SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(time))
