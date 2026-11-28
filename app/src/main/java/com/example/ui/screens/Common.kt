@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.Live
import com.example.data.remote.UserIdRecord
import com.example.domain.Countries
import com.example.domain.Country
import com.example.domain.UserIds
import com.example.ui.RaabtaViewModel
import com.example.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** Lets shared dialogs reach the online services without passing them through every screen. */
val LocalAppViewModel = staticCompositionLocalOf<RaabtaViewModel> { error("No view model") }

private val OkGreen = Color(0xFF3FBF5F)

/** Keeps listening to online data while the screen is visible. */
@Composable
fun <T> rememberLive(vararg keys: Any?, create: () -> Flow<Live<T>>): Live<T> {
    val flow = remember(*keys) { create() }
    val state by flow.collectAsStateWithLifecycle(initialValue = Live<T>())
    return state
}

/** Loads online data once, and again when a key changes. */
@Composable
fun <T> rememberLoad(vararg keys: Any?, load: suspend () -> Result<T>): Live<T> {
    var state by remember(*keys) { mutableStateOf(Live<T>()) }
    LaunchedEffect(*keys) {
        state = load().fold(
            onSuccess = { Live(it) },
            onFailure = { Live<T>(error = it.message) }
        )
    }
    return state
}

fun formatDateTime(time: Long): String =
    if (time <= 0L) "-" else SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(time))

fun formatDate(time: Long): String =
    if (time <= 0L) "-" else SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(time))

/** A page with a title, a back button and a scrolling list. */
@Composable
fun ListPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = actions
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/** Shows a progress bar, an error or an "empty" line for a list that is not ready. */
fun <T> LazyListScope.liveStatus(live: Live<List<T>>, emptyText: String) {
    val error = live.error
    val data = live.data
    when {
        error != null -> item { Text(error, color = MaterialTheme.colorScheme.error) }
        data == null -> item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        data.isEmpty() -> item { Text(emptyText) }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun PersonRow(
    name: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UserAvatar(name, null, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailing()
        }
    }
}

/**
 * Text field for choosing a User ID. Shows a green tick when the ID is free and
 * "User ID already taken" when someone else has it.
 */
@Composable
fun UserIdField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAvailability: (Boolean) -> Unit
) {
    val viewModel = LocalAppViewModel.current
    var checking by remember { mutableStateOf(false) }
    var free by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(value) {
        free = false
        checking = false
        onAvailability(false)
        message = if (value.isEmpty()) null else UserIds.problem(value)
        if (value.isNotEmpty() && message == null) {
            checking = true
            delay(600) // wait until typing pauses
            viewModel.account.findUserId(value)
                .onSuccess { record: UserIdRecord? ->
                    free = record == null
                    message = if (free) "User ID available hai" else "User ID already taken"
                    onAvailability(free)
                }
                .onFailure { message = it.message }
            checking = false
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.lowercase().filter { !it.isWhitespace() }.take(20)) },
        label = { Text("User ID (apni marzi ki)") },
        singleLine = true,
        isError = value.isNotEmpty() && !free && !checking,
        trailingIcon = {
            when {
                checking -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                free -> Icon(Icons.Filled.CheckCircle, contentDescription = "Available", tint = OkGreen)
                value.isNotEmpty() -> Icon(
                    Icons.Filled.Cancel,
                    contentDescription = "Not available",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        supportingText = message?.let { text ->
            { Text(text, color = if (free) OkGreen else MaterialTheme.colorScheme.error) }
        },
        modifier = modifier.fillMaxWidth().testTag("user_id_input")
    )
}

/** Searchable country list. [onPick] receives null for "All / none". */
@Composable
fun CountryPickerDialog(
    noneLabel: String,
    onPick: (Country?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = Countries.all.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Country chunein") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    item {
                        Text(
                            noneLabel,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(10.dp)
                        )
                    }
                    items(shown, key = { it.code }) { country ->
                        Text(
                            "${country.flag}  ${country.name}",
                            modifier = Modifier.fillMaxWidth().clickable { onPick(country) }.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
