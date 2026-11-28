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
    userId: String,
    onOpenProfile: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenSpin: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenBuyCoins: () -> Unit,
    onOpenAuthorityGuide: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onOpenFaq: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showBoostDialog by remember { mutableStateOf(false) }
    val level = AuthorityEngine.getLevel(currentUser.authorityCoins)
    val isStaff = currentUser.role == UserRoles.OWNER

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
                                text = if (userId.isBlank()) "User ID: Meri Profile mein banayen" else "User ID: $userId",
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
                icon = Icons.Filled.Person,
                title = "Meri Profile",
                subtitle = "User ID, country, mobile, email aur privacy",
                onClick = onOpenProfile,
                testTag = "menu_profile"
            )
        }

        item {
            MenuActionCard(
                icon = Icons.Filled.People,
                title = "App Users",
                subtitle = "Sab log, country ke hisaab se",
                onClick = onOpenPeople,
                testTag = "menu_people"
            )
        }

        item {
            MenuActionCard(
                icon = Icons.Filled.Casino,
                title = "Spin Game",
                subtitle = "Har 24 ghante mein ek spin",
                onClick = onOpenSpin,
                testTag = "menu_spin"
            )
        }

        item {
            MenuActionCard(
                icon = Icons.Filled.SupportAgent,
                title = "Support",
                subtitle = "Moderator, Cop, Mentor: contact ya apply",
                onClick = onOpenSupport,
                testTag = "menu_support"
            )
        }

        item {
            MenuActionCard(
                icon = Icons.Filled.SwitchAccount,
                title = "ID Switch",
                subtitle = "Doosri ID add karein ya badlein",
                onClick = onOpenAccounts,
                testTag = "menu_accounts"
            )
        }

        item {
            MenuActionCard(
                icon = Icons.Filled.AccountBalanceWallet,
                title = "Top-up (Coins khareedein)",
                subtitle = "1 Coin = 50 PKR",
                onClick = onOpenBuyCoins,
                testTag = "menu_buy_coins"
            )
        }

        item {
            MenuActionCard(
                icon = Icons.Filled.MilitaryTech,
                title = "16 Authority Levels & Badges",
                subtitle = "Har level ke badges aur zaroori authority",
                onClick = onOpenAuthorityGuide,
                testTag = "menu_authority_guide"
            )
        }

        item {
            MenuActionCard(
                icon = Icons.Filled.Info,
                title = "FAQ",
                subtitle = "App ke bare mein sawal jawab",
                onClick = onOpenFaq,
                testTag = "menu_faq"
            )
        }

        if (isStaff) {
            item {
                MenuActionCard(
                    icon = Icons.Filled.AdminPanelSettings,
                    title = "Owner Admin Panel",
                    subtitle = "Users, reports, staff, top-up, messages",
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
                                text = "Authority ${item.minCoins}+",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AuthorityBadgesRow(authorityCoins = item.minCoins)
                    }
                }
            }
        }
    }
}
