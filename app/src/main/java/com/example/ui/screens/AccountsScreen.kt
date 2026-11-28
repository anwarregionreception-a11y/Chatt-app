@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SavedAccount

fun accountSubtitle(account: SavedAccount): String = when {
    account.userId.isNotBlank() -> "ID: ${account.userId}"
    else -> account.email
}

/** Switch between any number of accounts saved on this phone. */
@Composable
fun AccountsScreen(onBack: () -> Unit) {
    val viewModel = LocalAppViewModel.current
    val myUid by viewModel.currentUserId.collectAsStateWithLifecycle()
    val saved by viewModel.savedAccounts.collectAsStateWithLifecycle()

    ListPage(title = "ID Switch", onBack = onBack) {
        item {
            Text(
                "Jis ID par jana ho us par tap karein. Passwords is phone par encrypt ho kar save hote hain.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(saved, key = { it.uid }) { account ->
            PersonRow(
                name = account.name,
                subtitle = accountSubtitle(account),
                onClick = { if (account.uid != myUid) viewModel.switchAccount(account.uid) }
            ) {
                if (account.uid == myUid) {
                    Text("Mojooda", color = MaterialTheme.colorScheme.primary)
                } else {
                    IconButton(onClick = { viewModel.forgetAccount(account.uid) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove")
                    }
                }
            }
        }
        item {
            Button(onClick = viewModel::addAnotherAccount, modifier = Modifier.fillMaxWidth()) {
                Text("Doosri ID add karein")
            }
        }
        if (saved.none { it.uid == myUid }) {
            item {
                Text(
                    "Mojooda ID abhi save nahi. Isay save karne ke liye ek dafa Logout kar ke dobara login karein.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
