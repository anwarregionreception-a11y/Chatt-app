@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.UserEntity
import com.example.data.remote.PrivateSettings
import com.example.domain.AuthorityEngine
import com.example.domain.Countries
import com.example.ui.components.UserAvatar

/** The user's own profile: User ID, country, contact details and privacy. */
@Composable
fun ProfileScreen(currentUser: UserEntity, onBack: () -> Unit) {
    val viewModel = LocalAppViewModel.current
    val settings by viewModel.mySettings.collectAsStateWithLifecycle()
    val userId by viewModel.myUserId.collectAsStateWithLifecycle()
    var showPasswordDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.runAction("Photo save ho gayi") { me ->
                viewModel.repository.setProfilePhoto(me, uri.toString())
                Result.success(Unit)
            }
        }
    }

    ListPage(title = "Meri Profile", onBack = onBack) {
        val current = settings
        val currentId = userId
        if (current == null || currentId == null) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            item { Text("Profile load ho rahi hai. Internet check karein.") }
        } else {
            item {
                SectionCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UserAvatar(current.name, currentUser.profileImageUri, size = 60.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${current.name} ${Countries.flag(current.country)}",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "Level ${AuthorityEngine.getLevel(currentUser.authorityCoins)} • " +
                                    "Authority ${currentUser.authorityCoins}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    TextButton(onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) { Text("Photo badlein (sirf is phone par)") }
                }
            }
            item { UserIdSection(currentId) }
            item {
                SettingsSection(current) { changed ->
                    viewModel.runAction("Profile save ho gayi") { me ->
                        viewModel.account.saveSettings(me, changed)
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { showPasswordDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Password badlein") }
            }
        }
    }

    if (showPasswordDialog) {
        var old by remember { mutableStateOf("") }
        var new by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Password badlein") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PasswordField("Mojooda password", old) { old = it }
                    PasswordField("Naya password (8+, 1 Capital, 1 Number)", new) { new = it }
                    PasswordField("Naya password dobara", confirm) { confirm = it }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val a = old
                    val b = new
                    val c = confirm
                    viewModel.runAction("Password badal gaya") { viewModel.changePassword(a, b, c) }
                    showPasswordDialog = false
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true
    )
}

@Composable
private fun UserIdSection(userId: String) {
    val viewModel = LocalAppViewModel.current
    SectionCard {
        if (userId.isBlank()) {
            var chosen by remember { mutableStateOf("") }
            var free by remember { mutableStateOf(false) }
            var allowLogin by remember { mutableStateOf(false) }
            val loginEmail = viewModel.realLoginEmail()
            Text("Apni User ID banayen", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ek dafa banne ke baad User ID badli nahi ja sakti.",
                style = MaterialTheme.typography.bodySmall
            )
            UserIdField(value = chosen, onValueChange = { chosen = it }) { free = it }
            if (loginEmail.isNotBlank()) {
                SwitchRow("User ID se login", allowLogin) { allowLogin = it }
                IdLoginNote()
            }
            Button(
                onClick = {
                    val id = chosen
                    val email = if (allowLogin && loginEmail.isNotBlank()) loginEmail else null
                    viewModel.runAction("User ID ban gayi") { me ->
                        viewModel.account.claimUserId(me, id, email)
                    }
                },
                enabled = free,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Yeh User ID rakhein") }
        } else {
            Text("User ID: $userId", style = MaterialTheme.typography.titleMedium)
            val loginEmail = viewModel.realLoginEmail()
            if (loginEmail.isBlank()) {
                Text(
                    "Aap User ID aur password se login karte hain. Password bhoolne par reset nahi hota.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                val record = rememberLive(userId) { viewModel.account.observeUserIdRecord(userId) }.data
                SwitchRow("User ID se login", record?.loginEmail != null) { on ->
                    viewModel.runAction(if (on) "User ID login on" else "User ID login off") { me ->
                        viewModel.account.setLoginEmail(me, userId, if (on) loginEmail else null)
                    }
                }
                IdLoginNote()
            }
        }
    }
}

@Composable
private fun IdLoginNote() {
    Text(
        "On karne par kisi bhi phone se User ID aur password se login hoga. Magar jo banda aap ki " +
            "User ID jaanta ho woh technical tareeqe se aap ki login email maloom kar sakta hai.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SettingsSection(settings: PrivateSettings, onSave: (PrivateSettings) -> Unit) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var pickCountry by remember { mutableStateOf(false) }

    SectionCard {
        Text("Meri maloomat", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it.take(50)) },
            label = { Text("Naam") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = { pickCountry = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (draft.country.isBlank()) "Country chunein"
                else "${Countries.flag(draft.country)} ${Countries.nameOf(draft.country)}"
            )
        }
        OutlinedTextField(
            value = draft.mobile,
            onValueChange = { text ->
                draft = draft.copy(mobile = text.filter { it.isDigit() || it == '+' }.take(20))
            },
            label = { Text("Mobile number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SwitchRow("Mobile profile par dikhayen", draft.showMobile) { draft = draft.copy(showMobile = it) }
        OutlinedTextField(
            value = draft.contactEmail,
            onValueChange = { draft = draft.copy(contactEmail = it.take(100)) },
            label = { Text("Profile email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SwitchRow("Email profile par dikhayen", draft.showEmail) { draft = draft.copy(showEmail = it) }
        SwitchRow("Coins profile par dikhayen", draft.showCoins) { draft = draft.copy(showCoins = it) }
        SwitchRow("Friend requests aane dein", draft.allowRequests) {
            draft = draft.copy(allowRequests = it)
        }
        Button(
            onClick = { onSave(draft) },
            enabled = draft.name.isNotBlank() && draft != settings,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }
    }

    if (pickCountry) {
        CountryPickerDialog(
            noneLabel = "Koi nahi",
            onPick = {
                draft = draft.copy(country = it?.code.orEmpty())
                pickCountry = false
            },
            onDismiss = { pickCountry = false }
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
