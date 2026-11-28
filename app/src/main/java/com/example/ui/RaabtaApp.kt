package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.UserEntity
import com.example.data.local.UserRoles
import com.example.data.remote.CloudPresenceService
import com.example.ui.screens.*
import kotlinx.coroutines.delay

object Routes {
    const val HOME = "home"
    const val DIRECT_CHAT = "chat/{peerId}"
    const val GROUP_CHAT = "group/{groupId}"
    const val ANNOUNCEMENTS = "announcements"
    const val TOP_UP = "top_up"
    const val PROFILE = "profile"
    const val PEOPLE = "people"
    const val SPIN = "spin"
    const val SUPPORT = "support"
    const val ACCOUNTS = "accounts"
    const val AUTHORITY_GUIDE = "authority_guide"
    const val ADMIN_PANEL = "admin_panel"
    const val FAQ = "faq"

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

    // Android 13 and newer ask before notifications can be shown
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(currentUser != null) {
        if (currentUser != null && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val savedAccounts by viewModel.savedAccounts.collectAsStateWithLifecycle()

    // Report "online" only while the app is visible
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(currentUser?.id, lifecycleOwner) {
        if (currentUser != null) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    viewModel.heartbeat()
                    delay(CloudPresenceService.HEARTBEAT_MS)
                }
            }
        }
    }

    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    CompositionLocalProvider(LocalAppViewModel provides viewModel) {
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
                        savedAccounts = savedAccounts,
                        onSelectAccount = viewModel::switchAccount,
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
    val myUserId by viewModel.myUserId.collectAsStateWithLifecycle()
    val allRatings by viewModel.allRatings.collectAsStateWithLifecycle()

    val friendSearch by viewModel.friendSearch.collectAsStateWithLifecycle()

    val ownerPanel by viewModel.ownerPanel.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.CHAT) }
    var rankedProfile by remember { mutableStateOf<Pair<String, String>?>(null) }
    var askedForUserId by rememberSaveable { mutableStateOf(false) }

    // Accounts made before User IDs existed are invited to choose one
    if (myUserId == "" && !askedForUserId) {
        AlertDialog(
            onDismissRequest = { askedForUserId = true },
            title = { Text("Apni User ID banayen") },
            text = { Text("User ID se log aap ko dhoond sakte hain aur aap us se login bhi kar sakte hain.") },
            confirmButton = {
                Button(onClick = {
                    askedForUserId = true
                    navController.navigate(Routes.PROFILE)
                }) { Text("Banayen") }
            },
            dismissButton = { TextButton(onClick = { askedForUserId = true }) { Text("Baad mein") } }
        )
    }

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
                                onClick = { navController.navigate(Routes.TOP_UP) }
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
                            if (user.role == UserRoles.OWNER) {
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
                            friendSearch = friendSearch,
                            onSearchUsers = viewModel::searchUsers,
                            onClearSearch = viewModel::clearFriendSearch,
                            onViewProfile = viewModel::refreshProfile,
                            onOpenDirectChat = { peerId -> navController.navigate(Routes.directChat(peerId)) },
                            onOpenSystemInbox = { navController.navigate(Routes.ANNOUNCEMENTS) },
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
                        HomeTab.GROUP -> OnlineGroupsTab(
                            onOpenGroup = { gid -> navController.navigate(Routes.groupChat(gid)) }
                        )
                        HomeTab.MENU -> MenuTab(
                            currentUser = user,
                            onInvestAuthority = { amt ->
                                viewModel.runAction("Authority upgraded!") { uid ->
                                    viewModel.account.investAuthority(uid, amt)
                                }
                            },
                            userId = myUserId.orEmpty(),
                            onOpenProfile = { navController.navigate(Routes.PROFILE) },
                            onOpenPeople = { navController.navigate(Routes.PEOPLE) },
                            onOpenSpin = { navController.navigate(Routes.SPIN) },
                            onOpenSupport = { navController.navigate(Routes.SUPPORT) },
                            onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                            onOpenBuyCoins = { navController.navigate(Routes.TOP_UP) },
                            onOpenAuthorityGuide = { navController.navigate(Routes.AUTHORITY_GUIDE) },
                            onOpenAdminPanel = { navController.navigate(Routes.ADMIN_PANEL) },
                            onOpenFaq = { navController.navigate(Routes.FAQ) },
                            onLogout = viewModel::logout
                        )
                        HomeTab.RATING -> LeaderboardTab(
                            onOpenGroup = { gid -> navController.navigate(Routes.groupChat(gid)) },
                            onOpenProfile = { uid, name ->
                                viewModel.rememberPerson(uid, name)
                                rankedProfile = uid to name
                            },
                            reviews = {
                                RatingTab(
                                    ratings = allRatings,
                                    onSubmitRating = { stars, comment ->
                                        viewModel.runAction("Thanks for rating Millionaire!") { uid ->
                                            viewModel.repository.submitAppRating(uid, stars, comment)
                                        }
                                    }
                                )
                            }
                        )
                        HomeTab.ADS -> AdsTab(
                            currentUser = user,
                            onAdCompleted = {
                                viewModel.runAction("+1 Coin earned from Ad!") { uid ->
                                    viewModel.account.claimAdReward(uid)
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
            OnlineGroupScreen(
                groupId = groupId,
                friendRelations = friendRelations,
                onBack = { navController.popBackStack() },
                onOpenChat = { peerId -> navController.navigate(Routes.directChat(peerId)) }
            )
        }

        composable(Routes.ANNOUNCEMENTS) {
            AnnouncementsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.TOP_UP) {
            TopUpScreen(currentUser = user, onBack = { navController.popBackStack() })
        }

        composable(Routes.PROFILE) {
            ProfileScreen(currentUser = user, onBack = { navController.popBackStack() })
        }

        composable(Routes.PEOPLE) {
            PeopleScreen(
                friendRelations = friendRelations,
                onBack = { navController.popBackStack() },
                onOpenChat = { peerId -> navController.navigate(Routes.directChat(peerId)) }
            )
        }

        composable(Routes.SPIN) {
            SpinScreen(currentUser = user, onBack = { navController.popBackStack() })
        }

        composable(Routes.SUPPORT) {
            SupportScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { peerId -> navController.navigate(Routes.directChat(peerId)) }
            )
        }

        composable(Routes.ACCOUNTS) {
            AccountsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.AUTHORITY_GUIDE) {
            AuthorityGuideScreen(
                currentUser = user,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADMIN_PANEL) {
            if (user.role == UserRoles.OWNER) {
                OwnerPanelScreen(
                    currentUser = user,
                    state = ownerPanel,
                    onBack = { navController.popBackStack() },
                    onRefresh = { viewModel.loadOwnerPanel() },
                    onLoadMore = { viewModel.loadOwnerPanel(more = true) },
                    onSaveBalance = { coins, authority ->
                        viewModel.runAction("Save ho gaya") { uid ->
                            viewModel.setOwnerBalance(uid, coins, authority)
                        }
                    },
                    onShowDevice = viewModel::showDeviceAccounts,
                    onCloseDevice = viewModel::closeDeviceAccounts
                )
            }
        }

        composable(Routes.FAQ) {
            FaqScreen(onBack = { navController.popBackStack() })
        }
    }

    rankedProfile?.let { (uid, name) ->
        UserProfileFor(
            uid = uid,
            name = name,
            friendRelations = friendRelations,
            onDismiss = { rankedProfile = null },
            onChat = {
                rankedProfile = null
                navController.navigate(Routes.directChat(uid))
            }
        )
    }
}
