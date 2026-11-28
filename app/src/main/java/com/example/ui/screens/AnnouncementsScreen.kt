@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*

/** Messages the Owner sent to everyone. */
@Composable
fun AnnouncementsScreen(onBack: () -> Unit) {
    val viewModel = LocalAppViewModel.current
    val live = rememberLive(Unit) { viewModel.community.observeAnnouncements(50) }
    ListPage(title = "Owner ke Messages", onBack = onBack) {
        liveStatus(live, "Abhi koi message nahi")
        items(live.data.orEmpty(), key = { it.id }) { message ->
            SectionCard {
                Text(message.title, style = MaterialTheme.typography.titleMedium)
                Text(message.text)
                Text(formatDateTime(message.createdAt), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
