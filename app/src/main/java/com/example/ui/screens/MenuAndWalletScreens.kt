package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.domain.AuthorityEngine
import com.example.ui.components.AuthorityBadgesRow
import com.example.ui.components.LevelPill
import com.example.ui.components.RolePill
import com.example.ui.components.UserAvatar

@Composable
fun MenuTab(
    currentUser: UserEntity,
    onInvestAuthority: (Long) -> Unit,
    onOpenBuyCoins: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAuthorityGuide: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showBoostDialog by remember { mutableStateOf(false) }
    val level = AuthorityEngine.getLevel(currentUser.authorityCoins)
    val isStaff = currentUser.role == UserRoles.OWNER || currentUser.role == UserRoles.DEVELOPER

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        UserAvatar(currentUser.name, currentUser.profileImageUri, size = 60.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentUser.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "${currentUser.email} • ${currentUser.mobileNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                LevelPill(level)
                                RolePill(currentUser)
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Available Wallet Coins", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (currentUser.showCoinsOnProfile) "${currentUser.walletCoins} Coins" else "Hidden on Profile (${currentUser.walletCoins})",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        FilledTonalButton(
                            onClick = { showBoostDialog = true },
                            modifier = Modifier.testTag("boost_authority_button")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Boost Authority")
                        }
                    }

                    Text("Authority Badges (Level $level):", style = MaterialTheme.typography.labelLarge)
                    AuthorityBadgesRow(authorityCoins = currentUser.authorityCoins)
                }
            }
        }

        item {
            MenuActionCard(
                icon = Icons.Filled.AccountBalanceWallet,
                title = "Buy Coins (PKR & Crypto)",
                subtitle = "1 Coin = 50 PKR • JazzCash, EasyPaisa, IBFT, BTC, USDT, ETH",
                onClick = onOpenBuyCoins,
                testTag = "menu_buy_coins"
            )
        }

        item {
            MenuActionCard(
                icon = Icons.Filled.MilitaryTech,
                title = "16 Authority Levels & Badges",
                subtitle = "View normalized badge tiers, kick durations & cooldowns",
                onClick = onOpenAuthorityGuide,
                testTag = "menu_authority_guide"
            )
        }

        item {
            MenuActionCard(
                icon = Icons.Filled.Settings,
                title = "Settings & Privacy",
                subtitle = "Friend requests, Group invites, Profile Coins ON/OFF, Mobile & Email",
                onClick = onOpenSettings,
                testTag = "menu_settings"
            )
        }

        if (isStaff) {
            item {
                MenuActionCard(
                    icon = Icons.Filled.AdminPanelSettings,
                    title = if (currentUser.role == UserRoles.OWNER) "Owner & Admin Panel" else "Developer Admin Panel",
                    subtitle = "Search users, Reset Temp Passwords, Approve Coin Orders, Manage Roles",
                    onClick = onOpenAdminPanel,
                    highlighted = true,
                    testTag = "menu_admin_panel"
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("logout_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Logout")
            }
        }
    }

    if (showBoostDialog) {
        var amountInput by remember { mutableStateOf("50") }
        AlertDialog(
            onDismissRequest = { showBoostDialog = false },
            title = { Text("Increase Authority Level") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Spend Wallet Coins to upgrade your Authority Badges & Level. Available: ${currentUser.walletCoins} Coins.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Coins to Invest") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amountInput.toLongOrNull() ?: 0L
                    onInvestAuthority(amt)
                    showBoostDialog = false
                }) { Text("Upgrade Authority") }
            },
            dismissButton = {
                TextButton(onClick = { showBoostDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MenuActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BuyCoinsScreen(
    currentUser: UserEntity,
    orders: List<CoinOrderEntity>,
    onBack: () -> Unit,
    onSubmitOrder: (String, Long, String) -> Unit
) {
    val methods = listOf("JazzCash", "EasyPaisa", "IBFT", "BTC", "USDT", "Ethereum")
    var selectedMethod by remember { mutableStateOf("JazzCash") }
    var coinsText by remember { mutableStateOf("100") }
    var refText by remember { mutableStateOf("") }

    val coins = coinsText.toLongOrNull() ?: 0L
    val totalPkr = coins * AppConstants.COIN_PRICE_PKR
    val isCrypto = selectedMethod in listOf("BTC", "USDT", "Ethereum")

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Buy Coins") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("1 Coin = 50 PKR", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Coins are credited after Owner approval. Crypto (BTC, USDT, Ethereum) requires minimum 20 coins (max unlimited).",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Text("Select Payment Method", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    methods.forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method) }
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = coinsText,
                    onValueChange = { coinsText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(if (isCrypto) "Coins (Min 20 for $selectedMethod)" else "Number of Coins") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("buy_coins_amount_input")
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = refText,
                    onValueChange = { refText = it },
                    label = { Text("Transaction ID / Wallet Reference") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Total Equivalent: $totalPkr PKR", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        onSubmitOrder(selectedMethod, coins, refText)
                        refText = ""
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("submit_coin_order_button")
                ) {
                    Text("Submit Order for Owner Approval")
                }
            }

            item {
                Text("Your Coin Orders", style = MaterialTheme.typography.titleMedium)
            }

            items(orders.filter { it.userId == currentUser.id }, key = { it.id }) { order ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${order.coinsRequested} Coins via ${order.paymentMethod}", style = MaterialTheme.typography.titleSmall)
                            Text("${order.amountPkr} PKR • Ref: ${order.referenceNote}", style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (order.status) {
                                "APPROVED" -> MaterialTheme.colorScheme.primaryContainer
                                "REJECTED" -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            }
                        ) {
                            Text(
                                order.status,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorityGuideScreen(
    currentUser: UserEntity,
    onBack: () -> Unit
) {
    val myLevel = AuthorityEngine.getLevel(currentUser.authorityCoins)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("16 Authority Levels") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Your Current Authority: Level $myLevel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        AuthorityBadgesRow(authorityCoins = currentUser.authorityCoins)
                    }
                }
            }

            items(AuthorityEngine.levelThresholds.reversed(), key = { it.level }) { item ->
                val kickMins = AuthorityEngine.kickDurationMinutes(item.level)
                val cooldownMins = AuthorityEngine.kickCooldownMinutes(item.level)
                val immunity = AuthorityEngine.unfairKickImmunityCount(item.level)
                val ownerBanH = AuthorityEngine.ownerChatBanHours(item.level)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.level == myLevel) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LevelPill(item.level)
                            Text(
                                text = "Cooldown: ${AuthorityEngine.formatDurationMinutes(cooldownMins)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AuthorityBadgesRow(authorityCoins = item.minCoins)
                        val rulesSummary = buildList {
                            if (item.level >= 2) add("Supervisor Eligible")
                            if (kickMins > 0) add("Kick: ${AuthorityEngine.formatDurationMinutes(kickMins)}")
                            if (immunity > 0) add("Unfair Immunity: $immunity")
                            if (item.level >= 14) add("Private Group Access")
                            if (ownerBanH > 0) add("Ban Owner Chat: ${ownerBanH}h")
                            if (item.level >= 16) add("50-Coin Blacklist Entry")
                        }
                        if (rulesSummary.isNotEmpty()) {
                            Text(
                                text = rulesSummary.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
