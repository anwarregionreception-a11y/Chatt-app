@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.UserEntity
import com.example.domain.SpinReward
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One spin per account every 24 hours. */
@Composable
fun SpinScreen(currentUser: UserEntity, onBack: () -> Unit) {
    val viewModel = LocalAppViewModel.current
    val myUid by viewModel.currentUserId.collectAsStateWithLifecycle()
    val uid = myUid.orEmpty()
    val live = rememberLive(uid) { viewModel.community.observeSpin(uid) }
    val status = live.data?.value
    val scope = rememberCoroutineScope()

    var spinning by remember { mutableStateOf(false) }
    var highlighted by remember { mutableIntStateOf(-1) }
    var result by remember { mutableStateOf<String?>(null) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }
    val waitMs = status?.let { it.lastSpin + SpinReward.COOLDOWN_MS - now } ?: 0L
    val unfinished = status != null && !status.claimed
    val canSpin = live.data != null && (waitMs <= 0 || unfinished) && !spinning

    ListPage(title = "Spin Game", onBack = onBack) {
        item {
            Text(
                "Coins: ${currentUser.walletCoins}   Authority: ${currentUser.authorityCoins}",
                style = MaterialTheme.typography.titleMedium
            )
        }
        items(SpinReward.entries.size) { index ->
            val reward = SpinReward.entries[index]
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (index == highlighted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Text(
                    reward.label,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (index == highlighted) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        spinning = true
                        result = null
                        scope.launch {
                            val request = launch {
                                viewModel.community.spin(uid)
                                    .onSuccess { reward ->
                                        // The light runs round and stops on the reward
                                        val steps = SpinReward.entries.size * 4 + reward.ordinal
                                        for (step in 0..steps) {
                                            highlighted = step % SpinReward.entries.size
                                            delay(70L + step * 6L)
                                        }
                                        result = if (reward == SpinReward.TRY_LATER) reward.label
                                        else "Mubarak! Aap ko ${reward.label} mila"
                                    }
                                    .onFailure { result = it.message }
                            }
                            request.join()
                            spinning = false
                        }
                    },
                    enabled = canSpin,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text(if (spinning) "Spinning..." else "SPIN") }

                result?.let { Text(it, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center) }
                live.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (waitMs > 0 && !unfinished && !spinning) {
                    val seconds = waitMs / 1000
                    Text("Agla spin: %02d:%02d:%02d baad".format(seconds / 3600, seconds % 3600 / 60, seconds % 60))
                }
            }
        }
    }
}
