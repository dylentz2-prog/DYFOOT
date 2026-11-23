package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.UserPreferencesDataStore
import com.example.data.model.UiState
import com.example.data.remote.ExternalSportsService
import com.example.data.repository.DyfootRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.Dyfoot3DSplashScreen
import com.example.ui.components.DyfootFloatingBottomBar
import com.example.ui.components.DyfootTopBar
import com.example.ui.components.MainTab
import com.example.ui.components.OwnerSocialFooter
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.dyfootStrings
import com.example.ui.screens.AdminStudioScreen
import com.example.ui.screens.CommunityScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LiveArenaScreen
import com.example.ui.screens.LiveScoresScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SportHubScreen
import com.example.ui.screens.TipsScreen
import com.example.ui.screens.VipMembershipScreen
import com.example.ui.screens.WatchFeedTab
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.DyfootTheme
import com.example.ui.theme.InfoCyan
import com.example.ui.theme.LiveRed
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardAlt
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.theme.SlateText
import com.example.ui.theme.VipAmber
import com.example.viewmodel.DyfootViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

object DyfootRoutes {
    const val HOME = "home"
    const val TIPS = "tips"
    const val WATCH = "watch"
    const val SPORT_HUB_PATTERN = "sport_hub?tab={initialTab}"
    fun sportHub(initialTab: Int = 0): String = "sport_hub?tab=$initialTab"
    const val GROUPS = "groups"
    const val PROFILE = "profile"
    const val VIP = "vip"
    const val LIVE_SCORES = "live_scores"
    const val LIVE_ARENA = "live_arena"
    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
    const val ADMIN_STUDIO = "admin_studio"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DyfootTheme {
                DyfootRoot()
            }
        }
    }
}

internal fun resolveFirebaseAuthOrNull(context: Context): FirebaseAuth? {
    val appContext = context.applicationContext ?: context
    val initialized = FirebaseApp.getApps(appContext).isNotEmpty() ||
        runCatching { FirebaseApp.initializeApp(appContext) != null }.getOrDefault(false)
    if (!initialized) return null
    return runCatching { Firebase.auth }.getOrNull()
}

@Composable
fun DyfootRoot(
    forceSetupScreen: Boolean = false
) {
    val context = LocalContext.current
    var retryTick by remember { mutableIntStateOf(0) }
    val auth = remember(retryTick, forceSetupScreen) {
        if (forceSetupScreen) null else resolveFirebaseAuthOrNull(context)
    }
    var currentUser by remember(auth) { mutableStateOf<FirebaseUser?>(auth?.currentUser) }
    var showSplash by rememberSaveable { mutableStateOf(true) }

    if (auth != null) {
        DisposableEffect(auth) {
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                currentUser = firebaseAuth.currentUser
            }
            auth.addAuthStateListener(listener)
            onDispose { auth.removeAuthStateListener(listener) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (auth == null) {
            FirebaseSetupRequiredScreen(
                onRetry = { retryTick++ }
            )
        } else {
            val user = currentUser
            if (user == null) {
                AuthScreen(
                    onAuthSuccess = {
                        currentUser = auth.currentUser
                    }
                )
            } else {
                key(user.uid) {
                    DyfootAuthenticatedApp(
                        uid = user.uid,
                        onSignedOut = {
                            currentUser = auth.currentUser
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut()
        ) {
            Dyfoot3DSplashScreen(onFinish = { showSplash = false })
        }
    }
}

@Composable
fun FirebaseSetupRequiredScreen(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MidnightElevated,
                        MidnightBlack,
                        MidnightBlack
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .testTag("firebase_setup_required"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_dyfoot_emblem),
                    contentDescription = stringResource(R.string.cd_dyfoot_logo),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "DYFOOT",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = CrispWhite
                )
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = NeonLimeContainer
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudOff,
                            contentDescription = "Firebase configuration required",
                            tint = NeonLime,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(30.dp)
                        )
                    }

                    Text(
                        text = "Unganisha Firebase Kwenye APK\nFirebase Setup Needed",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = CrispWhite,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "APK hii ilitengenezwa bila faili la app/google-services.json. Weka faili hilo kwenye GitHub ili kuwasha Google Sign-In, habari na mikeka ya DYFOOT.\n\nThis APK was built without app/google-services.json. Add it in GitHub and rebuild to connect Firebase & Google Sign-In.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateText,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MidnightCardAlt,
                        border = BorderStroke(1.dp, MidnightBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Hatua za Haraka / Quick Fix Steps:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = NeonLime
                            )
                            Text(
                                text = "1. Weka faili la app/google-services.json kwenye GitHub repo au weka Secret ya GOOGLE_SERVICES_JSON / GOOGLE_SERVICES_JSON_BASE64.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CrispWhite
                            )
                            Text(
                                text = "2. Hakikisha SHA-1 ya keystore yako imeongezwa kwenye Firebase Console (com.aistudio.dyfoot.vkmpqz).",
                                style = MaterialTheme.typography.bodySmall,
                                color = CrispWhite
                            )
                            Text(
                                text = "3. Endesha GitHub Actions build upya kisha install APK mpya.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CrispWhite
                            )
                        }
                    }

                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonLime,
                            contentColor = MidnightBlack
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("firebase_setup_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Jaribu Tena / Check Again",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DyfootAuthenticatedApp(
    uid: String,
    onSignedOut: () -> Unit
) {
    val appContext = LocalContext.current.applicationContext
    val viewModel: DyfootViewModel = viewModel(
        key = uid,
        factory = viewModelFactory {
            initializer {
                DyfootViewModel(
                    repository = DyfootRepository(appContext),
                    sportsService = ExternalSportsService(),
                    prefs = UserPreferencesDataStore(appContext),
                    currentUserId = uid,
                    appContext = appContext
                )
            }
        }
    )

    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadNotificationsCount.collectAsStateWithLifecycle()
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val copy = remember(language) { dyfootStrings(language) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showQuickMenuSheet by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val profile = (profileState as? UiState.Success)?.data
    if (profile?.suspended == true) {
        SuspendedAccountScreen(onSignedOut = onSignedOut)
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoutePattern = backStackEntry?.destination?.route

    val currentMainTabRoute = when (currentRoutePattern) {
        DyfootRoutes.HOME -> MainTab.HOME.route
        DyfootRoutes.TIPS -> MainTab.TIPS.route
        DyfootRoutes.WATCH -> MainTab.WATCH.route
        DyfootRoutes.SPORT_HUB_PATTERN -> MainTab.HUB.route
        DyfootRoutes.GROUPS -> MainTab.CHAT.route
        else -> null
    }

    val showMainChrome = currentMainTabRoute != null
    val isAdminOrOwner = viewModel.isAdminOrOwner()

    fun navigateToMainTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        Scaffold(
            containerColor = MidnightBlack,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (showMainChrome) {
                    DyfootTopBar(
                        unreadCount = unreadCount,
                        userPhotoUrl = profile?.photoUrl.orEmpty(),
                        userName = profile?.displayName ?: "DYFOOT Fan",
                        onNotificationsClick = { navController.navigate(DyfootRoutes.NOTIFICATIONS) },
                        onProfileClick = { navController.navigate(DyfootRoutes.PROFILE) },
                        onMenuClick = { showQuickMenuSheet = true }
                    )
                }
            }
        ) { innerPadding ->
            if (profileState is UiState.Loading && profile == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeonLime)
                }
            } else {
                NavHost(
                    navController = navController,
                    startDestination = DyfootRoutes.HOME,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    composable(DyfootRoutes.HOME) {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateTips = { navigateToMainTab(DyfootRoutes.TIPS) },
                            onNavigateVip = { navController.navigate(DyfootRoutes.VIP) },
                            onNavigateLiveScores = { navController.navigate(DyfootRoutes.LIVE_SCORES) },
                            onNavigateLiveArena = { navController.navigate(DyfootRoutes.LIVE_ARENA) },
                            onNavigateWatch = { navigateToMainTab(DyfootRoutes.WATCH) },
                            onNavigateSportHub = { tab ->
                                navController.navigate(DyfootRoutes.sportHub(tab))
                            }
                        )
                    }
                    composable(DyfootRoutes.TIPS) {
                        TipsScreen(
                            viewModel = viewModel,
                            onUpgradeVipClick = { navController.navigate(DyfootRoutes.VIP) },
                            onNavigateSportHub = { tab -> navController.navigate(DyfootRoutes.sportHub(tab)) },
                            onNavigateLiveScores = { navController.navigate(DyfootRoutes.LIVE_SCORES) },
                            onNavigateLiveArena = { navController.navigate(DyfootRoutes.LIVE_ARENA) }
                        )
                    }
                    composable(DyfootRoutes.WATCH) {
                        WatchFeedTab(viewModel = viewModel)
                    }
                    composable(
                        route = DyfootRoutes.SPORT_HUB_PATTERN,
                        arguments = listOf(
                            navArgument("initialTab") {
                                type = NavType.IntType
                                defaultValue = 0
                            }
                        )
                    ) { entry ->
                        val tab = entry.arguments?.getInt("initialTab") ?: 0
                        SportHubScreen(
                            viewModel = viewModel,
                            initialTab = tab,
                            onNavigateVip = { navController.navigate(DyfootRoutes.VIP) },
                            onNavigateTips = { navigateToMainTab(DyfootRoutes.TIPS) },
                            onNavigateLiveScores = { navController.navigate(DyfootRoutes.LIVE_SCORES) },
                            onNavigateLiveArena = { navController.navigate(DyfootRoutes.LIVE_ARENA) }
                        )
                    }
                    composable(DyfootRoutes.GROUPS) {
                        CommunityScreen(viewModel = viewModel)
                    }
                    composable(DyfootRoutes.PROFILE) {
                        ProfileScreen(
                            viewModel = viewModel,
                            onNavigateVip = { navController.navigate(DyfootRoutes.VIP) },
                            onNavigateAdminStudio = { navController.navigate(DyfootRoutes.ADMIN_STUDIO) },
                            onNavigateLiveScores = { navController.navigate(DyfootRoutes.LIVE_SCORES) },
                            onNavigateLiveArena = { navController.navigate(DyfootRoutes.LIVE_ARENA) },
                            onNavigateSportHub = { tab ->
                                when (tab) {
                                    1 -> navigateToMainTab(DyfootRoutes.WATCH)
                                    2 -> navController.navigate(DyfootRoutes.sportHub(1))
                                    else -> navController.navigate(DyfootRoutes.sportHub(0))
                                }
                            },
                            onSignedOut = onSignedOut
                        )
                    }
                    composable(DyfootRoutes.VIP) {
                        VipMembershipScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(DyfootRoutes.LIVE_SCORES) {
                        LiveScoresScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenLiveArena = { navController.navigate(DyfootRoutes.LIVE_ARENA) }
                        )
                    }
                    composable(DyfootRoutes.LIVE_ARENA) {
                        LiveArenaScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onUpgradeVipClick = { navController.navigate(DyfootRoutes.VIP) }
                        )
                    }
                    composable(DyfootRoutes.SEARCH) {
                        SearchScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateTips = { navigateToMainTab(DyfootRoutes.TIPS) },
                            onNavigateSportHub = { tab ->
                                when (tab) {
                                    1 -> navigateToMainTab(DyfootRoutes.WATCH)
                                    2 -> navController.navigate(DyfootRoutes.sportHub(1))
                                    else -> navController.navigate(DyfootRoutes.sportHub(0))
                                }
                            },
                            onNavigateLiveScores = { navController.navigate(DyfootRoutes.LIVE_SCORES) },
                            onNavigateGroups = { navigateToMainTab(DyfootRoutes.GROUPS) }
                        )
                    }
                    composable(DyfootRoutes.NOTIFICATIONS) {
                        NotificationsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(DyfootRoutes.ADMIN_STUDIO) {
                        AdminStudioScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }

        if (showMainChrome) {
            DyfootFloatingBottomBar(
                currentRoute = currentMainTabRoute,
                onSelectTab = { tab ->
                    when (tab) {
                        MainTab.HOME -> navigateToMainTab(DyfootRoutes.HOME)
                        MainTab.TIPS -> navigateToMainTab(DyfootRoutes.TIPS)
                        MainTab.WATCH -> navigateToMainTab(DyfootRoutes.WATCH)
                        MainTab.HUB -> navigateToMainTab(DyfootRoutes.sportHub(0))
                        MainTab.CHAT -> navigateToMainTab(DyfootRoutes.GROUPS)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                copy = copy
            )
        }
    }

    if (showQuickMenuSheet) {
        val isSw = language == AppLanguage.SWAHILI
        ModalBottomSheet(
            onDismissRequest = { showQuickMenuSheet = false },
            containerColor = MidnightElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isSw) "Menyu ya Haraka ya DYFOOT" else "DYFOOT Quick Navigation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = CrispWhite
                )
                Text(
                    text = profile?.let { "${it.displayName} (@${it.username}) • ${it.parsedRole().label}" } ?: "DYFOOT Member",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateText
                )
                HorizontalDivider(color = MidnightBorder)

                // Exclusive App Language Switcher (ONLY inside Menu)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MidnightCard),
                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_lang_switcher")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Translate,
                                contentDescription = if (isSw) "Lugha ya App" else "App Language",
                                tint = NeonLime,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSw) "Lugha ya App (Kiswahili / English)" else "App Language (English / Kiswahili)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = CrispWhite
                                )
                                Text(
                                    text = if (isSw) {
                                        "Badilisha lugha hapa kwenye Menyu — taarifa za Kiswahili na Kiingereza zitajipanga moja kwa moja."
                                    } else {
                                        "Switch app language from Menu — separates English updates and Kiswahili Tanzania/Africa updates."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateText
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val enActive = language == AppLanguage.ENGLISH
                            val swActive = language == AppLanguage.SWAHILI
                            Surface(
                                onClick = { viewModel.setAppLanguage(AppLanguage.ENGLISH) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (enActive) NeonLime else MidnightCardAlt,
                                border = BorderStroke(1.dp, if (enActive) NeonLime else MidnightBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("menu_lang_en")
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "English (EN)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (enActive) MidnightBlack else CrispWhite
                                    )
                                }
                            }
                            Surface(
                                onClick = { viewModel.setAppLanguage(AppLanguage.SWAHILI) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (swActive) NeonLime else MidnightCardAlt,
                                border = BorderStroke(1.dp, if (swActive) NeonLime else MidnightBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("menu_lang_sw")
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Kiswahili (SW)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (swActive) MidnightBlack else CrispWhite
                                    )
                                }
                            }
                        }
                    }
                }

                QuickMenuRow(
                    icon = Icons.Filled.SportsSoccer,
                    title = if (isSw) "Matokeo Mubashara & Msimamo" else "Full Live Scores & Tables",
                    subtitle = if (isSw) "Ratiba, magoli, kadi, kona na msimamo wa ligi zote" else "Live fixtures, crests, match events & league standings",
                    accent = NeonLime
                ) {
                    showQuickMenuSheet = false
                    navController.navigate(DyfootRoutes.LIVE_SCORES)
                }
                QuickMenuRow(
                    icon = Icons.Filled.WorkspacePremium,
                    title = if (isSw) "Vifurushi vya VIP (ROCK • STAR • PRO • LEGEND)" else "VIP Packages (ROCK • STAR • PRO • LEGEND)",
                    subtitle = if (isSw) "Kuanzia 1,000 TSH/siku kwa Simu (0706499813)" else "From 1,000 TSH/day via Mobile Money (0706499813)",
                    accent = VipAmber
                ) {
                    showQuickMenuSheet = false
                    navController.navigate(DyfootRoutes.VIP)
                }
                QuickMenuRow(
                    icon = Icons.Filled.Podcasts,
                    title = if (isSw) "Studio ya Podikasti" else "Podcasts Studio",
                    subtitle = if (isSw) "Vipindi vya sauti na uchambuzi wa soka" else "YouTube-style audio & commentary episodes",
                    accent = InfoCyan
                ) {
                    showQuickMenuSheet = false
                    navController.navigate(DyfootRoutes.sportHub(1))
                }
                QuickMenuRow(
                    icon = Icons.Filled.LiveTv,
                    title = if (isSw) "Uwanja Mubashara (Live Arena)" else "Live Arena",
                    subtitle = if (isSw) "Matangazo ya moja kwa moja na mijadala ya mechi" else "Scheduled match streams & live watch parties",
                    accent = LiveRed
                ) {
                    showQuickMenuSheet = false
                    navController.navigate(DyfootRoutes.LIVE_ARENA)
                }
                QuickMenuRow(
                    icon = Icons.Filled.Search,
                    title = if (isSw) "Utafutaji wa Kina" else "Global Search",
                    subtitle = if (isSw) "Tafuta mechi, mikeka, timu, habari na mashabiki" else "Search matches, tips, teams, articles & fans",
                    accent = CrispWhite
                ) {
                    showQuickMenuSheet = false
                    navController.navigate(DyfootRoutes.SEARCH)
                }
                QuickMenuRow(
                    icon = Icons.Filled.Person,
                    title = if (isSw) "Wasifu & Vipendwa Vyangu" else "My Profile & Bookmarks",
                    subtitle = if (isSw) "Simamia akaunti, mikeka uliyohifadhi na arifa" else "Manage account, saved tips & notification toggles",
                    accent = NeonLime
                ) {
                    showQuickMenuSheet = false
                    navController.navigate(DyfootRoutes.PROFILE)
                }
                if (isAdminOrOwner) {
                    QuickMenuRow(
                        icon = Icons.Filled.AdminPanelSettings,
                        title = if (isSw) "Studio ya Mmiliki (Admin)" else "Owner Admin Studio",
                        subtitle = if (isSw) "Tuma mikeka, thibitisha malipo, simamia habari na video" else "Publish tips, verify payments, manage users & content",
                        accent = NeonLime
                    ) {
                        showQuickMenuSheet = false
                        navController.navigate(DyfootRoutes.ADMIN_STUDIO)
                    }
                }

                // In-App Direct Updates & Pre-Play Store Build Status Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = NeonLimeContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_inapp_updates_card")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint = NeonLime,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSw) "DYFOOT Direct Build • v2.0 (Masasisho Ndani ya App)" else "DYFOOT Direct Build • v2.0 (In-App Updates)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = CrispWhite
                            )
                            Text(
                                text = if (isSw) {
                                    "Habari za TZ, Afrika na matokeo zinasomwa ndani ya app moja kwa moja kabla ya kuweka Play Store."
                                } else {
                                    "News, Swahili Tanzania/Africa feeds, scores & live content update directly in-app prior to Play Store release."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateText
                            )
                        }
                    }
                }

                OwnerSocialFooter(copy = copy)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun QuickMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MidnightCard,
        border = BorderStroke(1.dp, MidnightBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = CrispWhite, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SlateText)
            }
        }
    }
}

@Composable
private fun SuspendedAccountScreen(onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightCard),
            border = BorderStroke(1.dp, LiveRed),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Block, contentDescription = null, tint = LiveRed, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Account Suspended", style = MaterialTheme.typography.headlineSmall, color = CrispWhite)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This account has been suspended by a DYFOOT administrator. Sign out or contact platform support.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateText
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { signOut(credentialManager, scope, onSignedOut) },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveRed, contentColor = CrispWhite),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
