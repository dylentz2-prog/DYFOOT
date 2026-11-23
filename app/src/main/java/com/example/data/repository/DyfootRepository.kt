package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.media.BannerImageOptimizer
import com.example.data.media.BannerImageSelection
import com.example.data.model.AdPlacement
import com.example.data.model.AdminAuditLog
import com.example.data.model.Advertisement
import com.example.data.model.Announcement
import com.example.data.model.AppSettings
import com.example.data.model.BlockedUser
import com.example.data.model.CommunityGroup
import com.example.data.model.CommunityStatus
import com.example.data.model.ContentComment
import com.example.data.model.ContentReport
import com.example.data.model.FixtureItem
import com.example.data.model.GroupMessage
import com.example.data.model.Membership
import com.example.data.model.MobileMoneyProvider
import com.example.data.model.NewsArticle
import com.example.data.model.NotificationItem
import com.example.data.model.OFFICIAL_MERCHANT_NAME
import com.example.data.model.OFFICIAL_MERCHANT_NUMBER
import com.example.data.model.OFFICIAL_OWNER_EMAIL
import com.example.data.model.PaymentRecord
import com.example.data.model.PaymentStatus
import com.example.data.model.PodcastEpisode
import com.example.data.model.TipItem
import com.example.data.model.TipTier
import com.example.data.model.UserBookmark
import com.example.data.model.UserPrivateInfo
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VideoItem
import com.example.data.model.VipTier
import com.example.data.model.VipTipSecret
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.TimeUnit

data class UploadedBannerAsset(
    val downloadUrl: String,
    val storagePath: String
)

class DyfootRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun isVerifiedOwner(user: FirebaseUser?): Boolean =
        user != null &&
            user.isEmailVerified &&
            user.email.equals(OFFICIAL_OWNER_EMAIL, ignoreCase = true)

    private suspend fun requireAdminAuthorized(): String {
        val user = auth.currentUser
            ?: throw IllegalStateException("Please sign in with an authorized DYFOOT owner or admin account.")
        if (isVerifiedOwner(user)) return user.uid
        val adminSnap = db.collection("admins").document(user.uid).get().await()
        if (adminSnap.exists() && adminSnap.getBoolean("active") == true) {
            return user.uid
        }
        throw SecurityException("Only authorized DYFOOT owners or admins can publish or manage advertisements.")
    }

    suspend fun ensureUserProfile(): Result<UserProfile> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        val uid = currentUser.uid
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val ownerAccount = isVerifiedOwner(currentUser)
            val baseUsername = (currentUser.email?.substringBefore("@") ?: "dyfoot_${uid.take(6)}")
                .replace(Regex("[^a-zA-Z0-9_]"), "_")
                .take(30)
                .ifBlank { "dyfoot_user" }
            val displayName = currentUser.displayName?.take(80)?.ifBlank { baseUsername } ?: baseUsername
            val photoUrl = currentUser.photoUrl?.toString()?.take(1000).orEmpty()

            if (!snap.exists()) {
                val role = if (ownerAccount) UserRole.OWNER.name else UserRole.FREE_MEMBER.name
                val payload = mapOf(
                    "userId" to uid,
                    "username" to baseUsername,
                    "displayName" to displayName,
                    "photoUrl" to photoUrl,
                    "bio" to if (ownerAccount) "Official DYFOOT Founder & Owner" else "DYFOOT Football Member",
                    "role" to role,
                    "vipTier" to VipTier.NONE.name,
                    "vipExpiresAt" to 0L,
                    "suspended" to false,
                    "searchable" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else if (ownerAccount && snap.getString("role") != UserRole.OWNER.name) {
                docRef.update(
                    mapOf(
                        "role" to UserRole.OWNER.name,
                        "username" to (snap.getString("username") ?: baseUsername),
                        "displayName" to displayName,
                        "photoUrl" to photoUrl,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Ensure isolated private PII & notification preferences doc
            val privateRef = docRef.collection("private").document("info")
            val privSnap = privateRef.get().await()
            if (!privSnap.exists()) {
                val privatePayload = mapOf(
                    "userId" to uid,
                    "email" to (currentUser.email ?: ""),
                    "phone" to "",
                    "notifyTips" to true,
                    "notifyNews" to true,
                    "notifyCommunity" to true,
                    "notifyPayments" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                privateRef.set(privatePayload).await()
            }

            // Ensure default platform settings if owner signs in for first time
            if (ownerAccount) {
                val settingsRef = db.collection("app_settings").document("platform")
                if (!settingsRef.get().await().exists()) {
                    settingsRef.set(
                        mapOf(
                            "vipDailyTzs" to 1_000L,
                            "vipWeeklyTzs" to 5_000L,
                            "vipMonthlyTzs" to 15_000L,
                            "vipQuarterlyTzs" to 40_000L,
                            "vipAnnualTzs" to 80_000L,
                            "merchantNumber" to OFFICIAL_MERCHANT_NUMBER,
                            "merchantName" to OFFICIAL_MERCHANT_NAME,
                            "categories" to listOf(
                                "INTERNATIONAL",
                                "AFRICA_LOCAL",
                                "TRANSFERS",
                                "PREVIEWS",
                                "REPORTS",
                                "ANALYSIS",
                                "BLOGS"
                            ),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            val updated = docRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(userId = uid, username = baseUsername, displayName = displayName)
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private fun <T> Flow<T>.handleStreamError(uid: String, operation: OperationType, path: String): Flow<T> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val toLog = firestoreError ?: (error as? Exception)
            if (toLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(toLog, operation, path)
            }
            throw error
        }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .handleStreamError(uid, OperationType.GET, "users/$uid")
        )
    }

    fun observePrivateInfo(uid: String): Flow<UserPrivateInfo?> = flow {
        emitAll(
            db.collection("users").document(uid).collection("private").document("info")
                .snapshots()
                .map { it.toObject(UserPrivateInfo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .handleStreamError(uid, OperationType.GET, "users/$uid/private/info")
        )
    }

    fun observeMembership(uid: String): Flow<Membership?> = flow {
        emitAll(
            db.collection("memberships").document(uid)
                .snapshots()
                .map { it.toObject(Membership::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .handleStreamError(uid, OperationType.GET, "memberships/$uid")
        )
    }

    fun observePublishedTips(uid: String): Flow<List<TipItem>> = flow {
        emitAll(
            db.collection("tips")
                .whereEqualTo("published", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(TipItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "tips")
        )
    }

    suspend fun getVipTipSecret(tipId: String): Result<VipTipSecret?> {
        val path = "vip_tips_secrets/$tipId"
        return try {
            requireUserId()
            val snap = db.collection("vip_tips_secrets").document(tipId).get().await()
            val secret = snap.toObject(VipTipSecret::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            Result.success(secret)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    fun observePublishedNews(uid: String): Flow<List<NewsArticle>> = flow {
        emitAll(
            db.collection("news")
                .whereEqualTo("published", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(NewsArticle::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "news")
        )
    }

    fun observeNewsComments(uid: String, newsId: String): Flow<List<ContentComment>> = flow {
        emitAll(
            db.collection("news").document(newsId).collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ContentComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .handleStreamError(uid, OperationType.LIST, "news/$newsId/comments")
        )
    }

    fun observePublishedVideos(uid: String): Flow<List<VideoItem>> = flow {
        emitAll(
            db.collection("videos")
                .whereEqualTo("published", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VideoItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "videos")
        )
    }

    fun observeVideoComments(uid: String, videoId: String): Flow<List<ContentComment>> = flow {
        emitAll(
            db.collection("videos").document(videoId).collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ContentComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .handleStreamError(uid, OperationType.LIST, "videos/$videoId/comments")
        )
    }

    fun observePublishedPodcasts(uid: String): Flow<List<PodcastEpisode>> = flow {
        emitAll(
            db.collection("podcasts")
                .whereEqualTo("published", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PodcastEpisode::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "podcasts")
        )
    }

    fun observeActiveAds(uid: String): Flow<List<Advertisement>> = flow {
        emitAll(
            db.collection("advertisements")
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Advertisement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "advertisements")
        )
    }

    fun observeAnnouncements(uid: String): Flow<List<Announcement>> = flow {
        emitAll(
            db.collection("announcements")
                .whereEqualTo("published", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "announcements")
        )
    }

    fun observePublishedFixtures(uid: String): Flow<List<FixtureItem>> = flow {
        emitAll(
            db.collection("fixtures")
                .whereEqualTo("published", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FixtureItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.kickoffEpochMs }
                }
                .handleStreamError(uid, OperationType.LIST, "fixtures")
        )
    }

    fun observeMyGroups(uid: String): Flow<List<CommunityGroup>> = flow {
        emitAll(
            db.collection("groups")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CommunityGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "groups")
        )
    }

    fun observePublicGroups(uid: String): Flow<List<CommunityGroup>> = flow {
        emitAll(
            db.collection("groups")
                .whereEqualTo("publicRoom", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CommunityGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "groups")
        )
    }

    fun observeGroupMessages(uid: String, group: CommunityGroup): Flow<List<GroupMessage>> = flow {
        val baseQuery = if (group.publicRoom) {
            db.collection("groups").document(group.id).collection("messages")
                .whereEqualTo("publicRoom", true)
        } else {
            db.collection("groups").document(group.id).collection("messages")
                .whereArrayContains("memberUids", uid)
        }
        emitAll(
            baseQuery
                .snapshots()
                .map { snap ->
                    snap.toObjects(GroupMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "groups/${group.id}/messages")
        )
    }

    fun observeNotifications(uid: String): Flow<List<NotificationItem>> = flow {
        emitAll(
            db.collection("users").document(uid).collection("notifications")
                .snapshots()
                .map { snap ->
                    snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "users/$uid/notifications")
        )
    }

    fun observeBookmarks(uid: String): Flow<List<UserBookmark>> = flow {
        emitAll(
            db.collection("users").document(uid).collection("bookmarks")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserBookmark::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "users/$uid/bookmarks")
        )
    }

    fun observeBlockedUsers(uid: String): Flow<List<BlockedUser>> = flow {
        emitAll(
            db.collection("users").document(uid).collection("blocked")
                .snapshots()
                .map { snap ->
                    snap.toObjects(BlockedUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .handleStreamError(uid, OperationType.LIST, "users/$uid/blocked")
        )
    }

    fun observeMyPayments(uid: String): Flow<List<PaymentRecord>> = flow {
        emitAll(
            db.collection("payments")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "payments")
        )
    }

    fun observeAppSettings(uid: String): Flow<AppSettings> = flow {
        emitAll(
            db.collection("app_settings").document("platform")
                .snapshots()
                .map {
                    it.toObject(AppSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: AppSettings()
                }
                .handleStreamError(uid, OperationType.GET, "app_settings/platform")
        )
    }

    // --- User Actions ---

    suspend fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        photoUrl: String,
        phone: String,
        searchable: Boolean,
        notifyTips: Boolean,
        notifyNews: Boolean,
        notifyCommunity: Boolean,
        notifyPayments: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            require(username.trim().length in 2..40) { "Username must be 2–40 characters" }
            require(displayName.trim().length in 1..80) { "Display name must be 1–80 characters" }
            db.collection("users").document(uid).update(
                mapOf(
                    "username" to username.trim(),
                    "displayName" to displayName.trim(),
                    "bio" to bio.trim().take(300),
                    "photoUrl" to photoUrl.trim().take(1000),
                    "searchable" to searchable,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("users").document(uid).collection("private").document("info").update(
                mapOf(
                    "phone" to phone.trim().take(40),
                    "notifyTips" to notifyTips,
                    "notifyNews" to notifyNews,
                    "notifyCommunity" to notifyCommunity,
                    "notifyPayments" to notifyPayments,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleBookmark(
        contentType: String,
        contentId: String,
        title: String,
        subtitle: String
    ): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val safeDocId = "${contentType}_${contentId}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        val docRef = db.collection("users").document(uid).collection("bookmarks").document(safeDocId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.delete().await()
                Result.success(false)
            } else {
                docRef.set(
                    mapOf(
                        "contentType" to contentType.take(30),
                        "contentId" to contentId.take(128),
                        "title" to title.take(160),
                        "subtitle" to subtitle.take(200),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun addNewsComment(newsId: String, authorName: String, text: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > 500) {
            return Result.failure(IllegalArgumentException("Comment must be 1–500 characters"))
        }
        val docRef = db.collection("news").document(newsId).collection("comments").document()
        return try {
            docRef.set(
                mapOf(
                    "authorId" to uid,
                    "authorName" to authorName.take(80).ifBlank { "DYFOOT Member" },
                    "text" to clean,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("news").document(newsId).update(
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun likeNews(newsId: String): Result<Unit> = try {
        requireUserId()
        db.collection("news").document(newsId).update(
            mapOf(
                "likesCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "news/$newsId")
        Result.failure(e)
    }

    suspend fun likeVideo(videoId: String): Result<Unit> = try {
        requireUserId()
        db.collection("videos").document(videoId).update(
            mapOf(
                "likesCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "videos/$videoId")
        Result.failure(e)
    }

    suspend fun addVideoComment(videoId: String, authorName: String, text: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > 500) {
            return Result.failure(IllegalArgumentException("Comment must be 1–500 characters"))
        }
        val docRef = db.collection("videos").document(videoId).collection("comments").document()
        return try {
            docRef.set(
                mapOf(
                    "authorId" to uid,
                    "authorName" to authorName.take(80).ifBlank { "DYFOOT Member" },
                    "text" to clean,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("videos").document(videoId).update(
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun submitMobileMoneyPayment(
        userDisplayName: String,
        tier: VipTier,
        amountTzs: Long,
        provider: MobileMoneyProvider,
        payerPhone: String,
        transactionRef: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanPhone = payerPhone.trim()
        val effectiveRef = transactionRef.trim().ifBlank {
            com.example.data.notifications.LiveMatchAlertEngine.generateStkPushRef(tier)
        }
        if (cleanPhone.length !in 9..20) {
            return Result.failure(IllegalArgumentException("Enter a valid Tanzania mobile number (9–20 digits)"))
        }
        if (effectiveRef.length !in 4..64) {
            return Result.failure(IllegalArgumentException("Transaction reference must be 4–64 chars"))
        }
        val payRef = db.collection("payments").document()
        return try {
            payRef.set(
                mapOf(
                    "userId" to uid,
                    "userDisplayName" to userDisplayName.take(80),
                    "planId" to tier.name,
                    "planName" to tier.label,
                    "amountTzs" to amountTzs,
                    "currency" to "TZS",
                    "provider" to provider.wireValue(),
                    "payerPhone" to cleanPhone,
                    "merchantNumber" to OFFICIAL_MERCHANT_NUMBER,
                    "transactionRef" to effectiveRef,
                    "providerRef" to "",
                    "status" to PaymentStatus.PENDING_VERIFICATION.name,
                    "verifiedBy" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            createUserNotification(
                targetUid = uid,
                title = "Payment Push Sent • ${tier.label}",
                body = "Approve ${"%,d".format(amountTzs)} TZS via ${provider.displayName} to $OFFICIAL_MERCHANT_NUMBER (Ref: $effectiveRef). VIP unlocks automatically once verified.",
                category = "PAYMENT",
                deepLinkRoute = "vip"
            )
            Result.success(effectiveRef)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, payRef.path)
            Result.failure(e)
        }
    }

    suspend fun createUserNotification(
        targetUid: String,
        title: String,
        body: String,
        category: String,
        deepLinkRoute: String = ""
    ): Result<Unit> {
        val notifRef = db.collection("users").document(targetUid).collection("notifications").document()
        return try {
            notifRef.set(
                mapOf(
                    "userId" to targetUid,
                    "title" to title.take(140),
                    "body" to body.take(500),
                    "category" to category.take(40),
                    "read" to false,
                    "deepLinkRoute" to deepLinkRoute.take(80),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, notifRef.path)
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val ref = db.collection("users").document(uid).collection("notifications").document(notificationId)
        return try {
            ref.update(mapOf("read" to true)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    // --- Community ---

    suspend fun searchUsersByUsername(queryText: String): Result<List<UserProfile>> {
        val path = "users"
        return try {
            requireUserId()
            val snap = db.collection("users")
                .whereEqualTo("searchable", true)
                .limit(50)
                .get()
                .await()
            val clean = queryText.trim().lowercase()
            val users = snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .filter {
                    clean.isEmpty() ||
                        it.username.lowercase().contains(clean) ||
                        it.displayName.lowercase().contains(clean)
                }
            Result.success(users)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun createOrOpenDirectChat(
        currentUser: UserProfile,
        targetUser: UserProfile
    ): Result<CommunityGroup> {
        val uid = currentUser.userId
        val pairIds = listOf(uid, targetUser.userId).sorted()
        val roomId = "dm_${pairIds[0].take(20)}_${pairIds[1].take(20)}"
        val docRef = db.collection("groups").document(roomId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "title" to "${targetUser.displayName} • ${currentUser.displayName}",
                        "description" to "Direct 1-to-1 chat",
                        "directMessage" to true,
                        "publicRoom" to false,
                        "createdBy" to uid,
                        "memberUids" to pairIds,
                        "lastMessage" to "Conversation started",
                        "lastSenderName" to currentUser.displayName.take(80),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            val group = docRef.get().await()
                .toObject(CommunityGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)!!
            Result.success(group)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createCommunityGroup(
        currentUser: UserProfile,
        title: String,
        description: String,
        publicRoom: Boolean
    ): Result<CommunityGroup> {
        val uid = currentUser.userId
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return Result.failure(IllegalArgumentException("Group title required"))
        val docRef = db.collection("groups").document()
        return try {
            docRef.set(
                mapOf(
                    "title" to cleanTitle.take(100),
                    "description" to description.trim().take(300),
                    "directMessage" to false,
                    "publicRoom" to publicRoom,
                    "createdBy" to uid,
                    "memberUids" to listOf(uid),
                    "lastMessage" to "Group created",
                    "lastSenderName" to currentUser.displayName.take(80),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val group = docRef.get().await()
                .toObject(CommunityGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)!!
            Result.success(group)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinPublicGroup(group: CommunityGroup): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        if (uid in group.memberUids) return Result.success(Unit)
        val docRef = db.collection("groups").document(group.id)
        return try {
            docRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun sendGroupMessage(
        group: CommunityGroup,
        senderName: String,
        text: String,
        imageUrl: String = ""
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > 1500) {
            return Result.failure(IllegalArgumentException("Message must be 1–1500 characters"))
        }
        val msgRef = db.collection("groups").document(group.id).collection("messages").document()
        return try {
            val members = (group.memberUids + uid).distinct().take(50)
            val payload = mutableMapOf<String, Any>(
                "senderId" to uid,
                "senderName" to senderName.take(80).ifBlank { "Member" },
                "text" to clean,
                "publicRoom" to group.publicRoom,
                "memberUids" to members,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (imageUrl.isNotBlank()) {
                payload["imageUrl"] = imageUrl.take(1000)
            }
            msgRef.set(payload).await()
            db.collection("groups").document(group.id).update(
                mapOf(
                    "lastMessage" to clean.take(300),
                    "lastSenderName" to senderName.take(80),
                    "memberUids" to members,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun blockUser(blockedUid: String, blockedName: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val ref = db.collection("users").document(uid).collection("blocked").document(blockedUid)
        return try {
            ref.set(
                mapOf(
                    "blockedUid" to blockedUid,
                    "blockedName" to blockedName.take(80),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(blockedUid: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val ref = db.collection("users").document(uid).collection("blocked").document(blockedUid)
        return try {
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(targetType: String, targetId: String, reason: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanReason = reason.trim()
        if (cleanReason.length < 3) return Result.failure(IllegalArgumentException("Provide a brief reason"))
        val ref = db.collection("reports").document()
        return try {
            ref.set(
                mapOf(
                    "reporterId" to uid,
                    "targetType" to targetType.take(40),
                    "targetId" to targetId.take(128),
                    "reason" to cleanReason.take(500),
                    "status" to "OPEN",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    fun observeActiveStatuses(uid: String): Flow<List<CommunityStatus>> = flow {
        val now = System.currentTimeMillis()
        emitAll(
            db.collection("statuses")
                .whereGreaterThan("expiresAtEpochMs", now - 60_000L)
                .snapshots()
                .map { snap ->
                    val cutoff = System.currentTimeMillis()
                    snap.toObjects(CommunityStatus::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.expiresAtEpochMs > cutoff }
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "statuses")
        )
    }

    suspend fun postStatus(
        authorName: String,
        authorPhotoUrl: String,
        text: String,
        themeColor: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > 240) {
            return Result.failure(IllegalArgumentException("Status must be 1–240 characters"))
        }
        val allowedTheme = if (themeColor in setOf("LIME", "MIDNIGHT", "EMERALD", "CRIMSON")) themeColor else "LIME"
        val docRef = db.collection("statuses").document()
        return try {
            val expiresAt = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24)
            docRef.set(
                mapOf(
                    "authorId" to uid,
                    "authorName" to authorName.take(80).ifBlank { "DYFOOT Fan" },
                    "authorPhotoUrl" to authorPhotoUrl.take(1000),
                    "text" to clean,
                    "themeColor" to allowedTheme,
                    "expiresAtEpochMs" to expiresAt,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteStatus(statusId: String): Result<Unit> = try {
        requireUserId()
        db.collection("statuses").document(statusId).delete().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "statuses/$statusId")
        Result.failure(e)
    }

    // --- Admin Studio Operations (Owner & Explicitly Authorized Admins Only) ---

    private suspend fun writeAuditLog(action: String, targetType: String, targetId: String, details: String) {
        val user = auth.currentUser ?: return
        try {
            db.collection("admin_logs").document().set(
                mapOf(
                    "actorId" to user.uid,
                    "actorEmail" to (user.email ?: "unknown@dyfoot"),
                    "action" to action.take(80),
                    "targetType" to targetType.take(50),
                    "targetId" to targetId.take(128),
                    "details" to details.take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
            // Audit log error should not mask primary result if already logged
        }
    }

    fun observeAdminTips(uid: String, isOwner: Boolean): Flow<List<TipItem>> = flow {
        val query = if (isOwner) db.collection("tips") else db.collection("tips").whereEqualTo("authorId", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(TipItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "tips")
        )
    }

    fun observeAdminNews(uid: String, isOwner: Boolean): Flow<List<NewsArticle>> = flow {
        val query = if (isOwner) db.collection("news") else db.collection("news").whereEqualTo("authorId", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(NewsArticle::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "news")
        )
    }

    fun observeAdminVideos(uid: String, isOwner: Boolean): Flow<List<VideoItem>> = flow {
        val query = if (isOwner) db.collection("videos") else db.collection("videos").whereEqualTo("authorId", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(VideoItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "videos")
        )
    }

    fun observeAdminPodcasts(uid: String, isOwner: Boolean): Flow<List<PodcastEpisode>> = flow {
        val query = if (isOwner) db.collection("podcasts") else db.collection("podcasts").whereEqualTo("authorId", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(PodcastEpisode::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "podcasts")
        )
    }

    fun observeAdminAds(uid: String, isOwner: Boolean): Flow<List<Advertisement>> = flow {
        val query = if (isOwner) db.collection("advertisements") else db.collection("advertisements").whereEqualTo("authorId", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(Advertisement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "advertisements")
        )
    }

    fun observeAdminAnnouncements(uid: String, isOwner: Boolean): Flow<List<Announcement>> = flow {
        val query = if (isOwner) db.collection("announcements") else db.collection("announcements").whereEqualTo("authorId", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "announcements")
        )
    }

    fun observeAdminFixtures(uid: String, isOwner: Boolean): Flow<List<FixtureItem>> = flow {
        val query = if (isOwner) db.collection("fixtures") else db.collection("fixtures").whereEqualTo("authorId", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(FixtureItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.kickoffEpochMs }
                }
                .handleStreamError(uid, OperationType.LIST, "fixtures")
        )
    }

    fun observeAllUsersForAdmin(uid: String, isOwner: Boolean): Flow<List<UserProfile>> = flow {
        val query = if (isOwner) db.collection("users") else db.collection("users").whereEqualTo("searchable", true)
        emitAll(
            query
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName }
                }
                .handleStreamError(uid, OperationType.LIST, "users")
        )
    }

    fun observeAllPaymentsForOwner(uid: String): Flow<List<PaymentRecord>> = flow {
        emitAll(
            db.collection("payments")
                .snapshots()
                .map { snap ->
                    snap.toObjects(PaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "payments")
        )
    }

    fun observeReportsForOwner(uid: String): Flow<List<ContentReport>> = flow {
        emitAll(
            db.collection("reports")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ContentReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "reports")
        )
    }

    fun observeAdminLogs(uid: String, isOwner: Boolean): Flow<List<AdminAuditLog>> = flow {
        val query = if (isOwner) db.collection("admin_logs") else db.collection("admin_logs").whereEqualTo("actorId", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(AdminAuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleStreamError(uid, OperationType.LIST, "admin_logs")
        )
    }

    suspend fun saveTip(tip: TipItem): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val isNew = tip.id.isBlank()
        val docRef = if (isNew) db.collection("tips").document() else db.collection("tips").document(tip.id)
        val isVip = tip.tier == TipTier.VIP.name
        return try {
            val publicPayload = mutableMapOf<String, Any>(
                "authorId" to (tip.authorId.ifBlank { uid }),
                "matchName" to tip.matchName.trim().take(140),
                "homeTeam" to tip.homeTeam.trim().take(80),
                "awayTeam" to tip.awayTeam.trim().take(80),
                "competition" to tip.competition.trim().take(80),
                "kickoffLabel" to tip.kickoffLabel.trim().take(60),
                "kickoffEpochMs" to tip.kickoffEpochMs,
                "tier" to tip.tier,
                "market" to tip.market.trim().take(80),
                // Protected: VIP selection/code/score/analysis stripped from public /tips doc!
                "selection" to if (isVip) "" else tip.selection.trim().take(120),
                "odds" to tip.odds.trim().take(20),
                "bookmakerName" to tip.bookmakerName.trim().take(60),
                "bookmakerCode" to if (isVip) "" else tip.bookmakerCode.trim().take(60),
                "predictedScore" to if (isVip) "" else tip.predictedScore.trim().take(20),
                "analysis" to if (isVip) "VIP slip protected. Upgrade to VIP to unlock full selection & code." else tip.analysis.trim().take(2000),
                "riskLevel" to tip.riskLevel,
                "status" to tip.status,
                "published" to tip.published,
                "featured" to tip.featured,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isNew) {
                publicPayload["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(publicPayload).await()
            } else {
                docRef.update(publicPayload).await()
            }

            if (isVip) {
                db.collection("vip_tips_secrets").document(docRef.id).set(
                    mapOf(
                        "tipId" to docRef.id,
                        "selection" to tip.selection.trim().take(120).ifBlank { "VIP Selection" },
                        "bookmakerCode" to tip.bookmakerCode.trim().take(60),
                        "predictedScore" to tip.predictedScore.trim().take(20),
                        "analysis" to tip.analysis.trim().take(2000).ifBlank { "Official DYFOOT VIP analysis." },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            writeAuditLog(if (isNew) "CREATE_TIP" else "UPDATE_TIP", "tip", docRef.id, "${tip.tier} • ${tip.matchName}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTip(tipId: String): Result<Unit> = try {
        requireUserId()
        db.collection("tips").document(tipId).delete().await()
        try {
            db.collection("vip_tips_secrets").document(tipId).delete().await()
        } catch (_: Exception) {
        }
        writeAuditLog("DELETE_TIP", "tip", tipId, "Deleted tip")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "tips/$tipId")
        Result.failure(e)
    }

    suspend fun saveNews(article: NewsArticle): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanTitleEn = article.title.trim().take(180)
        val cleanSummaryEn = article.summary.trim().take(500)
        val cleanContentEn = article.content.trim().take(10000)
        val cleanTitleSw = article.titleSw.trim().take(180)
        val cleanSummarySw = article.summarySw.trim().take(500)
        val cleanContentSw = article.contentSw.trim().take(10000)

        val canonicalTitle = cleanTitleEn.ifBlank { cleanTitleSw }
        val canonicalSummary = cleanSummaryEn.ifBlank { cleanSummarySw }.ifBlank { canonicalTitle }
        val canonicalContent = cleanContentEn.ifBlank { cleanContentSw }
        if (canonicalTitle.length < 3 || canonicalSummary.isEmpty() || canonicalContent.isEmpty()) {
            return Result.failure(IllegalArgumentException("Headline (min 3 chars), summary, and article body are required."))
        }

        val resolvedLang = when {
            article.language.equals("BILINGUAL", ignoreCase = true) ||
                (cleanTitleEn.isNotBlank() && cleanTitleSw.isNotBlank() && cleanContentEn.isNotBlank() && cleanContentSw.isNotBlank()) -> "BILINGUAL"
            cleanTitleSw.isNotBlank() && cleanTitleEn.isBlank() -> "SW"
            article.language.equals("SW", ignoreCase = true) -> "SW"
            else -> "EN"
        }

        val safeImageUrl = article.imageUrl.trim().take(1000).let { url ->
            if (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)) url else ""
        }

        val isNew = article.id.isBlank()
        val ref = if (isNew) db.collection("news").document() else db.collection("news").document(article.id)
        return try {
            val payload = mutableMapOf<String, Any>(
                "title" to canonicalTitle,
                "summary" to canonicalSummary,
                "content" to canonicalContent,
                "titleSw" to cleanTitleSw,
                "summarySw" to cleanSummarySw,
                "contentSw" to cleanContentSw,
                "language" to resolvedLang,
                "leagueTag" to article.leagueTag.trim().take(80),
                "localFootball" to article.localFootball,
                "category" to article.category.trim().take(50).ifBlank { "TANZANIA_LIGI" },
                "sourceName" to article.sourceName.trim().take(100).ifBlank { "DYFOOT Editorial" },
                "sourceUrl" to "",
                "imageUrl" to safeImageUrl,
                "published" to article.published,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isNew) {
                payload["authorId"] = article.authorId.ifBlank { uid }
                payload["likesCount"] = article.likesCount.coerceAtLeast(0L)
                payload["commentsCount"] = article.commentsCount.coerceAtLeast(0L)
                payload["createdAt"] = FieldValue.serverTimestamp()
                ref.set(payload).await()
            } else {
                ref.update(payload).await()
            }
            writeAuditLog(
                if (isNew) "CREATE_NEWS" else "UPDATE_NEWS",
                "news",
                ref.id,
                "$resolvedLang • ${canonicalTitle.take(100)}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNews(newsId: String): Result<Unit> = try {
        requireUserId()
        db.collection("news").document(newsId).delete().await()
        writeAuditLog("DELETE_NEWS", "news", newsId, "Deleted article")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "news/$newsId")
        Result.failure(e)
    }

    suspend fun saveVideo(video: VideoItem): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val isNew = video.id.isBlank()
        val ref = if (isNew) db.collection("videos").document() else db.collection("videos").document(video.id)
        return try {
            val payload = mutableMapOf<String, Any>(
                "authorId" to (video.authorId.ifBlank { uid }),
                "title" to video.title.trim().take(160),
                "caption" to video.caption.trim().take(500),
                "creatorName" to video.creatorName.trim().take(80).ifBlank { "DYFOOT TV" },
                "category" to video.category.trim().take(50).ifBlank { "Highlights" },
                "videoUrl" to video.videoUrl.trim().take(1000),
                "thumbnailUrl" to video.thumbnailUrl.trim().take(1000),
                "durationSec" to video.durationSec,
                "published" to video.published,
                "likesCount" to video.likesCount,
                "commentsCount" to video.commentsCount,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isNew) {
                payload["createdAt"] = FieldValue.serverTimestamp()
                ref.set(payload).await()
            } else {
                ref.update(payload).await()
            }
            writeAuditLog("SAVE_VIDEO", "video", ref.id, video.title.take(120))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteVideo(videoId: String): Result<Unit> = try {
        requireUserId()
        db.collection("videos").document(videoId).delete().await()
        writeAuditLog("DELETE_VIDEO", "video", videoId, "Deleted video")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "videos/$videoId")
        Result.failure(e)
    }

    suspend fun savePodcast(podcast: PodcastEpisode): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val isNew = podcast.id.isBlank()
        val ref = if (isNew) db.collection("podcasts").document() else db.collection("podcasts").document(podcast.id)
        return try {
            val payload = mutableMapOf<String, Any>(
                "authorId" to (podcast.authorId.ifBlank { uid }),
                "title" to podcast.title.trim().take(160),
                "showName" to podcast.showName.trim().take(100).ifBlank { "DYFOOT Audio" },
                "hostName" to podcast.hostName.trim().take(80).ifBlank { "DYFOOT Studio" },
                "description" to podcast.description.trim().take(2000),
                "category" to podcast.category.trim().take(50).ifBlank { "Match Talk" },
                "audioUrl" to podcast.audioUrl.trim().take(1000),
                "coverUrl" to podcast.coverUrl.trim().take(1000),
                "durationSec" to podcast.durationSec,
                "published" to podcast.published,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isNew) {
                payload["createdAt"] = FieldValue.serverTimestamp()
                ref.set(payload).await()
            } else {
                ref.update(payload).await()
            }
            writeAuditLog("SAVE_PODCAST", "podcast", ref.id, podcast.title.take(120))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deletePodcast(podcastId: String): Result<Unit> = try {
        requireUserId()
        db.collection("podcasts").document(podcastId).delete().await()
        writeAuditLog("DELETE_PODCAST", "podcast", podcastId, "Deleted podcast")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "podcasts/$podcastId")
        Result.failure(e)
    }

    fun newAdvertisementId(): String = db.collection("advertisements").document().id

    suspend fun uploadBannerImage(
        adId: String,
        image: BannerImageSelection,
        onProgress: (Float) -> Unit = {}
    ): Result<UploadedBannerAsset> {
        return try {
            requireAdminAuthorized()
            val cleanAdId = adId.replace(Regex("[^a-zA-Z0-9_-]"), "").take(128)
            require(cleanAdId.isNotBlank()) { "Invalid advertisement ID for image upload." }
            require(image.optimizedBytes.isNotEmpty() && image.optimizedBytes.size.toLong() <= BannerImageOptimizer.MAX_BANNER_FILE_BYTES) {
                "Banner image exceeds the 5 MB upload limit."
            }

            val storagePath = "advertisements/$cleanAdId/banner_${System.currentTimeMillis()}.${image.optimizedExtension}"
            val storageRef = Firebase.storage.reference.child(storagePath)
            val metadata = StorageMetadata.Builder()
                .setContentType(image.optimizedMimeType)
                .setCacheControl("public,max-age=86400")
                .build()

            val uploadTask = storageRef.putBytes(image.optimizedBytes, metadata)
            uploadTask.addOnProgressListener { snapshot ->
                val total = snapshot.totalByteCount
                if (total > 0L) {
                    val ratio = (snapshot.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 0.98f)
                    onProgress(ratio)
                }
            }
            uploadTask.await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            onProgress(1f)
            Result.success(UploadedBannerAsset(downloadUrl = downloadUrl, storagePath = storagePath))
        } catch (e: CancellationException) {
            throw e
        } catch (e: StorageException) {
            val friendly = when (e.errorCode) {
                StorageException.ERROR_BUCKET_NOT_FOUND,
                StorageException.ERROR_PROJECT_NOT_FOUND ->
                    "Firebase Cloud Storage is not enabled for this project yet. Open Firebase Console → Storage → Get Started."
                StorageException.ERROR_NOT_AUTHORIZED,
                StorageException.ERROR_NOT_AUTHENTICATED ->
                    "Cloud Storage denied permission. Verify signed-in owner account and Storage security rules."
                StorageException.ERROR_QUOTA_EXCEEDED ->
                    "Firebase Cloud Storage quota exceeded."
                else -> e.localizedMessage ?: "Cloud Storage upload failed."
            }
            Result.failure(IllegalStateException(friendly, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBannerImageAsset(storagePath: String): Result<Unit> {
        val cleanPath = storagePath.trim()
        if (cleanPath.isBlank()) return Result.success(Unit)
        return try {
            requireAdminAuthorized()
            Firebase.storage.reference.child(cleanPath).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun saveAdvertisement(ad: Advertisement, isNewOverride: Boolean? = null): Result<String> {
        return try {
            val uid = requireAdminAuthorized()
            val cleanTitle = ad.title.trim().take(120)
            val cleanPromo = ad.promoText.trim().take(300)
            require(cleanTitle.length >= 2) { "Banner title must be at least 2 characters." }
            require(cleanPromo.isNotEmpty()) { "Promo copy cannot be empty." }

            val cleanDest = BannerImageOptimizer.sanitizeBannerDestination(ad.destinationUrl).getOrThrow()
            val cleanPlacement = AdPlacement.safeFrom(ad.placement).wireValue
            val cleanImageUrl = ad.imageUrl.trim().take(2000)
            val cleanStoragePath = ad.storagePath.trim().take(500)

            val isNew = isNewOverride ?: ad.id.isBlank()
            val ref = if (ad.id.isBlank()) {
                db.collection("advertisements").document()
            } else {
                db.collection("advertisements").document(ad.id)
            }

            val payload = mutableMapOf<String, Any>(
                "authorId" to (ad.authorId.ifBlank { uid }),
                "title" to cleanTitle,
                "promoText" to cleanPromo,
                "imageUrl" to cleanImageUrl,
                "storagePath" to cleanStoragePath,
                "destinationUrl" to cleanDest,
                "placement" to cleanPlacement,
                "active" to ad.active,
                "startEpochMs" to ad.startEpochMs,
                "endEpochMs" to ad.endEpochMs,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isNew) {
                payload["createdAt"] = FieldValue.serverTimestamp()
                ref.set(payload).await()
            } else {
                ref.update(payload).await()
            }
            writeAuditLog("SAVE_AD", "advertisement", ref.id, "$cleanTitle [$cleanPlacement] active=${ad.active}")
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val path = if (ad.id.isBlank()) "advertisements" else "advertisements/${ad.id}"
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAdvertisement(adId: String, storagePath: String = ""): Result<Unit> = try {
        requireAdminAuthorized()
        db.collection("advertisements").document(adId).delete().await()
        if (storagePath.isNotBlank()) {
            deleteBannerImageAsset(storagePath)
        }
        writeAuditLog("DELETE_AD", "advertisement", adId, "Deleted ad")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "advertisements/$adId")
        Result.failure(e)
    }

    suspend fun saveAnnouncement(announcement: Announcement): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val isNew = announcement.id.isBlank()
        val ref = if (isNew) db.collection("announcements").document() else db.collection("announcements").document(announcement.id)
        return try {
            val payload = mutableMapOf<String, Any>(
                "authorId" to (announcement.authorId.ifBlank { uid }),
                "title" to announcement.title.trim().take(140),
                "message" to announcement.message.trim().take(1000),
                "priority" to announcement.priority,
                "published" to announcement.published,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isNew) {
                payload["createdAt"] = FieldValue.serverTimestamp()
                ref.set(payload).await()
            } else {
                ref.update(payload).await()
            }
            writeAuditLog("SAVE_ANNOUNCEMENT", "announcement", ref.id, announcement.title.take(120))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> = try {
        requireUserId()
        db.collection("announcements").document(announcementId).delete().await()
        writeAuditLog("DELETE_ANNOUNCEMENT", "announcement", announcementId, "Deleted announcement")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "announcements/$announcementId")
        Result.failure(e)
    }

    suspend fun saveFixture(fixture: FixtureItem): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val isNew = fixture.id.isBlank()
        val ref = if (isNew) db.collection("fixtures").document() else db.collection("fixtures").document(fixture.id)
        return try {
            val payload = mutableMapOf<String, Any>(
                "authorId" to (fixture.authorId.ifBlank { uid }),
                "homeTeam" to fixture.homeTeam.trim().take(80),
                "awayTeam" to fixture.awayTeam.trim().take(80),
                "competition" to fixture.competition.trim().take(80),
                "homeScore" to fixture.homeScore,
                "awayScore" to fixture.awayScore,
                "status" to fixture.status,
                "minuteLabel" to fixture.minuteLabel.trim().take(30),
                "kickoffEpochMs" to fixture.kickoffEpochMs,
                "venue" to fixture.venue.trim().take(120),
                "sourceLabel" to fixture.sourceLabel.trim().take(100).ifBlank { "DYFOOT Official" },
                "streamScheduled" to fixture.streamScheduled,
                "streamStatus" to fixture.streamStatus,
                "eventsSummary" to fixture.eventsSummary.trim().take(1000),
                "published" to fixture.published,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isNew) {
                payload["createdAt"] = FieldValue.serverTimestamp()
                ref.set(payload).await()
            } else {
                ref.update(payload).await()
            }
            writeAuditLog("SAVE_FIXTURE", "fixture", ref.id, "${fixture.homeTeam} vs ${fixture.awayTeam}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteFixture(fixtureId: String): Result<Unit> = try {
        requireUserId()
        db.collection("fixtures").document(fixtureId).delete().await()
        writeAuditLog("DELETE_FIXTURE", "fixture", fixtureId, "Deleted fixture")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "fixtures/$fixtureId")
        Result.failure(e)
    }

    suspend fun verifyPaymentAndActivateVip(
        payment: PaymentRecord,
        approve: Boolean
    ): Result<Unit> {
        val actorUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val payRef = db.collection("payments").document(payment.id)
        return try {
            val newStatus = if (approve) PaymentStatus.VERIFIED_PAID.name else PaymentStatus.FAILED.name
            payRef.update(
                mapOf(
                    "status" to newStatus,
                    "verifiedBy" to actorUid,
                    "providerRef" to if (approve) "VERIFIED_${System.currentTimeMillis()}" else "REJECTED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (approve) {
                val tier = VipTier.safeFrom(payment.planId).let { if (it == VipTier.NONE) VipTier.MONTHLY else it }
                val expiryMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(tier.durationDays.toLong())
                val expiryTs = Timestamp(Date(expiryMs))

                db.collection("memberships").document(payment.userId).set(
                    mapOf(
                        "userId" to payment.userId,
                        "planId" to tier.name,
                        "planName" to tier.label,
                        "status" to "ACTIVE",
                        "expiresAt" to expiryTs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                val userSnap = db.collection("users").document(payment.userId).get().await()
                val existingRole = UserRole.safeFrom(userSnap.getString("role"))
                val nextRole = if (existingRole == UserRole.OWNER || existingRole == UserRole.ADMIN) {
                    existingRole.name
                } else {
                    UserRole.VIP_MEMBER.name
                }
                db.collection("users").document(payment.userId).update(
                    mapOf(
                        "role" to nextRole,
                        "vipTier" to tier.name,
                        "vipExpiresAt" to expiryMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                createUserNotification(
                    targetUid = payment.userId,
                    title = "VIP Membership Activated!",
                    body = "Your ${tier.label} payment (${payment.amountTzs} TZS) is verified. Enjoy full VIP Tips access.",
                    category = "VIP",
                    deepLinkRoute = "tips"
                )
            } else {
                createUserNotification(
                    targetUid = payment.userId,
                    title = "Payment Verification Failed",
                    body = "Reference ${payment.transactionRef} could not be verified against $OFFICIAL_MERCHANT_NUMBER.",
                    category = "PAYMENT",
                    deepLinkRoute = "vip"
                )
            }
            writeAuditLog(
                if (approve) "APPROVE_PAYMENT" else "REJECT_PAYMENT",
                "payment",
                payment.id,
                "User=${payment.userId} Amount=${payment.amountTzs} TZS"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, payRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateUserRoleAndSuspension(
        targetUser: UserProfile,
        newRole: UserRole,
        suspended: Boolean
    ): Result<Unit> {
        val actorUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val userRef = db.collection("users").document(targetUser.userId)
        return try {
            val isPromotingAdmin = newRole == UserRole.ADMIN
            if (isPromotingAdmin) {
                val privEmail = try {
                    db.collection("users").document(targetUser.userId)
                        .collection("private").document("info").get().await().getString("email").orEmpty()
                } catch (_: Exception) {
                    ""
                }
                db.collection("admins").document(targetUser.userId).set(
                    mapOf(
                        "userId" to targetUser.userId,
                        "email" to privEmail.ifBlank { "${targetUser.username}@dyfoot" },
                        "assignedBy" to actorUid,
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else if (targetUser.parsedRole() == UserRole.ADMIN && newRole != UserRole.ADMIN) {
                db.collection("admins").document(targetUser.userId).delete().await()
            }

            if (newRole == UserRole.VIP_MEMBER) {
                val expiryMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)
                db.collection("memberships").document(targetUser.userId).set(
                    mapOf(
                        "userId" to targetUser.userId,
                        "planId" to VipTier.MONTHLY.name,
                        "planName" to "VIP Monthly (Admin Grant)",
                        "status" to "ACTIVE",
                        "expiresAt" to Timestamp(Date(expiryMs)),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                userRef.update(
                    mapOf(
                        "role" to newRole.name,
                        "vipTier" to VipTier.MONTHLY.name,
                        "vipExpiresAt" to expiryMs,
                        "suspended" to suspended,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                userRef.update(
                    mapOf(
                        "role" to newRole.name,
                        "suspended" to suspended,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            writeAuditLog(
                "UPDATE_USER_ACCESS",
                "user",
                targetUser.userId,
                "role=${newRole.name} suspended=$suspended"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun resolveReport(reportId: String, status: String): Result<Unit> = try {
        requireUserId()
        db.collection("reports").document(reportId).update(mapOf("status" to status)).await()
        writeAuditLog("RESOLVE_REPORT", "report", reportId, "status=$status")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "reports/$reportId")
        Result.failure(e)
    }

    suspend fun saveAppSettings(settings: AppSettings): Result<Unit> = try {
        requireUserId()
        db.collection("app_settings").document("platform").set(
            mapOf(
                "vipDailyTzs" to settings.vipDailyTzs,
                "vipWeeklyTzs" to settings.vipWeeklyTzs,
                "vipMonthlyTzs" to settings.vipMonthlyTzs,
                "vipQuarterlyTzs" to settings.vipQuarterlyTzs,
                "vipAnnualTzs" to settings.vipAnnualTzs,
                "merchantNumber" to settings.merchantNumber.ifBlank { OFFICIAL_MERCHANT_NUMBER },
                "merchantName" to settings.merchantName.ifBlank { OFFICIAL_MERCHANT_NAME },
                "categories" to settings.categories.take(20),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        writeAuditLog("UPDATE_SETTINGS", "app_settings", "platform", "Updated pricing/categories")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "app_settings/platform")
        Result.failure(e)
    }

    /**
     * Owner-only optional starter sample pack so the owner can test/seed a fresh Firestore DB
     * with clearly labeled [SAMPLE] items without fabricating live production data.
     */
    suspend fun seedStarterSampleContent(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            saveTip(
                TipItem(
                    authorId = uid,
                    matchName = "[SAMPLE] Arsenal vs Liverpool",
                    homeTeam = "Arsenal",
                    awayTeam = "Liverpool",
                    competition = "Premier League",
                    kickoffLabel = "Sat 17:30 EAT",
                    kickoffEpochMs = System.currentTimeMillis() + 3600_000L,
                    tier = TipTier.FREE.name,
                    market = "Over/Under 2.5 Goals",
                    selection = "Over 2.5 Goals",
                    odds = "1.78",
                    bookmakerName = "SportPesa",
                    bookmakerCode = "SP-SAMPLE-11",
                    predictedScore = "2-2",
                    analysis = "[SAMPLE] Both sides average 2.1 xG per match in recent home/away fixtures.",
                    riskLevel = "LOW",
                    status = "PENDING",
                    published = true,
                    featured = true
                )
            )
            saveTip(
                TipItem(
                    authorId = uid,
                    matchName = "[SAMPLE] Simba SC vs Young Africans",
                    homeTeam = "Simba SC",
                    awayTeam = "Young Africans",
                    competition = "NBC Premier League",
                    kickoffLabel = "Sun 16:00 EAT",
                    kickoffEpochMs = System.currentTimeMillis() + 7200_000L,
                    tier = TipTier.VIP.name,
                    market = "Asian Handicap & BTTS",
                    selection = "BTTS Yes & Over 1.5",
                    odds = "2.25",
                    bookmakerName = "BetPawa TZ",
                    bookmakerCode = "BP-VIP-8841",
                    predictedScore = "2-1",
                    analysis = "[SAMPLE VIP] Tactical breakdown of Dar derby midfield transitions and set-piece threats.",
                    riskLevel = "MEDIUM",
                    status = "PENDING",
                    published = true,
                    featured = true
                )
            )
            saveFixture(
                FixtureItem(
                    authorId = uid,
                    homeTeam = "[SAMPLE] Young Africans",
                    awayTeam = "Azam FC",
                    competition = "NBC Premier League",
                    homeScore = 1,
                    awayScore = 1,
                    status = "UPCOMING",
                    minuteLabel = "16:00 EAT",
                    kickoffEpochMs = System.currentTimeMillis() + 5400_000L,
                    venue = "Benjamin Mkapa Stadium",
                    sourceLabel = "DYFOOT Sample Schedule",
                    streamScheduled = true,
                    streamStatus = "COMING_SOON",
                    eventsSummary = "[SAMPLE] Pre-match lineups posted 60 mins before kickoff.",
                    published = true
                )
            )
            saveAdvertisement(
                Advertisement(
                    authorId = uid,
                    title = "DYFOOT VIP Tanzania Pass",
                    promoText = "Unlock protected VIP slips via M-Pesa, Tigo Pesa & Airtel Money (0706499813).",
                    destinationUrl = "dyfoot://vip",
                    placement = "HOME_TOP",
                    active = true
                )
            )
            saveAnnouncement(
                Announcement(
                    authorId = uid,
                    title = "Welcome to DYFOOT V2",
                    message = "Official DYFOOT Sports Platform is live. Check Sport Hub for live RSS news, Watch highlights & Podcasts.",
                    priority = "URGENT",
                    published = true
                )
            )
            saveVideo(
                VideoItem(
                    authorId = uid,
                    title = "[SAMPLE] Matchday Tactical Breakdown",
                    caption = "How high-pressing traps decide big European & African fixtures. #DYFOOT #Tactics",
                    creatorName = "DYFOOT Studio",
                    category = "Tactics",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    durationSec = 15L,
                    published = true
                )
            )
            savePodcast(
                PodcastEpisode(
                    authorId = uid,
                    title = "[SAMPLE] East Africa & Europe Weekend Preview",
                    showName = "DYFOOT Pitchside Podcast",
                    hostName = "DYFOOT Editorial Desk",
                    description = "Sample audio episode demonstrating Media3 playback, seek controls, and episode tracking.",
                    category = "Match Talk",
                    audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    durationSec = 372L,
                    published = true
                )
            )
            val currentProfile = db.collection("users").document(uid).get().await()
                .toObject(UserProfile::class.java) ?: UserProfile(userId = uid, displayName = "Owner")
            createCommunityGroup(
                currentUser = currentProfile,
                title = "DYFOOT Global Matchday Lounge",
                description = "Public football discussion room for match reactions & lineups.",
                publicRoom = true
            )
            writeAuditLog("SEED_SAMPLE_DATA", "platform", "sample_pack", "Seeded clearly labeled [SAMPLE] content")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncFcmDeviceRegistration(
        fcmToken: String,
        favoriteMatchIds: Set<String>,
        favoriteTopics: List<String>,
        language: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanToken = fcmToken.trim().take(512)
        if (cleanToken.length < 8) {
            return Result.failure(IllegalArgumentException("Invalid FCM registration token"))
        }
        val docRef = db.collection("users").document(uid)
            .collection("fcm_tokens").document("android_primary")
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "token" to cleanToken,
                    "favoriteMatchIds" to favoriteMatchIds.take(100),
                    "favoriteTopics" to favoriteTopics.take(100),
                    "platform" to "ANDROID",
                    "language" to if (language.uppercase() in listOf("SWAHILI", "SW")) "SWAHILI" else "ENGLISH",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
