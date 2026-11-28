@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.UserEntity
import com.example.domain.SpinReward
import com.rabbta.chatapp.v1.R
import kotlin.math.ceil
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One spin per account every 24 hours. The wheel turns and stops on the reward. */
@Composable
fun SpinScreen(currentUser: UserEntity, onBack: () -> Unit) {
    val viewModel = LocalAppViewModel.current
    val myUid by viewModel.currentUserId.collectAsStateWithLifecycle()
    val uid = myUid.orEmpty()
    val live = rememberLive(uid) { viewModel.community.observeSpin(uid) }
    val status = live.data?.value
    val scope = rememberCoroutineScope()

    var spinning by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
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
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Image(
                    painter = painterResource(id = R.drawable.img_spin_wheel),
                    contentDescription = "Spin wheel",
                    modifier = Modifier
                        .padding(top = 28.dp)
                        .size(300.dp)
                        .rotate(rotation.value)
                )
                // Fixed pointer at the top
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp).align(Alignment.TopCenter)
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
                            viewModel.community.spin(uid)
                                .onSuccess { slot ->
                                    val reward = SpinReward.WHEEL[slot]
                                    // Slice `slot` sits slot x 60 degrees clockwise from the top,
                                    // so the wheel must end turned back by that much.
                                    val wobble = Random.nextInt(-20, 21)
                                    val target = ceil(rotation.value / 360f) * 360f +
                                        5 * 360f - slot * 60f + wobble
                                    rotation.animateTo(
                                        target,
                                        tween(durationMillis = 4500, easing = FastOutSlowInEasing)
                                    )
                                    result = if (reward == SpinReward.TRY_LATER) reward.label
                                    else "Mubarak! Aap ko ${reward.label} mila"
                                }
                                .onFailure { result = it.message }
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
