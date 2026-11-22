package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.UiState
import com.example.data.model.UserRole
import com.example.ui.auth.signOut
import com.example.ui.components.OwnerSocialFooter
import com.example.ui.components.SectionHeader
import com.example.ui.i18n.dyfootStrings
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.InfoCyan
import com.example.ui.theme.LiveRed
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardAlt
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.theme.SlateText
import com.example.ui.theme.VipAmber
import com.example.ui.theme.VipAmberContainer
import com.example.viewmodel.DyfootViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ProfileScreen(
    viewModel: DyfootViewModel,
    onNavigateVip: () -> Unit,
    onNavigateAdminStudio: () -> Unit,
    onNavigateLiveScores: () -> Unit,
    onNavigateLiveArena: () -> Unit,
    onNavigateSportHub: (Int) -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val privateInfo by viewModel.privateInfo.collectAsStateWithLifecycle()
    val membership by viewModel.membership.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val copy = remember(language) { dyfootStrings(language) }

    val profile = (profileState as? UiState.Success)?.data
    val isAdminOrOwner = viewModel.isAdminOrOwner()
    val isOwner = viewModel.isVerifiedOwner()
    val hasVip = viewModel.hasActiveVipAccess()
    val firebaseEmail = Firebase.auth.currentUser?.email.orEmpty()
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var searchable by remember { mutableStateOf(true) }
    var notifyTips by remember { mutableStateOf(true) }
    var notifyNews by remember { mutableStateOf(true) }
    var notifyCommunity by remember { mutableStateOf(true) }
    var notifyPayments by remember { mutableStateOf(true) }

    LaunchedEffect(profile, privateInfo) {
        profile?.let {
            username = it.username
            displayName = it.displayName
            bio = it.bio
            photoUrl = it.photoUrl
            searchable = it.searchable
        }
        privateInfo?.let {
            phone = it.phone
            notifyTips = it.notifyTips
            notifyNews = it.notifyNews
            notifyCommunity = it.notifyCommunity
            notifyPayments = it.notifyPayments
        }
    }

    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            photoUrl = uri.toString().take(1000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. User Header Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = BorderStroke(1.dp, if (isAdminOrOwner) NeonLime.copy(alpha = 0.55f) else MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MidnightCardAlt)
                                .border(2.dp, if (hasVip) VipAmber else NeonLime, CircleShape)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (photoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = stringResource(R.string.cd_user_avatar),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = stringResource(R.string.cd_user_avatar),
                                    tint = NeonLime,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile?.displayName?.ifBlank { "DYFOOT Member" } ?: "Loading...",
                                style = MaterialTheme.typography.titleLarge,
                                color = CrispWhite
                            )
                            Text(
                                text = "@${profile?.username ?: "member"} • ${privateInfo?.email?.ifBlank { firebaseEmail } ?: firebaseEmail}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val role = profile?.parsedRole() ?: UserRole.FREE_MEMBER
                                Surface(
                                    color = when (role) {
                                        UserRole.OWNER, UserRole.ADMIN -> NeonLimeContainer
                                        UserRole.VIP_MEMBER -> VipAmberContainer
                                        UserRole.FREE_MEMBER -> MidnightCardAlt
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = role.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (role) {
                                            UserRole.OWNER, UserRole.ADMIN -> NeonLime
                                            UserRole.VIP_MEMBER -> VipAmber
                                            UserRole.FREE_MEMBER -> SlateText
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                if (hasVip) {
                                    val expText = membership?.expiresAt?.toDate()?.let { dateFmt.format(it) } ?: "Active"
                                    Surface(color = VipAmberContainer, shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            text = "VIP • $expText",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VipAmber,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = profile?.bio?.ifBlank { "Football supporter on DYFOOT." } ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change Profile Photo (Android Photo Picker)", color = NeonLime)
                    }
                }
            }
        }

        // 2. Owner / Admin Studio Entry (strictly visible to OWNER & ADMIN)
        if (isAdminOrOwner) {
            item {
                Card(
                    onClick = onNavigateAdminStudio,
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = NeonLimeContainer.copy(alpha = 0.7f)),
                    border = BorderStroke(1.5.dp, NeonLime),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_admin_studio_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = "Admin Studio",
                            tint = NeonLime,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isOwner) "DYFOOT Owner Studio" else "DYFOOT Admin Studio",
                                style = MaterialTheme.typography.titleMedium,
                                color = CrispWhite
                            )
                            Text(
                                text = "Publish tips, news, videos, podcasts, fixtures, ads, verify VIP payments & moderate users.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateText
                            )
                        }
                    }
                }
            }
        }

        // 3. Quick Navigation Shortcuts
        item {
            SectionHeader(title = "Platform Shortcuts")
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ProfileShortcutCard(
                        icon = Icons.Filled.WorkspacePremium,
                        label = "VIP & Payments",
                        tint = VipAmber,
                        onClick = onNavigateVip,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileShortcutCard(
                        icon = Icons.Filled.SportsSoccer,
                        label = "Live Scores",
                        tint = NeonLime,
                        onClick = onNavigateLiveScores,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ProfileShortcutCard(
                        icon = Icons.Filled.LiveTv,
                        label = "Live Arena",
                        tint = LiveRed,
                        onClick = onNavigateLiveArena,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileShortcutCard(
                        icon = Icons.Filled.PlayCircle,
                        label = "Watch Feed",
                        tint = InfoCyan,
                        onClick = { onNavigateSportHub(1) },
                        modifier = Modifier.weight(1f)
                    )
                    ProfileShortcutCard(
                        icon = Icons.Filled.Podcasts,
                        label = "Podcasts",
                        tint = VipAmber,
                        onClick = { onNavigateSportHub(2) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Edit Profile & Notification Settings
        item {
            SectionHeader(title = "Account & Notification Settings")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_username_input")
                    )
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("Bio") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Tanzania Mobile Money Phone (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PreferenceSwitchRow("Discoverable in Community Search", searchable) { searchable = it }
                    PreferenceSwitchRow("Betting Tips Alerts", notifyTips) { notifyTips = it }
                    PreferenceSwitchRow("Football News Alerts", notifyNews) { notifyNews = it }
                    PreferenceSwitchRow("Community Chat Alerts", notifyCommunity) { notifyCommunity = it }
                    PreferenceSwitchRow("Payment & VIP Alerts", notifyPayments) { notifyPayments = it }

                    if (Build.VERSION.SDK_INT >= 33 && !notificationPermissionGranted) {
                        OutlinedButton(
                            onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = NeonLime)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Allow Device Notifications (Android 13+)", color = CrispWhite)
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.updateProfile(
                                username = username,
                                displayName = displayName,
                                bio = bio,
                                photoUrl = photoUrl,
                                phone = phone,
                                searchable = searchable,
                                notifyTips = notifyTips,
                                notifyNews = notifyNews,
                                notifyCommunity = notifyCommunity,
                                notifyPayments = notifyPayments
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_profile_btn")
                    ) {
                        Text("Save Profile & Preferences", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Bookmarks
        item {
            SectionHeader(title = "Saved Bookmarks (${bookmarks.size})")
            Spacer(modifier = Modifier.height(8.dp))
            if (bookmarks.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MidnightCard,
                    border = BorderStroke(1.dp, MidnightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No bookmarks yet. Tap the bookmark icon on Tips, News, or Watch videos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bookmarks.take(8).forEach { bm ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MidnightCard,
                            border = BorderStroke(1.dp, MidnightBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = NeonLime)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${bm.contentType} • ${bm.title}", style = MaterialTheme.typography.titleSmall, color = CrispWhite)
                                    if (bm.subtitle.isNotBlank()) {
                                        Text(bm.subtitle, style = MaterialTheme.typography.bodySmall, color = SlateText)
                                    }
                                }
                                TextButton(onClick = {
                                    viewModel.toggleBookmark(bm.contentType, bm.contentId, bm.title, bm.subtitle)
                                }) {
                                    Text("Remove", color = LiveRed)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Blocked Users
        if (blockedUsers.isNotEmpty()) {
            item {
                SectionHeader(title = "Blocked Users (${blockedUsers.size})")
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    blockedUsers.forEach { blocked ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MidnightCard,
                            border = BorderStroke(1.dp, MidnightBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Block, contentDescription = null, tint = LiveRed)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(blocked.blockedName, style = MaterialTheme.typography.titleSmall, color = CrispWhite, modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.unblockUser(blocked.blockedUid) }) {
                                    Text("Unblock", color = NeonLime)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6b. Owner Official Socials
        item {
            OwnerSocialFooter(copy = copy)
        }

        // 7. Google Account Security & Sign Out
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = NeonLime)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Account Security & Recovery", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                    }
                    Text(
                        text = "Authentication & password recovery are managed securely via your verified Google Account ($firebaseEmail). DYFOOT never stores or requests Google passwords.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText
                    )
                    Button(
                        onClick = {
                            signOut(
                                credentialManager = credentialManager,
                                scope = scope,
                                onSignOutComplete = onSignedOut
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LiveRed, contentColor = CrispWhite),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("sign_out_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileShortcutCard(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCard),
        border = BorderStroke(1.dp, MidnightBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = CrispWhite, maxLines = 1)
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = CrispWhite, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
