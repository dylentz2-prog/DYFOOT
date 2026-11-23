package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.media.BannerImageOptimizer
import com.example.data.media.BannerImageSelection
import com.example.data.model.AdPlacement
import com.example.data.model.Advertisement
import com.example.data.model.Announcement
import com.example.ui.components.AdBannerCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.InfoCyan
import com.example.ui.theme.LiveRed
import com.example.ui.theme.LiveRedContainer
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardAlt
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.theme.SlateText
import com.example.ui.theme.VipAmber
import com.example.ui.theme.WonEmerald
import com.example.ui.theme.WonEmeraldContainer
import com.example.viewmodel.BannerEditorUploadState
import com.example.viewmodel.DyfootViewModel
import kotlinx.coroutines.launch

@Composable
internal fun AdminAdsAndAlertsSection(viewModel: DyfootViewModel) {
    val context = LocalContext.current
    val ads by viewModel.adminAds.collectAsStateWithLifecycle()
    val announcements by viewModel.adminAnnouncements.collectAsStateWithLifecycle()
    val uploadState by viewModel.bannerUploadState.collectAsStateWithLifecycle()
    val isAuthorized = viewModel.isAdminOrOwner()

    AdminAdsAndAlertsPanel(
        isAuthorized = isAuthorized,
        ads = ads,
        announcements = announcements,
        uploadState = uploadState,
        onImageUriPicked = { uri, onReady ->
            viewModel.selectAndOptimizeBannerImage(context, uri, onReady)
        },
        onClearUploadFeedback = { viewModel.clearBannerUploadFeedback() },
        onPublishOrUpdateBanner = { existingAd, title, promo, dest, placement, active, pendingImage, keepUrl, keepStoragePath, prevStoragePath, onDone ->
            viewModel.publishOrUpdateBannerAd(
                existingAd = existingAd,
                title = title,
                promoText = promo,
                destinationUrl = dest,
                placement = placement,
                active = active,
                pendingImage = pendingImage,
                keepExistingImageUrl = keepUrl,
                keepExistingStoragePath = keepStoragePath,
                previousStoragePathToCleanup = prevStoragePath,
                onDone = onDone
            )
        },
        onToggleAdActive = { viewModel.toggleAdvertisementActive(it) },
        onDeleteAd = { viewModel.deleteAdvertisement(it) },
        onSaveAnnouncement = { ann, onDone -> viewModel.saveAnnouncement(ann, onDone) },
        onDeleteAnnouncement = { viewModel.deleteAnnouncement(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminAdsAndAlertsPanel(
    isAuthorized: Boolean,
    ads: List<Advertisement>,
    announcements: List<Announcement>,
    uploadState: BannerEditorUploadState,
    onImageUriPicked: (Uri, (BannerImageSelection) -> Unit) -> Unit,
    onClearUploadFeedback: () -> Unit,
    onPublishOrUpdateBanner: (
        existingAd: Advertisement?,
        title: String,
        promoText: String,
        destinationUrl: String,
        placement: String,
        active: Boolean,
        pendingImage: BannerImageSelection?,
        keepExistingImageUrl: String,
        keepExistingStoragePath: String,
        previousStoragePathToCleanup: String?,
        onDone: () -> Unit
    ) -> Unit,
    onToggleAdActive: (Advertisement) -> Unit,
    onDeleteAd: (Advertisement) -> Unit,
    onSaveAnnouncement: (Announcement, () -> Unit) -> Unit,
    onDeleteAnnouncement: (String) -> Unit
) {
    if (!isAuthorized) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("admin_ads_access_denied"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = LiveRed, modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Owner & Admin Access Required", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Only authorized DYFOOT owners and administrators can upload, publish, edit, or remove advertisement banners.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateText
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var editingAd by remember { mutableStateOf<Advertisement?>(null) }
    var adTitle by remember { mutableStateOf("") }
    var adPromo by remember { mutableStateOf("") }
    var adDest by remember { mutableStateOf("dyfoot://vip") }
    var adPlacement by remember { mutableStateOf(AdPlacement.HOME_TOP.wireValue) }
    var adActive by remember { mutableStateOf(true) }

    var pendingImage by remember { mutableStateOf<BannerImageSelection?>(null) }
    var existingImageUrl by remember { mutableStateOf("") }
    var existingStoragePath by remember { mutableStateOf("") }
    var originalStoragePathBeforeEdit by remember { mutableStateOf<String?>(null) }

    var showImageSourceSheet by remember { mutableStateOf(false) }
    var adFilter by remember { mutableStateOf("ALL") } // ALL, ACTIVE, PAUSED
    var previewingAd by remember { mutableStateOf<Advertisement?>(null) }
    var confirmingDeleteAd by remember { mutableStateOf<Advertisement?>(null) }

    var annTitle by remember { mutableStateOf("") }
    var annMsg by remember { mutableStateOf("") }
    var annPriority by remember { mutableStateOf("URGENT") }

    fun resetBannerForm() {
        editingAd = null
        adTitle = ""
        adPromo = ""
        adDest = "dyfoot://vip"
        adPlacement = AdPlacement.HOME_TOP.wireValue
        adActive = true
        pendingImage = null
        existingImageUrl = ""
        existingStoragePath = ""
        originalStoragePathBeforeEdit = null
    }

    fun startEditingAd(ad: Advertisement) {
        onClearUploadFeedback()
        editingAd = ad
        adTitle = ad.title
        adPromo = ad.promoText
        adDest = ad.destinationUrl
        adPlacement = AdPlacement.safeFrom(ad.placement).wireValue
        adActive = ad.active
        pendingImage = null
        existingImageUrl = ad.imageUrl
        existingStoragePath = ad.storagePath
        originalStoragePathBeforeEdit = ad.storagePath.takeIf { it.isNotBlank() }
        coroutineScope.launch {
            listState.animateScrollToItem(0)
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImageUriPicked(uri) { optimized ->
                pendingImage = optimized
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImageUriPicked(uri) { optimized ->
                pendingImage = optimized
            }
        }
    }

    val filteredAds = remember(ads, adFilter) {
        when (adFilter) {
            "ACTIVE" -> ads.filter { it.active }
            "PAUSED" -> ads.filter { !it.active }
            else -> ads
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_ads_lazy_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "create_edit_ad_card") {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (editingAd != null) NeonLime.copy(alpha = 0.7f) else MidnightBorder
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (editingAd != null) {
                                "Edit Promotional Banner / Ad"
                            } else {
                                "Create Promotional Banner / Ad"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonLime,
                            fontWeight = FontWeight.Bold
                        )
                        if (editingAd != null) {
                            TextButton(
                                onClick = {
                                    resetBannerForm()
                                    onClearUploadFeedback()
                                },
                                enabled = !uploadState.isBusy
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel Edit", color = SlateText)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = adTitle,
                        onValueChange = {
                            adTitle = it
                            if (uploadState.errorMessage != null || uploadState.successMessage != null) {
                                onClearUploadFeedback()
                            }
                        },
                        label = { Text("Banner Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ad_title_input")
                    )

                    OutlinedTextField(
                        value = adPromo,
                        onValueChange = {
                            adPromo = it
                            if (uploadState.errorMessage != null || uploadState.successMessage != null) {
                                onClearUploadFeedback()
                            }
                        },
                        label = { Text("Promo Copy") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ad_promo_input")
                    )

                    // Directly below Promo Copy: Upload Banner Image section
                    UploadBannerImageCard(
                        pendingImage = pendingImage,
                        existingImageUrl = existingImageUrl,
                        uploadState = uploadState,
                        onChooseOrChangeClick = { showImageSourceSheet = true },
                        onRemoveClick = {
                            pendingImage = null
                            existingImageUrl = ""
                            existingStoragePath = ""
                            onClearUploadFeedback()
                        }
                    )

                    OutlinedTextField(
                        value = adDest,
                        onValueChange = {
                            adDest = it
                            if (uploadState.errorMessage != null || uploadState.successMessage != null) {
                                onClearUploadFeedback()
                            }
                        },
                        label = { Text("Destination (dyfoot://vip, dyfoot://tips, dyfoot://hub or https://...)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ad_destination_input")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Placement",
                            style = MaterialTheme.typography.labelMedium,
                            color = SlateText
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AdPlacement.entries.forEach { place ->
                                val selected = adPlacement == place.wireValue
                                FilterChip(
                                    selected = selected,
                                    onClick = { adPlacement = place.wireValue },
                                    label = { Text(place.wireValue) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonLimeContainer,
                                        selectedLabelColor = NeonLime,
                                        containerColor = MidnightCardAlt,
                                        labelColor = SlateText
                                    ),
                                    modifier = Modifier.testTag("ad_placement_${place.wireValue}")
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MidnightCardAlt,
                        border = BorderStroke(1.dp, MidnightBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Publication Status",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CrispWhite
                                )
                                Text(
                                    text = if (adActive) "Active — Live immediately to all DYFOOT users" else "Inactive — Saved as paused draft",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (adActive) WonEmerald else SlateText
                                )
                            }
                            Switch(
                                checked = adActive,
                                onCheckedChange = { adActive = it },
                                modifier = Modifier.testTag("ad_active_switch")
                            )
                        }
                    }

                    AnimatedVisibility(visible = uploadState.errorMessage != null) {
                        uploadState.errorMessage?.let { err ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = LiveRedContainer,
                                border = BorderStroke(1.dp, LiveRed.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ad_upload_error")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = LiveRed, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(err, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = uploadState.successMessage != null) {
                        uploadState.successMessage?.let { msg ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = WonEmeraldContainer,
                                border = BorderStroke(1.dp, WonEmerald.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ad_upload_success")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = WonEmerald, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(msg, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onPublishOrUpdateBanner(
                                editingAd,
                                adTitle,
                                adPromo,
                                adDest,
                                adPlacement,
                                adActive,
                                pendingImage,
                                existingImageUrl,
                                existingStoragePath,
                                originalStoragePathBeforeEdit
                            ) {
                                resetBannerForm()
                            }
                        },
                        enabled = !uploadState.isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonLime,
                            contentColor = MidnightBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("ad_publish_button")
                    ) {
                        Text(
                            text = when {
                                uploadState.isOptimizing -> "Optimizing Image..."
                                uploadState.isUploading -> "Uploading & Publishing..."
                                editingAd != null -> "Save Banner Changes"
                                else -> "Publish Ad Banner"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Ads Management Section
        item(key = "ads_management_header") {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .testTag("ads_management_section")
            ) {
                SectionHeader(
                    title = "Ads Management",
                    eyebrow = "${ads.size} CAMPAIGN(S)"
                )
                Text(
                    text = "Preview, edit, deactivate or delete published promotional banners",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateText
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "All (${ads.size})", "ACTIVE" to "Active (${ads.count { it.active }})", "PAUSED" to "Paused (${ads.count { !it.active }})").forEach { (key, label) ->
                        FilterChip(
                            selected = adFilter == key,
                            onClick = { adFilter = key },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonLimeContainer,
                                selectedLabelColor = NeonLime,
                                containerColor = MidnightCard,
                                labelColor = SlateText
                            )
                        )
                    }
                }
            }
        }

        if (filteredAds.isEmpty()) {
            item(key = "ads_empty_state") {
                EmptyStateCard(
                    icon = Icons.Filled.Campaign,
                    title = if (adFilter == "ALL") "No Promotional Banners Yet" else "No ${adFilter.lowercase()} Banners",
                    subtitle = "Create a banner above with an uploaded image and target placement to display it live across DYFOOT."
                )
            }
        } else {
            items(filteredAds, key = { it.id }) { ad ->
                AdminAdManagementCard(
                    ad = ad,
                    onPreview = { previewingAd = ad },
                    onEdit = { startEditingAd(ad) },
                    onToggleActive = { onToggleAdActive(ad) },
                    onDelete = { confirmingDeleteAd = ad }
                )
            }
        }

        item(key = "publish_announcement_card") {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = BorderStroke(1.dp, MidnightBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Publish Platform Announcement", style = MaterialTheme.typography.titleMedium, color = VipAmber)
                    OutlinedTextField(value = annTitle, onValueChange = { annTitle = it }, label = { Text("Announcement Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = annMsg, onValueChange = { annMsg = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("NORMAL", "URGENT").forEach { p ->
                            FilterChip(selected = annPriority == p, onClick = { annPriority = p }, label = { Text(p) })
                        }
                    }
                    Button(
                        onClick = {
                            if (annTitle.isBlank() || annMsg.isBlank()) return@Button
                            onSaveAnnouncement(
                                Announcement(
                                    title = annTitle.trim(),
                                    message = annMsg.trim(),
                                    priority = annPriority,
                                    published = true
                                )
                            ) { annTitle = ""; annMsg = "" }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VipAmber, contentColor = MidnightBlack),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Post Announcement", fontWeight = FontWeight.Bold) }
                }
            }
        }

        items(announcements, key = { "ann_${it.id}" }) { ann ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MidnightCard,
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("[${ann.priority}] ${ann.title}", style = MaterialTheme.typography.titleSmall, color = CrispWhite)
                        Text(ann.message, style = MaterialTheme.typography.bodySmall, color = SlateText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onDeleteAnnouncement(ann.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete announcement", tint = LiveRed)
                    }
                }
            }
        }
    }

    if (showImageSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImageSourceSheet = false },
            containerColor = MidnightCard,
            contentColor = CrispWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select Banner Image Source",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CrispWhite
                )
                Text(
                    text = "Supported formats: JPG, JPEG, PNG, WEBP • Maximum 5 MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateText
                )
                Button(
                    onClick = {
                        showImageSourceSheet = false
                        galleryPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("ad_picker_gallery_option")
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Phone Photo Gallery", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        showImageSourceSheet = false
                        filePickerLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
                    },
                    border = BorderStroke(1.dp, MidnightBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("ad_picker_files_option")
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = InfoCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Browse Device Files", color = CrispWhite, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    previewingAd?.let { ad ->
        AlertDialog(
            onDismissRequest = { previewingAd = null },
            containerColor = MidnightBlack,
            titleContentColor = CrispWhite,
            title = {
                Column {
                    Text("Live Banner Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Placement: ${ad.placement} • ${if (ad.active) "ACTIVE" else "PAUSED"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonLime
                    )
                }
            },
            text = {
                AdBannerCard(ad = ad, onClick = {})
            },
            confirmButton = {
                TextButton(onClick = { previewingAd = null }) {
                    Text("Close", color = NeonLime, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    confirmingDeleteAd?.let { ad ->
        AlertDialog(
            onDismissRequest = { confirmingDeleteAd = null },
            containerColor = MidnightCard,
            titleContentColor = CrispWhite,
            textContentColor = SlateText,
            title = { Text("Delete Promotional Banner?") },
            text = {
                Text("This will permanently remove \"${ad.title}\" (${ad.placement}) and its uploaded banner image from DYFOOT.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = ad
                        confirmingDeleteAd = null
                        if (editingAd?.id == target.id) {
                            resetBannerForm()
                        }
                        onDeleteAd(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveRed, contentColor = CrispWhite)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDeleteAd = null }) {
                    Text("Cancel", color = SlateText)
                }
            }
        )
    }
}

@Composable
private fun UploadBannerImageCard(
    pendingImage: BannerImageSelection?,
    existingImageUrl: String,
    uploadState: BannerEditorUploadState,
    onChooseOrChangeClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val hasPreview = pendingImage != null || existingImageUrl.startsWith("http")
    val previewBitmap = remember(pendingImage) {
        pendingImage?.let { selection ->
            runCatching {
                BitmapFactory.decodeByteArray(
                    selection.optimizedBytes,
                    0,
                    selection.optimizedBytes.size
                )?.asImageBitmap()
            }.getOrNull()
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MidnightCardAlt,
        border = BorderStroke(
            width = 1.dp,
            color = if (hasPreview) NeonLime.copy(alpha = 0.55f) else MidnightBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ad_upload_image_section")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Upload Banner Image",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = CrispWhite
                    )
                    Text(
                        text = "JPG, JPEG, PNG, WEBP • Max 5 MB • Auto-optimized",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateText
                    )
                }
                if (pendingImage != null) {
                    Surface(
                        color = NeonLimeContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${pendingImage.width}×${pendingImage.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonLime,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (hasPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MidnightBlack)
                        .testTag("ad_image_preview")
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = "Selected banner image preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (existingImageUrl.startsWith("http")) {
                        AsyncImage(
                            model = existingImageUrl,
                            contentDescription = "Existing banner image preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Surface(
                        color = MidnightBlack.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        val badgeText = if (pendingImage != null) {
                            "${pendingImage.formatLabel} → WEBP • ${BannerImageOptimizer.formatReadableSize(pendingImage.optimizedSizeBytes)} (from ${BannerImageOptimizer.formatReadableSize(pendingImage.originalSizeBytes)})"
                        } else {
                            "Current Cloud Storage Banner"
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonLime,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onChooseOrChangeClick,
                        enabled = !uploadState.isBusy,
                        border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("ad_change_image_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change Image", color = NeonLime, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onRemoveClick,
                        enabled = !uploadState.isBusy,
                        border = BorderStroke(1.dp, LiveRed.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("ad_remove_image_button")
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = LiveRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remove Image", color = LiveRed, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MidnightBlack,
                    border = BorderStroke(1.dp, MidnightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = "Upload banner image",
                            tint = NeonLime,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Select a wide promotional banner from your gallery or device files",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateText
                        )
                        Button(
                            onClick = onChooseOrChangeClick,
                            enabled = !uploadState.isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonLimeContainer,
                                contentColor = NeonLime
                            ),
                            border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ad_choose_image_button")
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Choose Image", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (uploadState.isOptimizing) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        color = NeonLime,
                        trackColor = MidnightBlack,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = uploadState.statusMessage ?: "Optimizing image...",
                        style = MaterialTheme.typography.labelSmall,
                        color = InfoCyan
                    )
                }
            } else if (uploadState.isUploading) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.testTag("ad_upload_progress")
                ) {
                    LinearProgressIndicator(
                        progress = { uploadState.progress.coerceIn(0f, 1f) },
                        color = NeonLime,
                        trackColor = MidnightBlack,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = uploadState.statusMessage ?: "Uploading banner image...",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonLime
                    )
                }
            } else if (!uploadState.statusMessage.isNullOrBlank() && uploadState.errorMessage == null && uploadState.successMessage == null) {
                Text(
                    text = uploadState.statusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = InfoCyan
                )
            }
        }
    }
}

@Composable
private fun AdminAdManagementCard(
    ad: Advertisement,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MidnightCard,
        border = BorderStroke(
            width = 1.dp,
            color = if (ad.active) NeonLime.copy(alpha = 0.35f) else MidnightBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_ad_card_${ad.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ad.imageUrl.startsWith("http")) {
                    AsyncImage(
                        model = ad.imageUrl,
                        contentDescription = ad.title,
                        modifier = Modifier
                            .width(88.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_vip_crown_banner),
                        contentDescription = ad.title,
                        modifier = Modifier
                            .width(88.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (ad.active) WonEmeraldContainer else MidnightCardAlt,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (ad.active) "ACTIVE" else "PAUSED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (ad.active) WonEmerald else SlateText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            color = NeonLimeContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = ad.placement,
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonLime,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = ad.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CrispWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = ad.promoText,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (ad.destinationUrl.isNotBlank()) {
                        Text(
                            text = "→ ${ad.destinationUrl}",
                            style = MaterialTheme.typography.labelSmall,
                            color = InfoCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPreview,
                    border = BorderStroke(1.dp, MidnightBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("ad_preview_btn_${ad.id}")
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = "Preview ad", tint = CrispWhite, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Preview", color = CrispWhite, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onEdit,
                    border = BorderStroke(1.dp, MidnightBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("ad_edit_btn_${ad.id}")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ad", tint = NeonLime, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", color = NeonLime, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = onToggleActive,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("ad_toggle_btn_${ad.id}")
                ) {
                    Text(
                        text = if (ad.active) "Deactivate" else "Activate",
                        color = if (ad.active) VipAmber else WonEmerald,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("ad_delete_btn_${ad.id}")
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete ad", tint = LiveRed)
                }
            }
        }
    }
}
