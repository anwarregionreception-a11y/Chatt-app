package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.AppConstants
import com.example.data.local.CoinOrderEntity
import com.example.data.local.UserEntity
import com.example.data.local.UserRoles
import com.example.domain.AuthorityEngine
import com.example.ui.components.AuthorityBadgesRow
import com.example.ui.components.LevelPill
import com.example.ui.components.RolePill
import com.example.ui.components.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUser: UserEntity,
    onBack: () -> Unit,
    onSaveSettings: (String, String, Boolean, Boolean, Boolean, String?) -> Unit,
    onChangePassword: (String, String, String) -> Unit
) {
    var mobile by remember(currentUser.mobileNumber) { mutableStateOf(currentUser.mobileNumber) }
    var email by remember(currentUser.email) { mutableStateOf(currentUser.email) }
    var showCoins by remember(currentUser.showCoinsOnProfile) { mutableStateOf(currentUser.showCoinsOnProfile) }
    var receiveFriendReq by remember(currentUser.receiveFriendRequests) { mutableStateOf(currentUser.receiveFriendRequests) }
    var receiveGroupInv by remember(currentUser.receiveGroupInvites) { mutableStateOf(currentUser.receiveGroupInvites) }
    var photoUri by remember(currentUser.profileImageUri) { mutableStateOf(currentUser.profileImageUri) }
    var showChangePassDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUri = uri.toString()
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
                title = { Text("Settings & Privacy") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    UserAvatar(currentUser.name, photoUri, size = 64.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Profile Picture", style = MaterialTheme.typography.titleMedium)
                        Text("Pick from gallery using Android Photo Picker", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Text("Change")
                    }
                }
            }

            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Account Contact", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Change Mobile Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Change Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Privacy & Invitations", style = MaterialTheme.typography.titleMedium)
                    ToggleSettingRow("Receive Friend Requests", receiveFriendReq) { receiveFriendReq = it }
                    ToggleSettingRow("Receive Group Invitations", receiveGroupInv) { receiveGroupInv = it }
                    ToggleSettingRow("Show Available Coins on Profile", showCoins) { showCoins = it }
                }
            }

            Button(
                onClick = {
                    onSaveSettings(mobile, email, showCoins, receiveFriendReq, receiveGroupInv, photoUri)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_settings_button")
            ) {
                Text("Save Settings")
            }

            OutlinedButton(
                onClick = { showChangePassDialog = true },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Change Account Password")
            }
        }
    }

    if (showChangePassDialog) {
        var curr by remember { mutableStateOf("") }
        var newP by remember { mutableStateOf("") }
        var conf by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePassDialog = false },
            title = { Text("Change Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = curr,
                        onValueChange = { curr = it },
                        label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newP,
                        onValueChange = { newP = it },
                        label = { Text("New Password (8+ chars, 1 Cap, 1 Num)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = conf,
                        onValueChange = { conf = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onChangePassword(curr, newP, conf)
                    showChangePassDialog = false
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { showChangePassDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ToggleSettingRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    currentUser: UserEntity,
    allUsers: List<UserEntity>,
    coinOrders: List<CoinOrderEntity>,
    onBack: () -> Unit,
    onAdminResetPassword: (String, String?, (String) -> Unit) -> Unit,
    onSetBlocked: (String, Boolean) -> Unit,
    onDeleteUser: (String) -> Unit,
    onEditUser: (String, String, Long) -> Unit,
    onAssignDeveloper: (String, Boolean, Int, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onReviewCoinOrder: (String, Boolean) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var inspectedUserId by remember { mutableStateOf<String?>(null) }
    var generatedAdminTempPass by remember { mutableStateOf<Pair<String, String>?>(null) }

    val isOwner = currentUser.role == UserRoles.OWNER
    val filteredUsers = remember(allUsers, search) {
        allUsers.filter {
            search.isBlank() ||
                it.name.contains(search, ignoreCase = true) ||
                it.email.contains(search, ignoreCase = true) ||
                it.mobileNumber.contains(search, ignoreCase = true) ||
                it.id.contains(search, ignoreCase = true)
        }
    }
    val inspectedUser = allUsers.firstOrNull { it.id == inspectedUserId }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(if (isOwner) "Owner & Admin Panel" else "Developer L${currentUser.developerLevel} Panel")
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Users (${allUsers.size})") })
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Coin Orders (${coinOrders.count { it.status == "PENDING" }})") }
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            label = { Text("Search User by Name, Email, Mobile or ID") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("admin_user_search")
                        )
                    }

                    items(filteredUsers, key = { it.id }) { user ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { inspectedUserId = user.id }
                                .testTag("admin_user_${user.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                UserAvatar(user.name, user.profileImageUri, size = 44.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(user.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        RolePill(user)
                                        LevelPill(AuthorityEngine.getLevel(user.authorityCoins))
                                    }
                                    Text("${user.email} • ${user.mobileNumber}", style = MaterialTheme.typography.bodySmall)
                                    Text("ID: ${user.id} • Coins: ${user.walletCoins}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (user.isBlocked) {
                                    Text("BLOCKED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(coinOrders, key = { it.id }) { order ->
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(order.userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(order.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Text("${order.coinsRequested} Coins (${order.amountPkr} PKR) via ${order.paymentMethod}", style = MaterialTheme.typography.bodyMedium)
                                Text("Reference: ${order.referenceNote}", style = MaterialTheme.typography.bodySmall)
                                if (order.status == "PENDING" && (isOwner || currentUser.devCanApproveCoins)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onReviewCoinOrder(order.id, false) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Reject") }
                                        Button(
                                            onClick = { onReviewCoinOrder(order.id, true) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Approve") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Admin User Detail Modal
    inspectedUser?.let { u ->
        var showResetChoiceDialog by remember { mutableStateOf(false) }
        var showDevRoleDialog by remember { mutableStateOf(false) }
        var showEditDialog by remember { mutableStateOf(false) }
        val level = AuthorityEngine.getLevel(u.authorityCoins)

        AlertDialog(
            onDismissRequest = { inspectedUserId = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UserAvatar(u.name, u.profileImageUri, size = 44.dp)
                    Column {
                        Text(u.name, style = MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RolePill(u)
                            LevelPill(level)
                        }
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("User ID: ${u.id}", style = MaterialTheme.typography.bodySmall)
                    Text("Email: ${u.email}", style = MaterialTheme.typography.bodySmall)
                    Text("Mobile: ${u.mobileNumber}", style = MaterialTheme.typography.bodySmall)
                    Text("Total Coins: ${u.walletCoins}", style = MaterialTheme.typography.bodySmall)
                    Text("Authority Level: Level $level", style = MaterialTheme.typography.bodySmall)
                    AuthorityBadgesRow(authorityCoins = u.authorityCoins, compact = true)
                    Text("Password: ********", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    if (isOwner || currentUser.devCanResetPassword) {
                        Button(
                            onClick = { showResetChoiceDialog = true },
                            modifier = Modifier.fillMaxWidth().testTag("admin_reset_password_button")
                        ) {
                            Icon(Icons.Filled.LockReset, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Reset Password")
                        }
                        Text(
                            text = "User ko Temporary Password de kar us ka password reset karein. Aap user ka purana password nahi dekh sakte.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (u.id != AppConstants.OWNER_ID) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isOwner || currentUser.devCanBlockUsers) {
                                OutlinedButton(
                                    onClick = { onSetBlocked(u.id, !u.isBlocked) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (u.isBlocked) "Unblock" else "Block")
                                }
                            }
                            if (isOwner) {
                                OutlinedButton(
                                    onClick = { showEditDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Edit") }
                            }
                        }
                        if (isOwner) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { showDevRoleDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Developer Role") }
                                OutlinedButton(
                                    onClick = {
                                        onDeleteUser(u.id)
                                        inspectedUserId = null
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Delete") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedUserId = null }) { Text("Close") }
            }
        )

        if (showResetChoiceDialog) {
            var useCustom by remember { mutableStateOf(false) }
            var customTemp by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showResetChoiceDialog = false },
                title = { Text("Admin Reset Password (30m Expiry)") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { useCustom = false }) {
                            RadioButton(selected = !useCustom, onClick = { useCustom = false })
                            Text("Auto Generate Temporary Password")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { useCustom = true }) {
                            RadioButton(selected = useCustom, onClick = { useCustom = true })
                            Text("Admin Writes Temporary Password")
                        }
                        if (useCustom) {
                            OutlinedTextField(
                                value = customTemp,
                                onValueChange = { customTemp = it },
                                label = { Text("Custom Temporary Password") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onAdminResetPassword(u.id, if (useCustom) customTemp else null) { generated ->
                            generatedAdminTempPass = u.name to generated
                        }
                        showResetChoiceDialog = false
                    }) { Text("Confirm Reset") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetChoiceDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showDevRoleDialog) {
            var isDev by remember { mutableStateOf(u.role == UserRoles.DEVELOPER) }
            var devLvl by remember { mutableStateOf(u.developerLevel.coerceAtLeast(1).toString()) }
            var pReset by remember { mutableStateOf(u.devCanResetPassword) }
            var pBlock by remember { mutableStateOf(u.devCanBlockUsers) }
            var pCoins by remember { mutableStateOf(u.devCanApproveCoins) }
            var pGroup by remember { mutableStateOf(u.devCanModerateGroups) }

            AlertDialog(
                onDismissRequest = { showDevRoleDialog = false },
                title = { Text("Assign Developer Level") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToggleSettingRow("Is Developer", isDev) { isDev = it }
                        if (isDev) {
                            OutlinedTextField(
                                value = devLvl,
                                onValueChange = { devLvl = it.filter { c -> c.isDigit() } },
                                label = { Text("Developer Level (1 - 5+)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                            ToggleSettingRow("Can Reset Passwords", pReset) { pReset = it }
                            ToggleSettingRow("Can Block/Unblock Users", pBlock) { pBlock = it }
                            ToggleSettingRow("Can Approve Coin Orders", pCoins) { pCoins = it }
                            ToggleSettingRow("Can Moderate Groups", pGroup) { pGroup = it }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onAssignDeveloper(u.id, isDev, devLvl.toIntOrNull() ?: 1, pReset, pBlock, pCoins, pGroup)
                        showDevRoleDialog = false
                    }) { Text("Save Role") }
                },
                dismissButton = {
                    TextButton(onClick = { showDevRoleDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showEditDialog) {
            var editName by remember { mutableStateOf(u.name) }
            var editCoins by remember { mutableStateOf(u.walletCoins.toString()) }
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Edit User") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Name") }, singleLine = true)
                        OutlinedTextField(
                            value = editCoins,
                            onValueChange = { editCoins = it.filter { c -> c.isDigit() } },
                            label = { Text("Wallet Coins") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onEditUser(u.id, editName, editCoins.toLongOrNull() ?: u.walletCoins)
                        showEditDialog = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
                }
            )
        }
    }

    generatedAdminTempPass?.let { (userName, tempCode) ->
        AlertDialog(
            onDismissRequest = { generatedAdminTempPass = null },
            icon = { Icon(Icons.Filled.Key, contentDescription = null) },
            title = { Text("Temporary Password Created") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Share this one-time Temporary Password with $userName via WhatsApp or Call (valid for 30 minutes):")
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tempCode,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { generatedAdminTempPass = null }) { Text("Done") }
            }
        )
    }
}
