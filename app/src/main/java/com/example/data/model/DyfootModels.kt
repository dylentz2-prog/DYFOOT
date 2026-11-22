package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val OFFICIAL_OWNER_EMAIL = "dyvix02@gmail.com"
const val OFFICIAL_MERCHANT_NUMBER = "0706499813"
const val OFFICIAL_MERCHANT_NAME = "DYFOOT Tanzania"
const val OWNER_TIKTOK_HANDLE = "@dyvix_"
const val OWNER_TIKTOK_URL = "https://www.tiktok.com/@dyvix_"
const val OWNER_INSTAGRAM_HANDLE = "@dyvix_02"
const val OWNER_INSTAGRAM_URL = "https://www.instagram.com/dyvix_02"
const val OWNER_YOUTUBE_HANDLE = "@dyvix_02"
const val OWNER_YOUTUBE_URL = "https://www.youtube.com/@dyvix_02"

enum class UserRole(val label: String) {
    OWNER("OWNER"),
    ADMIN("ADMIN"),
    VIP_MEMBER("VIP MEMBER"),
    FREE_MEMBER("FREE MEMBER");

    companion object {
        fun safeFrom(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: FREE_MEMBER
    }
}

enum class VipTier(
    val label: String,
    val durationDays: Int,
    val defaultTzs: Long,
    val subtitle: String,
    val badgeText: String = ""
) {
    NONE("Free", 0, 0L, "Standard member", ""),
    DAILY("ROCK PACKAGE", 1, 1_000L, "Per Day • 24 Hours Full VIP", "DAILY PASS"),
    WEEKLY("STAR PACKAGE", 7, 5_000L, "Per Week • 7 Days Full VIP", "POPULAR"),
    MONTHLY("PRO PACKAGE", 30, 15_000L, "Per Month • 30 Days Full VIP", "BEST VALUE"),
    QUARTERLY("PRO+ QUARTER", 90, 40_000L, "Per 90 Days • Full VIP", ""),
    ANNUAL("LEGEND PACKAGE", 365, 80_000L, "Per Year • 365 Days Full VIP", "SAVE 55%");

    companion object {
        fun safeFrom(raw: String?): VipTier =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class TipTier {
    FREE, VIP;

    companion object {
        fun safeFrom(raw: String?): TipTier =
            entries.firstOrNull { it.name == raw } ?: FREE
    }
}

enum class RiskLevel(val label: String) {
    LOW("Low Risk"),
    MEDIUM("Medium Risk"),
    HIGH("High Value");

    companion object {
        fun safeFrom(raw: String?): RiskLevel =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class TipStatus(val label: String) {
    PENDING("Pending"),
    WON("Won"),
    LOST("Lost"),
    VOID("Void"),
    POSTPONED("Postponed");

    companion object {
        fun safeFrom(raw: String?): TipStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class MobileMoneyProvider(val displayName: String, val ussdHint: String) {
    M_PESA("Vodacom M-Pesa", "Dial *150*00# -> Send Money -> 0706499813"),
    TIGO_PESA("Mixx by Yas (Tigo Pesa)", "Dial *150*01# -> Send Money -> 0706499813"),
    AIRTEL_MONEY("Airtel Money", "Dial *150*60# -> Send Money -> 0706499813"),
    HALOPESA("HaloPesa", "Dial *150*88# -> Send Money -> 0706499813");

    fun wireValue(): String = when (this) {
        M_PESA -> "M-PESA"
        TIGO_PESA -> "TIGO_PESA"
        AIRTEL_MONEY -> "AIRTEL_MONEY"
        HALOPESA -> "HALOPESA"
    }

    companion object {
        fun safeFrom(raw: String?): MobileMoneyProvider = when (raw) {
            "M-PESA", "M_PESA" -> M_PESA
            "TIGO_PESA" -> TIGO_PESA
            "AIRTEL_MONEY" -> AIRTEL_MONEY
            "HALOPESA" -> HALOPESA
            else -> M_PESA
        }
    }
}

enum class PaymentStatus(val label: String) {
    PENDING_VERIFICATION("Pending Verification"),
    VERIFIED_PAID("Verified & Paid"),
    FAILED("Failed"),
    REFUNDED("Refunded");

    companion object {
        fun safeFrom(raw: String?): PaymentStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING_VERIFICATION
    }
}

enum class FixtureStatus(val label: String) {
    UPCOMING("Upcoming"),
    LIVE("LIVE"),
    FINISHED("FT"),
    POSTPONED("Postponed");

    companion object {
        fun safeFrom(raw: String?): FixtureStatus =
            entries.firstOrNull { it.name == raw } ?: UPCOMING
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val bio: String = "",
    val role: String = UserRole.FREE_MEMBER.name,
    val vipTier: String = VipTier.NONE.name,
    val vipExpiresAt: Long = 0L,
    val suspended: Boolean = false,
    val searchable: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedRole(): UserRole = UserRole.safeFrom(role)
    fun parsedVipTier(): VipTier = VipTier.safeFrom(vipTier)
}

@Keep
@IgnoreExtraProperties
data class UserPrivateInfo(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val phone: String = "",
    val notifyTips: Boolean = true,
    val notifyNews: Boolean = true,
    val notifyCommunity: Boolean = true,
    val notifyPayments: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Membership(
    @DocumentId val id: String = "",
    val userId: String = "",
    val planId: String = "",
    val planName: String = "",
    val status: String = "NONE",
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isCurrentlyActive(): Boolean {
        val exp = expiresAt ?: return false
        return status == "ACTIVE" && exp.toDate().time > System.currentTimeMillis()
    }
}

@Keep
@IgnoreExtraProperties
data class PaymentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userDisplayName: String = "",
    val planId: String = "",
    val planName: String = "",
    val amountTzs: Long = 0L,
    val currency: String = "TZS",
    val provider: String = "M-PESA",
    val payerPhone: String = "",
    val merchantNumber: String = OFFICIAL_MERCHANT_NUMBER,
    val transactionRef: String = "",
    val providerRef: String = "",
    val status: String = PaymentStatus.PENDING_VERIFICATION.name,
    val verifiedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TipItem(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val matchName: String = "",
    val homeTeam: String = "",
    val awayTeam: String = "",
    val competition: String = "",
    val kickoffLabel: String = "",
    val kickoffEpochMs: Long = 0L,
    val tier: String = TipTier.FREE.name,
    val market: String = "",
    val selection: String = "",
    val odds: String = "",
    val bookmakerName: String = "",
    val bookmakerCode: String = "",
    val predictedScore: String = "",
    val analysis: String = "",
    val riskLevel: String = RiskLevel.MEDIUM.name,
    val status: String = TipStatus.PENDING.name,
    val published: Boolean = true,
    val featured: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VipTipSecret(
    @DocumentId val id: String = "",
    val tipId: String = "",
    val selection: String = "",
    val bookmakerCode: String = "",
    val predictedScore: String = "",
    val analysis: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NewsArticle(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val title: String = "",
    val summary: String = "",
    val content: String = "",
    val titleSw: String = "",
    val summarySw: String = "",
    val contentSw: String = "",
    val leagueTag: String = "",
    val localFootball: Boolean = false,
    val category: String = "INTERNATIONAL",
    val sourceName: String = "",
    val sourceUrl: String = "",
    val imageUrl: String = "",
    val published: Boolean = true,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val isExternalRss: Boolean = false,
    val publishedDateLabel: String = "",
    val language: String = "EN"
)

fun NewsArticle.localizedTitle(isSwahili: Boolean): String =
    if (isSwahili) titleSw.ifBlank { title } else title.ifBlank { titleSw }

fun NewsArticle.localizedSummary(isSwahili: Boolean): String =
    if (isSwahili) summarySw.ifBlank { summary } else summary.ifBlank { summarySw }

fun NewsArticle.localizedContent(isSwahili: Boolean): String =
    if (isSwahili) contentSw.ifBlank { content } else content.ifBlank { contentSw }

fun NewsArticle.hasBilingualContent(): Boolean =
    language.equals("BILINGUAL", ignoreCase = true) ||
        (title.isNotBlank() && titleSw.isNotBlank() && content.isNotBlank() && contentSw.isNotBlank())

fun NewsArticle.supportsSwahili(): Boolean =
    language.equals("SW", ignoreCase = true) ||
        language.equals("BILINGUAL", ignoreCase = true) ||
        titleSw.isNotBlank() ||
        contentSw.isNotBlank() ||
        category.equals("SWAHILI", ignoreCase = true) ||
        category.startsWith("TANZANIA", ignoreCase = true) ||
        category.startsWith("AFRICA", ignoreCase = true)

fun NewsArticle.supportsEnglish(): Boolean =
    language.equals("EN", ignoreCase = true) ||
        language.equals("BILINGUAL", ignoreCase = true) ||
        (!language.equals("SW", ignoreCase = true) && title.isNotBlank())

@Keep
@IgnoreExtraProperties
data class ContentComment(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VideoItem(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val title: String = "",
    val caption: String = "",
    val creatorName: String = "",
    val category: String = "Highlights",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val durationSec: Long = 0L,
    val published: Boolean = true,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PodcastEpisode(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val title: String = "",
    val showName: String = "",
    val hostName: String = "",
    val description: String = "",
    val category: String = "Match Talk",
    val audioUrl: String = "",
    val coverUrl: String = "",
    val durationSec: Long = 0L,
    val published: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Advertisement(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val title: String = "",
    val promoText: String = "",
    val imageUrl: String = "",
    val destinationUrl: String = "",
    val placement: String = "HOME_TOP",
    val active: Boolean = true,
    val startEpochMs: Long = 0L,
    val endEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val title: String = "",
    val message: String = "",
    val priority: String = "NORMAL",
    val published: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FixtureItem(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val homeTeam: String = "",
    val awayTeam: String = "",
    val competition: String = "",
    val homeScore: Long = 0L,
    val awayScore: Long = 0L,
    val status: String = FixtureStatus.UPCOMING.name,
    val minuteLabel: String = "",
    val kickoffEpochMs: Long = 0L,
    val venue: String = "",
    val sourceLabel: String = "DYFOOT Official",
    val streamScheduled: Boolean = false,
    val streamStatus: String = "COMING_SOON",
    val eventsSummary: String = "",
    val published: Boolean = true,
    val homeLogoUrl: String = "",
    val awayLogoUrl: String = "",
    val leagueCode: String = "",
    val sportCategory: String = "Football",
    val espnEventId: String = "",
    val espnSportPath: String = "",
    val isHotMatch: Boolean = false,
    val possessionHome: String = "",
    val possessionAway: String = "",
    val shotsHome: String = "",
    val shotsAway: String = "",
    val shotsOnTargetHome: String = "",
    val shotsOnTargetAway: String = "",
    val cornersHome: String = "",
    val cornersAway: String = "",
    val yellowCardsHome: String = "",
    val yellowCardsAway: String = "",
    val redCardsHome: String = "",
    val redCardsAway: String = "",
    val foulsHome: String = "",
    val foulsAway: String = "",
    val offsidesHome: String = "",
    val offsidesAway: String = "",
    val passesHome: String = "",
    val passesAway: String = "",
    val referee: String = "",
    val attendance: String = "",
    val lineupsSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CommunityGroup(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val directMessage: Boolean = false,
    val publicRoom: Boolean = false,
    val createdBy: String = "",
    val memberUids: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastSenderName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GroupMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val publicRoom: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "GENERAL",
    val read: Boolean = false,
    val deepLinkRoute: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserBookmark(
    @DocumentId val id: String = "",
    val contentType: String = "",
    val contentId: String = "",
    val title: String = "",
    val subtitle: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BlockedUser(
    @DocumentId val id: String = "",
    val blockedUid: String = "",
    val blockedName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ContentReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val reason: String = "",
    val status: String = "OPEN",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminAuditLog(
    @DocumentId val id: String = "",
    val actorId: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppSettings(
    @DocumentId val id: String = "",
    val vipDailyTzs: Long = 1_000L,
    val vipWeeklyTzs: Long = 5_000L,
    val vipMonthlyTzs: Long = 15_000L,
    val vipQuarterlyTzs: Long = 40_000L,
    val vipAnnualTzs: Long = 80_000L,
    val merchantNumber: String = OFFICIAL_MERCHANT_NUMBER,
    val merchantName: String = OFFICIAL_MERCHANT_NAME,
    val categories: List<String> = listOf(
        "INTERNATIONAL",
        "AFRICA_LOCAL",
        "SWAHILI",
        "WORLD_SPORTS",
        "TRANSFERS",
        "PREVIEWS",
        "REPORTS",
        "ANALYSIS",
        "BLOGS"
    ),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CommunityStatus(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String = "",
    val text: String = "",
    val themeColor: String = "LIME",
    val expiresAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class StandingRow(
    val position: Int,
    val teamName: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalDiff: Int,
    val points: Int,
    val teamLogoUrl: String = ""
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
