package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.NewsArticle
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.InfoCyan
import com.example.ui.theme.LiveRed
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardAlt
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.theme.SlateText
import com.example.ui.theme.VipAmber
import com.example.ui.theme.VipAmberContainer
import com.example.ui.theme.WonEmerald
import com.example.ui.theme.WonEmeraldContainer
import com.example.viewmodel.DyfootViewModel

private val ADMIN_NEWS_CATEGORIES = listOf(
    "TANZANIA_LIGI",
    "TANZANIA_SOCIAL",
    "AFRICA_CAF",
    "INTERNATIONAL",
    "TRANSFERS_&_TACTICS"
)

private val LOCAL_FOOTBALL_LEAGUE_PRESETS = listOf(
    "NBC Premier League",
    "CRDB Bank Cup",
    "Mapinduzi Cup",
    "CAF Champions League",
    "CAF Confederation Cup",
    "Taifa Stars / AFCON"
)

@Composable
fun AdminLocalNewsSection(viewModel: DyfootViewModel) {
    val articles by viewModel.adminNews.collectAsStateWithLifecycle()
    val isAuthorized = viewModel.isAdminOrOwner()

    AdminBilingualNewsPanel(
        isAuthorized = isAuthorized,
        articles = articles,
        onSaveArticle = { article, onDone -> viewModel.saveNews(article, onDone) },
        onDeleteArticle = { newsId -> viewModel.deleteNews(newsId) }
    )
}

@Composable
fun AdminBilingualNewsPanel(
    isAuthorized: Boolean,
    articles: List<NewsArticle>,
    onSaveArticle: (NewsArticle, () -> Unit) -> Unit,
    onDeleteArticle: (String) -> Unit
) {
    if (!isAuthorized) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("admin_news_access_denied"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = LiveRed, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Admin-Only Local News Desk",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = CrispWhite
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Only verified DYFOOT admins can author, edit, or publish internal bilingual football articles.",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateText
            )
        }
        return
    }

    var editingId by remember { mutableStateOf("") }
    var editingAuthorId by remember { mutableStateOf("") }
    var editingLikesCount by remember { mutableStateOf(0L) }
    var editingCommentsCount by remember { mutableStateOf(0L) }

    var languageMode by remember { mutableStateOf("BILINGUAL") } // BILINGUAL, SW, EN
    var localFootball by remember { mutableStateOf(true) }
    var category by remember { mutableStateOf("TANZANIA_LIGI") }
    var leagueTag by remember { mutableStateOf("NBC Premier League") }
    var sourceName by remember { mutableStateOf("DYFOOT Tanzania Desk") }
    var imageUrl by remember { mutableStateOf("") }
    var published by remember { mutableStateOf(true) }

    var titleEn by remember { mutableStateOf("") }
    var summaryEn by remember { mutableStateOf("") }
    var contentEn by remember { mutableStateOf("") }

    var titleSw by remember { mutableStateOf("") }
    var summarySw by remember { mutableStateOf("") }
    var contentSw by remember { mutableStateOf("") }

    var validationError by remember { mutableStateOf<String?>(null) }
    var previewArticle by remember { mutableStateOf<NewsArticle?>(null) }
    var previewSwahili by remember { mutableStateOf(false) }
    var listFilter by remember { mutableStateOf("ALL") } // ALL, LOCAL, BILINGUAL, DRAFTS

    fun resetForm() {
        editingId = ""
        editingAuthorId = ""
        editingLikesCount = 0L
        editingCommentsCount = 0L
        languageMode = "BILINGUAL"
        localFootball = true
        category = "TANZANIA_LIGI"
        leagueTag = "NBC Premier League"
        sourceName = "DYFOOT Tanzania Desk"
        imageUrl = ""
        published = true
        titleEn = ""
        summaryEn = ""
        contentEn = ""
        titleSw = ""
        summarySw = ""
        contentSw = ""
        validationError = null
    }

    fun buildArticleCandidate(): NewsArticle? {
        val enValid = titleEn.trim().length >= 3 && summaryEn.trim().isNotEmpty() && contentEn.trim().isNotEmpty()
        val swValid = titleSw.trim().length >= 3 && summarySw.trim().isNotEmpty() && contentSw.trim().isNotEmpty()

        when (languageMode) {
            "BILINGUAL" -> {
                if (!enValid || !swValid) {
                    validationError = "Bilingual mode requires both English and Kiswahili headline (3+ chars), summary, and full body."
                    return null
                }
            }
            "SW" -> {
                if (!swValid) {
                    validationError = "Kiswahili mode requires Kichwa cha Habari (3+ chars), Muhtasari, and Habari Kamili."
                    return null
                }
            }
            else -> {
                if (!enValid) {
                    validationError = "English mode requires Headline (3+ chars), Summary, and Full Article Body."
                    return null
                }
            }
        }

        val cleanImg = imageUrl.trim()
        if (cleanImg.isNotBlank() &&
            !cleanImg.startsWith("https://", ignoreCase = true) &&
            !cleanImg.startsWith("http://", ignoreCase = true)
        ) {
            validationError = "Optional cover image must start with https:// or be left blank."
            return null
        }

        validationError = null
        val canonicalTitle = titleEn.trim().ifBlank { titleSw.trim() }
        val canonicalSummary = summaryEn.trim().ifBlank { summarySw.trim() }
        val canonicalContent = contentEn.trim().ifBlank { contentSw.trim() }

        return NewsArticle(
            id = editingId,
            authorId = editingAuthorId,
            title = canonicalTitle,
            summary = canonicalSummary,
            content = canonicalContent,
            titleSw = titleSw.trim(),
            summarySw = summarySw.trim(),
            contentSw = contentSw.trim(),
            leagueTag = leagueTag.trim(),
            localFootball = localFootball,
            category = category.trim().ifBlank { "TANZANIA_LIGI" },
            sourceName = sourceName.trim().ifBlank { "DYFOOT Tanzania Desk" },
            sourceUrl = "",
            imageUrl = cleanImg,
            published = published,
            likesCount = editingLikesCount,
            commentsCount = editingCommentsCount,
            isExternalRss = false,
            language = languageMode
        )
    }

    val filteredArticles = remember(articles, listFilter) {
        when (listFilter) {
            "LOCAL" -> articles.filter { it.localFootball }
            "BILINGUAL" -> articles.filter { it.language == "BILINGUAL" || (it.titleSw.isNotBlank() && it.title.isNotBlank()) }
            "DRAFTS" -> articles.filter { !it.published }
            else -> articles
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = NeonLime,
        unfocusedBorderColor = MidnightBorder,
        focusedTextColor = CrispWhite,
        unfocusedTextColor = CrispWhite,
        focusedLabelColor = NeonLime,
        unfocusedLabelColor = SlateText
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_local_news_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = NeonLimeContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = NeonLime, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Secure Admin Bilingual Football Desk",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = CrispWhite
                        )
                        Text(
                            text = "Stored in Firestore under admin-only rules • Served 100% internally without external links.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateText
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (editingId.isBlank()) "Create Local Football News" else "Edit Stored Football Article",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonLime
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MidnightCardAlt,
                            border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "IN-APP ONLY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = NeonLime,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = "Language Fields Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = SlateText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "BILINGUAL" to "Bilingual (EN+SW)",
                            "SW" to "Kiswahili",
                            "EN" to "English"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = languageMode == mode,
                                onClick = {
                                    languageMode = mode
                                    validationError = null
                                },
                                label = { Text(label, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonLime,
                                    selectedLabelColor = MidnightBlack,
                                    containerColor = MidnightCardAlt,
                                    labelColor = SlateText
                                ),
                                modifier = Modifier.testTag("admin_news_mode_${mode.lowercase()}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Local Football Story (Tanzania & Africa)",
                                style = MaterialTheme.typography.labelLarge,
                                color = CrispWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Strictly blocks external links when enabled.",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateText
                            )
                        }
                        Switch(
                            checked = localFootball,
                            onCheckedChange = { localFootball = it },
                            modifier = Modifier.testTag("admin_news_local_switch")
                        )
                    }

                    Text(
                        text = "Category & League Tag",
                        style = MaterialTheme.typography.labelMedium,
                        color = SlateText
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ADMIN_NEWS_CATEGORIES) { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat.replace("_", " ")) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonLimeContainer,
                                    selectedLabelColor = NeonLime,
                                    containerColor = MidnightCardAlt,
                                    labelColor = SlateText
                                )
                            )
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LOCAL_FOOTBALL_LEAGUE_PRESETS) { preset ->
                            FilterChip(
                                selected = leagueTag.equals(preset, ignoreCase = true),
                                onClick = { leagueTag = preset },
                                label = { Text(preset) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VipAmberContainer,
                                    selectedLabelColor = VipAmber,
                                    containerColor = MidnightCardAlt,
                                    labelColor = SlateText
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = leagueTag,
                        onValueChange = { leagueTag = it },
                        label = { Text("League / Competition Tag") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_news_league_tag")
                    )

                    if (languageMode == "BILINGUAL" || languageMode == "EN") {
                        HorizontalDivider(color = MidnightBorder)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Translate, contentDescription = null, tint = InfoCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ENGLISH CONTENT",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = InfoCyan
                            )
                        }
                        OutlinedTextField(
                            value = titleEn,
                            onValueChange = { titleEn = it },
                            label = { Text("Headline (English)") },
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_news_title_en")
                        )
                        OutlinedTextField(
                            value = summaryEn,
                            onValueChange = { summaryEn = it },
                            label = { Text("Summary (English)") },
                            maxLines = 2,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_news_summary_en")
                        )
                        OutlinedTextField(
                            value = contentEn,
                            onValueChange = { contentEn = it },
                            label = { Text("Full Article Body (English)") },
                            minLines = 3,
                            maxLines = 8,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_news_content_en")
                        )
                    }

                    if (languageMode == "BILINGUAL" || languageMode == "SW") {
                        HorizontalDivider(color = MidnightBorder)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Translate, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "MAUDHUI YA KISWAHILI (SWAHILI)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeonLime
                            )
                        }
                        OutlinedTextField(
                            value = titleSw,
                            onValueChange = { titleSw = it },
                            label = { Text("Kichwa cha Habari (Kiswahili)") },
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_news_title_sw")
                        )
                        OutlinedTextField(
                            value = summarySw,
                            onValueChange = { summarySw = it },
                            label = { Text("Muhtasari (Kiswahili)") },
                            maxLines = 2,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_news_summary_sw")
                        )
                        OutlinedTextField(
                            value = contentSw,
                            onValueChange = { contentSw = it },
                            label = { Text("Habari Kamili (Kiswahili)") },
                            minLines = 3,
                            maxLines = 8,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_news_content_sw")
                        )
                    }

                    HorizontalDivider(color = MidnightBorder)

                    OutlinedTextField(
                        value = sourceName,
                        onValueChange = { sourceName = it },
                        label = { Text("Internal Desk / Author Label") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_news_source_name")
                    )

                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("Cover Image URL (Optional • Never External Article Link)") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_news_image_url")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (published) "Published to App Feed Immediately" else "Save as Private Admin Draft",
                                style = MaterialTheme.typography.labelLarge,
                                color = CrispWhite
                            )
                            Text(
                                text = if (published) "Visible inside Home & Sport Hub without external links." else "Visible only to verified admins until published.",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateText
                            )
                        }
                        Switch(
                            checked = published,
                            onCheckedChange = { published = it },
                            modifier = Modifier.testTag("admin_news_publish_switch")
                        )
                    }

                    validationError?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = LiveRed.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, LiveRed.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_news_validation_error")
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = LiveRed,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val candidate = buildArticleCandidate() ?: return@Button
                                onSaveArticle(candidate) { resetForm() }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_news_save_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    editingId.isNotBlank() -> "Update Article"
                                    published -> "Store & Publish"
                                    else -> "Save Draft"
                                },
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val candidate = buildArticleCandidate() ?: return@OutlinedButton
                                previewSwahili = languageMode == "SW"
                                previewArticle = candidate
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.6f)),
                            modifier = Modifier.testTag("admin_news_preview_btn")
                        ) {
                            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Preview", color = NeonLime)
                        }
                    }

                    if (editingId.isNotBlank()) {
                        TextButton(
                            onClick = { resetForm() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Cancel Editing", color = SlateText)
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Stored Internal Articles (${articles.size})")
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "All", "LOCAL" to "Local", "BILINGUAL" to "EN+SW", "DRAFTS" to "Drafts").forEach { (key, label) ->
                    FilterChip(
                        selected = listFilter == key,
                        onClick = { listFilter = key },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonLime,
                            selectedLabelColor = MidnightBlack,
                            containerColor = MidnightCard,
                            labelColor = SlateText
                        )
                    )
                }
            }
        }

        if (filteredArticles.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Newspaper,
                    title = "No Stored Articles Yet",
                    subtitle = "Create a bilingual English & Swahili local football story above to publish directly inside DYFOOT."
                )
            }
        } else {
            items(filteredArticles, key = { it.id }) { article ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MidnightCard),
                    border = BorderStroke(1.dp, MidnightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (article.published) WonEmeraldContainer else VipAmberContainer
                            ) {
                                Text(
                                    text = if (article.published) "PUBLISHED" else "DRAFT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (article.published) WonEmerald else VipAmber,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NeonLimeContainer
                            ) {
                                Text(
                                    text = article.language.ifBlank { "EN" }.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonLime,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            if (article.localFootball) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MidnightCardAlt,
                                    border = BorderStroke(1.dp, MidnightBorder)
                                ) {
                                    Text(
                                        text = article.leagueTag.ifBlank { "LOCAL • IN-APP" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InfoCyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (article.title.isNotBlank()) {
                            Text(
                                text = article.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = CrispWhite
                            )
                        }
                        if (article.titleSw.isNotBlank() && article.titleSw != article.title) {
                            Text(
                                text = "SW: ${article.titleSw}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = NeonLime
                            )
                        }
                        Text(
                            text = "${article.category.replace("_", " ")} • ${article.sourceName.ifBlank { "DYFOOT Editorial" }} • No external links",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateText
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row {
                                TextButton(onClick = {
                                    previewSwahili = article.language.equals("SW", ignoreCase = true)
                                    previewArticle = article
                                }) {
                                    Text("Read", color = NeonLime)
                                }
                                TextButton(onClick = {
                                    onSaveArticle(article.copy(published = !article.published)) {}
                                }) {
                                    Text(if (article.published) "Unpublish" else "Publish", color = InfoCyan)
                                }
                            }
                            Row {
                                IconButton(onClick = {
                                    editingId = article.id
                                    editingAuthorId = article.authorId
                                    editingLikesCount = article.likesCount
                                    editingCommentsCount = article.commentsCount
                                    languageMode = when {
                                        article.titleSw.isNotBlank() && article.title.isNotBlank() -> "BILINGUAL"
                                        article.language.equals("SW", ignoreCase = true) -> "SW"
                                        else -> "EN"
                                    }
                                    localFootball = article.localFootball
                                    category = article.category
                                    leagueTag = article.leagueTag
                                    sourceName = article.sourceName.ifBlank { "DYFOOT Tanzania Desk" }
                                    imageUrl = article.imageUrl
                                    published = article.published
                                    titleEn = article.title
                                    summaryEn = article.summary
                                    contentEn = article.content
                                    titleSw = article.titleSw
                                    summarySw = article.summarySw
                                    contentSw = article.contentSw
                                    validationError = null
                                }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit article", tint = InfoCyan)
                                }
                                IconButton(onClick = { onDeleteArticle(article.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete article", tint = LiveRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewArticle?.let { art ->
        InAppArticleReaderSheet(
            article = art,
            isSwahili = previewSwahili,
            isLiked = false,
            isBookmarked = false,
            onToggleLike = {},
            onToggleBookmark = {},
            onDismiss = { previewArticle = null }
        )
    }
}
