@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.StaffMember
import com.example.data.remote.StaffRole

/** Staff duties, contact and applications. Only the Owner appoints staff. */
@Composable
fun SupportScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val viewModel = LocalAppViewModel.current
    val myUid by viewModel.currentUserId.collectAsStateWithLifecycle()
    val myRole by viewModel.myRole.collectAsStateWithLifecycle()
    val settings by viewModel.mySettings.collectAsStateWithLifecycle()
    val userId by viewModel.myUserId.collectAsStateWithLifecycle()
    val staff = rememberLive(myUid) { viewModel.community.observeStaff() }
    var selected by remember { mutableStateOf<StaffRole?>(null) }
    var applying by remember { mutableStateOf<StaffRole?>(null) }

    val role = selected
    ListPage(
        title = role?.let { "${it.title}s" } ?: "Support",
        onBack = { if (selected == null) onBack() else selected = null }
    ) {
        myRole?.let { item { Text("Aap is app ke ${it.title} hain.", color = MaterialTheme.colorScheme.primary) } }
        if (role == null) {
            items(StaffRole.entries.toList(), key = { it.name }) { entry ->
                SectionCard {
                    Text(entry.title, style = MaterialTheme.typography.titleLarge)
                    Text(entry.duties, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { selected = entry }, modifier = Modifier.fillMaxWidth()) {
                        Text("Contact ${entry.title}s")
                    }
                    if (myRole == null) {
                        OutlinedButton(onClick = { applying = entry }, modifier = Modifier.fillMaxWidth()) {
                            Text("Apply to become a ${entry.title}")
                        }
                    }
                }
            }
        } else {
            item { Text(role.duties, style = MaterialTheme.typography.bodyMedium) }
            val available: List<StaffMember> =
                staff.data.orEmpty().filter { it.role == role && it.uid != myUid }
            if (staff.data == null) liveStatus(staff, "")
            else if (available.isEmpty()) item { Text("Abhi koi ${role.title} available nahi") }
            items(available, key = { it.uid }) { person ->
                PersonRow(person.name, role.title, onClick = {
                    viewModel.contactStaff(person) { onOpenChat(person.uid) }
                }) {
                    Button(onClick = { viewModel.contactStaff(person) { onOpenChat(person.uid) } }) {
                        Text("Contact")
                    }
                }
            }
        }
    }

    applying?.let { wanted ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { applying = null },
            title = { Text("Apply: ${wanted.title}") },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("Apne bare mein likhein") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    val text = note
                    viewModel.runAction("Application Owner ko bhej di gayi") { me ->
                        viewModel.community.apply(
                            me, settings?.name.orEmpty(), userId.orEmpty(), wanted, text
                        )
                    }
                    applying = null
                }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { applying = null }) { Text("Cancel") } }
        )
    }
}
