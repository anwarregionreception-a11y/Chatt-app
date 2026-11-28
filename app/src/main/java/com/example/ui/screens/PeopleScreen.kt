@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.items
import com.example.data.local.FriendRelationEntity
import com.example.domain.AuthorityEngine
import com.example.domain.Countries
import com.example.domain.Country

private const val PAGE = 50L

/** Everyone who uses the app, with a filter and a count per country. */
@Composable
fun PeopleScreen(
    friendRelations: List<FriendRelationEntity>,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val viewModel = LocalAppViewModel.current
    val myUid by viewModel.currentUserId.collectAsStateWithLifecycle()
    var country by remember { mutableStateOf<Country?>(null) }
    var limit by remember { mutableLongStateOf(PAGE) }
    var pickCountry by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }
    val code = country?.code.orEmpty()

    val total = rememberLoad(Unit) { viewModel.account.countUsers("") }.data
    val inCountry = rememberLoad(code) { viewModel.account.countUsers(code) }.data
    val users = rememberLoad(code, limit) { viewModel.account.listUsers(code, limit) }

    ListPage(title = "App Users", onBack = onBack) {
        item {
            SectionCard {
                Text("App par total log: ${total ?: "..."}", style = MaterialTheme.typography.titleMedium)
                country?.let {
                    Text(
                        "${it.flag} ${it.name} ke log: ${inCountry ?: "..."}",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(onClick = { pickCountry = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(country?.let { "${it.flag} ${it.name}" } ?: "Country select karein")
                }
            }
        }
        liveStatus(users, "Koi user nahi mila")
        items(users.data.orEmpty(), key = { it.uid }) { card ->
            PersonRow(
                name = "${card.name} ${Countries.flag(card.country)}",
                subtitle = listOf(
                    card.userId.takeIf { it.isNotBlank() }?.let { "ID: $it" },
                    "Level ${AuthorityEngine.getLevel(card.authority)}",
                    if (card.uid == myUid) "Aap" else null
                ).filterNotNull().joinToString(" • "),
                onClick = {
                    viewModel.rememberPerson(card.uid, card.name)
                    selected = card.uid to card.name
                }
            )
        }
        if ((users.data?.size ?: 0) >= limit) {
            item {
                OutlinedButton(onClick = { limit += PAGE }, modifier = Modifier.fillMaxWidth()) {
                    Text("Mazeed dikhayen")
                }
            }
        }
    }

    if (pickCountry) {
        CountryPickerDialog(
            noneLabel = "Sab countries",
            onPick = {
                country = it
                limit = PAGE
                pickCountry = false
            },
            onDismiss = { pickCountry = false }
        )
    }

    selected?.let { (uid, name) ->
        UserProfileFor(uid, name, friendRelations, onDismiss = { selected = null }) {
            selected = null
            onOpenChat(uid)
        }
    }
}

/** Profile dialog with the right friend button for this person. */
@Composable
fun UserProfileFor(
    uid: String,
    name: String,
    friendRelations: List<FriendRelationEntity>,
    onDismiss: () -> Unit,
    group: GroupContext? = null,
    onChat: () -> Unit
) {
    val viewModel = LocalAppViewModel.current
    val relation = friendRelations.firstOrNull { it.userA == uid || it.userB == uid }
    ProfileDialog(
        uid = uid,
        fallbackName = name,
        onDismiss = onDismiss,
        isFriend = relation?.status == "ACCEPTED",
        onAddFriend = if (relation != null) null else {
            {
                viewModel.runAction("Friend request sent") { me ->
                    viewModel.requestFriend(me, uid, name)
                }
            }
        },
        // A pending request has neither button.
        onStartChat = if (relation?.status == "ACCEPTED") onChat else null,
        group = group
    )
}
