package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AppRatingEntity
import com.example.data.local.UserEntity
import kotlinx.coroutines.delay

@Composable
fun RatingTab(
    ratings: List<AppRatingEntity>,
    onSubmitRating: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStars by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }
    val avgRating = if (ratings.isEmpty()) 0.0 else ratings.map { it.stars }.average()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Community Rating", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${ratings.size} review(s)", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(30.dp))
                        Text(String.format("%.1f", avgRating), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Rate Raabta", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (star in 1..5) {
                            IconButton(
                                onClick = { selectedStars = star },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (star <= selectedStars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = "$star stars",
                                    tint = if (star <= selectedStars) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Write your feedback") },
                        modifier = Modifier.fillMaxWidth().testTag("rating_comment_input")
                    )
                    Button(
                        onClick = {
                            onSubmitRating(selectedStars, comment)
                            comment = ""
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("submit_rating_button")
                    ) {
                        Text("Submit Rating")
                    }
                }
            }
        }

        items(ratings, key = { it.id }) { item ->
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.userName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row {
                            repeat(item.stars) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Text(item.comment, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun AdsTab(
    currentUser: UserEntity,
    onAdCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isWatchingAd by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(5) }

    LaunchedEffect(isWatchingAd) {
        if (isWatchingAd) {
            for (sec in 5 downTo 1) {
                countdown = sec
                delay(1000L)
            }
            isWatchingAd = false
            onAdCompleted()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_ad_reward),
                    contentDescription = "Watch Ads to Earn Coins",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC062E28))))
                )
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text("1 Ad = 1 Coin", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("1 Coin = 50 PKR Value • Watch short ads anytime", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.88f))
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Your Wallet Balance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${currentUser.walletCoins} Coins", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("${currentUser.adsWatchedCount} Ads Watched", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    if (isWatchingAd) {
                        LinearProgressIndicator(
                            progress = { (6 - countdown) / 5f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "Sponsored Video Playing... Rewarding +1 Coin in ${countdown}s",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Button(
                            onClick = { isWatchingAd = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("watch_ad_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Watch Ad & Earn +1 Coin")
                        }
                    }
                }
            }
        }
    }
}
