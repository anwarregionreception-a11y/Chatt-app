package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.UserEntity
import com.example.data.local.UserRoles
import com.example.ui.screens.*

object Routes {
    const val HOME = "home"
    const val DIRECT_CHAT = "chat/{peerId}"
    const val GROUP_CHAT = "group/{groupId}"
    const val SYSTEM_INBOX = "system_inbox"
    const val BUY_COINS = "buy_coins"
    const val SETTINGS = "settings"
    const val AUTHORITY_GUIDE = "authority_guide"
    const val ADMIN_PANEL = "admin_panel"

    fun directChat(peerId: String) = "chat/$peerId"
    fun groupChat(groupId: String) = "group/$groupId"
}

enum class HomeTab(val label: String) {
    CHAT("Chat"),
    GROUP("Group"),
    MENU("Menu"),
    RATING("Rating"),
    ADS("Ads")
}

@Composable
fun RaabtaAppRoot(viewModel: RaabtaViewModel) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val toastMsg by viewModel.toastMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                currentUser == null -> {
                    LoginScreen(
                        authState = authState,
                        onLogin = viewModel::login,
                        onRegister = viewModel::register,
                        onForgotPassword = viewModel::requestForgotPassword,
                        onClearError = viewModel::clearAuthError,
                        onClearInfo = viewModel::clearInfoMessage
                    )
                }
                else -> {
                    val user = currentUser!!
                    key(user.id) {
                        AuthenticatedNavHost(user = user, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedNavHost(
    user: UserEntity,
    viewModel: RaabtaViewModel
) {
    val navController = rememberNavController()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val friendRelations by viewModel.friendRelations.collectAsStateWithLifecycle()
    val directMessages by viewModel.userDirectMessages.collectAsStateWithLifecycle()
    val allGroups by viewModel.allGroups.collectAsStateWithLifecycle()
    val allGroupMembers by viewModel.allGroupMembers.collectAsStateWithLifecycle()
    val coinOrders by viewModel.allCoinOrders.collectAsStateWithLifecycle()
    val ownerComplaints by viewModel.ownerComplaints.collectAsStateWithLifecycle()
    val allRatings by viewModel.allRatings.collectAsStateWithLifecycle()

    val friendSearch by viewModel.friendSearch.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(HomeTab.CHAT) }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("Millionaire", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${user.name} • ${selectedTab.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.padding(end = 8.dp),
                                onClick = { navController.navigate(Routes.BUY_COINS) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.MonetizationOn,
                                        contentDescription = "Coins",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "${user.walletCoins}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            if (user.role == UserRoles.OWNER || user.role == UserRoles.DEVELOPER) {
                                IconButton(onClick = { navController.navigate(Routes.ADMIN_PANEL) }) {
                                    Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Admin Panel")
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        HomeTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            val icon = when (tab) {
                                HomeTab.CHAT -> if (selected) Icons.Filled.Chat else Icons.Outlined.Chat
                                HomeTab.GROUP -> if (selected) Icons.Filled.Groups else Icons.Outlined.Groups
                                HomeTab.MENU -> if (selected) Icons.Filled.Menu else Icons.Outlined.Menu
                                HomeTab.RATING -> if (selected) Icons.Filled.Star else Icons.Outlined.StarOutline
                                HomeTab.ADS -> if (selected) Icons.Filled.PlayCircle else Icons.Outlined.PlayCircleOutline
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = { Icon(icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (selectedTab) {
                        HomeTab.CHAT -> ChatsTab(
                            currentUser = user,
                            allUsers = allUsers,
                            friendRelations = friendRelations,
                            directMessages = directMessages,
                            complaints = ownerComplaints,
                            friendSearch = friendSearch,
                            onSearchUsers = viewModel::searchUsers,
                            onClearSearch = viewModel::clearFriendSearch,
                            onViewProfile = viewModel::refreshProfile,
                            onOpenDirectChat = { peerId -> navController.navigate(Routes.directChat(peerId)) },
                            onOpenSystemInbox = { navController.navigate(Routes.SYSTEM_INBOX) },
                            onAcceptFriend = { relId ->
                                viewModel.runAction("Friend request accepted") {
                                    viewModel.respondFriendRequest(relId, true)
                                }
                            },
                            onDeclineFriend = { relId ->
                                viewModel.runAction("Request declined") {
                                    viewModel.respondFriendRequest(relId, false)
                                }
                            },
                            onSendFriendRequest = { targetId ->
                                viewModel.runAction("Friend request sent") { uid ->
                                    viewModel.sendFriendRequest(uid, targetId)
                                }
                            }
                        )
                        HomeTab.GROUP -> GroupsTab(
                            currentUser = user,
                            groups = allGroups,
                            allMembers = allGroupMembers,
                            onOpenGroup = { gid -> navController.navigate(Routes.groupChat(gid)) },
                            onJoinGroup = { gid, bypass ->
                                viewModel.runAction(if (bypass) "1-Hour Glorious Entry Unlocked" else "Joined group") { uid ->
                                    val res = viewModel.repository.joinGroup(uid, gid, bypass)
                                    if (res.isSuccess) {
                                        navController.navigate(Routes.groupChat(gid))
                                    }
                                    res
                                }
                            },
                            onBuyGroup = { name, desc, isPub ->
                                viewModel.runAction("Group purchased! (200 Coins)") { uid ->
                                    viewModel.repository.buyGroup(uid, name, desc, isPub)
                                }
                            }
                        )
                        HomeTab.MENU -> MenuTab(
                            currentUser = user,
                            onInvestAuthority = { amt ->
                                viewModel.runAction("Authority upgraded!") { uid ->
                                    viewModel.repository.investCoinsIntoAuthority(uid, amt)
                                }
                            },
                            onOpenBuyCoins = { navController.navigate(Routes.BUY_COINS) },
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            onOpenAuthorityGuide = { navController.navigate(Routes.AUTHORITY_GUIDE) },
                            onOpenAdminPanel = { navController.navigate(Routes.ADMIN_PANEL) },
                            onLogout = viewModel::logout
                        )
                        HomeTab.RATING -> RatingTab(
                            ratings = allRatings,
                            onSubmitRating = { stars, comment ->
                                viewModel.runAction("Thanks for rating Millionaire!") { uid ->
                                    viewModel.repository.submitAppRating(uid, stars, comment)
                                }
                            }
                        )
                        HomeTab.ADS -> AdsTab(
                            currentUser = user,
                            onAdCompleted = {
                                viewModel.runAction("+1 Coin earned from Ad!") { uid ->
                                    viewModel.repository.watchRewardedAd(uid)
                                }
                            }
                        )
                    }
                }
            }
        }

        composable(
            route = Routes.DIRECT_CHAT,
            arguments = listOf(navArgument("peerId") { type = NavType.StringType })
        ) { entry ->
            val peerId = entry.arguments?.getString("peerId") ?: return@composable
            val peer = allUsers.firstOrNull { it.id == peerId }
            val messages by viewModel.observeConversation(peerId).collectAsStateWithLifecycle(emptyList())
            val isFriend = friendRelations.any {
                it.status == "ACCEPTED" && (it.userA == peerId || it.userB == peerId)
            }
            LaunchedEffect(peerId) { viewModel.refreshProfile(peerId) }
            DirectChatScreen(
                currentUser = user,
                peer = peer,
                messages = messages,
                isFriend = isFriend,
                onBack = { navController.popBackStack() },
                onSendMessage = { text ->
                    viewModel.runAction { uid -> viewModel.sendMessage(uid, peerId, text) }
                },
                onSendFriendRequest = { targetId ->
                    viewModel.runAction("Friend request sent") { uid ->
                        viewModel.sendFriendRequest(uid, targetId)
                    }
                }
            )
        }

        composable(
            route = Routes.GROUP_CHAT,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { entry ->
            val groupId = entry.arguments?.getString("groupId") ?: return@composable
            val group by viewModel.observeGroup(groupId).collectAsStateWithLifecycle(null)
            val members by viewModel.observeGroupMembers(groupId).collectAsStateWithLifecycle(emptyList())
            val messages by viewModel.observeGroupMessages(groupId).collectAsStateWithLifecycle(emptyList())
            val donations by viewModel.observeGroupDonations(groupId).collectAsStateWithLifecycle(emptyList())

            GroupChatScreen(
                currentUser = user,
                group = group,
                members = members,
                messages = messages,
                donations = donations,
                allUsers = allUsers,
                onBack = { navController.popBackStack() },
                onSendMessage = { text, tags ->
                    viewModel.runAction { uid -> viewModel.repository.sendGroupMessage(uid, groupId, text, tags) }
                },
                onRenewGroup = {
                    viewModel.runAction("Group life renewed for 30 days!") { uid ->
                        viewModel.repository.renewGroupLife(uid, groupId)
                    }
                },
                onAddFund = { amt ->
                    viewModel.runAction("Added $amt Coins to Group Fund!") { uid ->
                        viewModel.repository.addGroupFund(uid, groupId, amt)
                    }
                },
                onKickMember = { targetId, reason ->
                    viewModel.runAction("Member kicked") { uid ->
                        viewModel.repository.kickMemberInGroup(uid, groupId, targetId, reason)
                    }
                },
                onBanOwnerInGroup = {
                    viewModel.runAction("Group Owner chat-banned in this group!") { uid ->
                        viewModel.repository.banGroupOwnerInGroup(uid, groupId)
                    }
                },
                onFileComplaint = {
                    viewModel.runAction("10-line chat complaint sent to Owner's (System) Inbox") { uid ->
                        viewModel.repository.fileKickComplaint(uid, groupId)
                    }
                },
                onToggleSupervisor = { targetId, makeSup ->
                    viewModel.runAction(if (makeSup) "Supervisor appointed" else "Supervisor removed") { uid ->
                        viewModel.repository.toggleSupervisor(uid, groupId, targetId, makeSup)
                    }
                },
                onAddMember = { targetId ->
                    viewModel.runAction("Member added") { uid ->
                        viewModel.repository.inviteOrAddMember(uid, groupId, targetId)
                    }
                },
                onRemoveMember = { targetId, blacklist ->
                    viewModel.runAction(if (blacklist) "Member blacklisted" else "Member removed") { uid ->
                        viewModel.repository.removeOrBlacklistMember(uid, groupId, targetId, blacklist)
                    }
                },
                onUnblacklistMember = { targetId ->
                    viewModel.runAction("Removed from blacklist") { uid ->
                        viewModel.repository.unblacklistMember(uid, groupId, targetId)
                    }
                },
                onUpdateSettings = { isPub, add, rem, blk, priv ->
                    viewModel.runAction("Group settings updated") { uid ->
                        viewModel.repository.updateGroupSettings(uid, groupId, isPub, add, rem, blk, priv)
                    }
                },
                onOpenDirectChat = { peerId -> navController.navigate(Routes.directChat(peerId)) },
                onSendFriendRequest = { targetId ->
                    viewModel.runAction("Friend request sent") { uid ->
                        viewModel.sendFriendRequest(uid, targetId)
                    }
                }
            )
        }

        composable(Routes.SYSTEM_INBOX) {
            SystemInboxScreen(
                complaints = ownerComplaints,
                allUsers = allUsers,
                onBack = { navController.popBackStack() },
                onOpenGroup = { gid -> navController.navigate(Routes.groupChat(gid)) },
                onResolveComplaint = { cmpId, punish ->
                    viewModel.runAction { uid -> viewModel.repository.resolveComplaint(uid, cmpId, punish) }
                },
                onSendFriendRequest = { targetId ->
                    viewModel.runAction("Friend request sent") { uid ->
                        viewModel.sendFriendRequest(uid, targetId)
                    }
                },
                onOpenDirectChat = { peerId -> navController.navigate(Routes.directChat(peerId)) }
            )
        }

        composable(Routes.BUY_COINS) {
            BuyCoinsScreen(
                currentUser = user,
                orders = coinOrders,
                onBack = { navController.popBackStack() },
                onSubmitOrder = { method, coins, ref ->
                    viewModel.runAction("Order submitted! Waiting for Owner approval.") { uid ->
                        viewModel.repository.submitCoinOrder(uid, method, coins, ref)
                    }
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                currentUser = user,
                onBack = { navController.popBackStack() },
                onSaveSettings = { mobile, email, showCoins, reqFriends, reqGroups, uri ->
                    viewModel.runAction("Settings saved") { uid ->
                        viewModel.repository.updateSettings(uid, mobile, email, showCoins, reqFriends, reqGroups, uri)
                    }
                },
                onChangePassword = { curr, newP, conf ->
                    viewModel.runAction("Password changed successfully") {
                        viewModel.changePassword(curr, newP, conf)
                    }
                }
            )
        }

        composable(Routes.AUTHORITY_GUIDE) {
            AuthorityGuideScreen(
                currentUser = user,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADMIN_PANEL) {
            AdminPanelScreen(
                currentUser = user,
                allUsers = allUsers,
                coinOrders = coinOrders,
                onBack = { navController.popBackStack() },
                onAdminResetPassword = { targetId, customTemp, onSuccess ->
                    viewModel.runAction { uid ->
                        val res = viewModel.repository.adminResetPassword(uid, targetId, customTemp)
                        res.onSuccess { code -> onSuccess(code) }
                        res
                    }
                },
                onSetBlocked = { targetId, block ->
                    viewModel.runAction(if (block) "User blocked" else "User unblocked") { uid ->
                        viewModel.repository.setUserBlocked(uid, targetId, block)
                    }
                },
                onDeleteUser = { targetId ->
                    viewModel.runAction("User deleted") { uid ->
                        viewModel.repository.deleteUserByOwner(uid, targetId)
                    }
                },
                onEditUser = { targetId, newName, newCoins ->
                    viewModel.runAction("User updated") { uid ->
                        viewModel.repository.editUserDetailsByOwner(uid, targetId, newName, newCoins)
                    }
                },
                onAssignDeveloper = { targetId, isDev, lvl, pReset, pBlock, pCoins, pGroup ->
                    viewModel.runAction("Developer role updated") { uid ->
                        viewModel.repository.assignDeveloperRole(uid, targetId, isDev, lvl, pReset, pBlock, pCoins, pGroup)
                    }
                },
                onReviewCoinOrder = { orderId, approve ->
                    viewModel.runAction(if (approve) "Coin order approved & credited!" else "Coin order rejected") { uid ->
                        viewModel.repository.reviewCoinOrder(uid, orderId, approve)
                    }
                }
            )
        }
    }
}
