package com.example.data.notifications

import android.content.Context
import android.util.Log
import com.example.data.local.UserPreferencesDataStore
import com.example.data.repository.DyfootRepository
import com.example.ui.i18n.AppLanguage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object DyfootFcmManager {
    private const val TAG = "DyfootFCM"

    /**
     * Converts an arbitrary fixture ID (e.g. "espn_PL_704123") into a valid FCM topic name.
     */
    fun topicForMatch(matchId: String): String {
        val sanitized = matchId.trim()
            .replace(Regex("[^a-zA-Z0-9\\-_.~%]"), "_")
            .take(80)
            .ifBlank { "general" }
        return "dyfoot_match_$sanitized"
    }

    /**
     * Formats incoming FCM remote payload fields for Goal Events, Final Scores, Kickoff, and Red Cards.
     * Returns null if a matchId is specified and is NOT in the user's favorited match IDs.
     */
    fun buildAlertFromPayload(
        data: Map<String, String>,
        fallbackTitle: String?,
        fallbackBody: String?,
        favoriteMatchIds: Set<String>,
        isSwahili: Boolean
    ): Pair<String, String>? {
        val matchId = data["matchId"].orEmpty().trim()
        if (matchId.isNotBlank() && matchId !in favoriteMatchIds) {
            return null
        }

        val eventType = data["eventType"].orEmpty().uppercase()
        val homeTeam = data["homeTeam"].orEmpty().ifBlank { "Home" }
        val awayTeam = data["awayTeam"].orEmpty().ifBlank { "Away" }
        val homeScore = data["homeScore"].orEmpty().ifBlank { "0" }
        val awayScore = data["awayScore"].orEmpty().ifBlank { "0" }
        val minuteLabel = data["minuteLabel"].orEmpty()
        val competition = data["competition"].orEmpty()
        val scorer = data["scorer"].orEmpty()

        val customTitle = data["title"].orEmpty().ifBlank { fallbackTitle.orEmpty() }
        val customBody = data["body"].orEmpty().ifBlank { fallbackBody.orEmpty() }

        val title = when {
            customTitle.isNotBlank() -> customTitle
            eventType == "GOAL" -> if (isSwahili) {
                "GOLI! $homeTeam $homeScore - $awayScore $awayTeam"
            } else {
                "GOAL! $homeTeam $homeScore - $awayScore $awayTeam"
            }
            eventType == "FINAL_SCORE" || eventType == "FULL_TIME" -> if (isSwahili) {
                "MATOKEO YA MWISHO (FT): $homeTeam $homeScore - $awayScore $awayTeam"
            } else {
                "FINAL SCORE (FT): $homeTeam $homeScore - $awayScore $awayTeam"
            }
            eventType == "RED_CARD" -> if (isSwahili) {
                "KADI NYEKUNDU: $homeTeam $homeScore - $awayScore $awayTeam"
            } else {
                "RED CARD: $homeTeam $homeScore - $awayScore $awayTeam"
            }
            eventType == "KICKOFF" -> if (isSwahili) {
                "KIPENGA KIMEANZA: $homeTeam vs $awayTeam"
            } else {
                "KICKOFF LIVE: $homeTeam vs $awayTeam"
            }
            else -> "$homeTeam $homeScore - $awayScore $awayTeam"
        }

        val body = when {
            customBody.isNotBlank() -> customBody
            eventType == "GOAL" -> buildString {
                if (scorer.isNotBlank()) append("$scorer ")
                if (minuteLabel.isNotBlank()) append("($minuteLabel) ")
                if (competition.isNotBlank()) append("• $competition")
                if (isEmpty()) {
                    append(if (isSwahili) "Goli limefungwa kwenye mechi yako pendwa!" else "Goal scored in your favourite match!")
                }
            }.trim()
            eventType == "FINAL_SCORE" || eventType == "FULL_TIME" -> buildString {
                append(if (isSwahili) "Mechi imeisha rasmi" else "Match ended")
                if (competition.isNotBlank()) append(" • $competition")
            }
            else -> competition.ifBlank {
                if (isSwahili) "Taarifa ya mechi yako pendwa" else "Favourite match live update"
            }
        }

        return title to body
    }

    suspend fun fetchAndStoreToken(prefs: UserPreferencesDataStore): String {
        val token = runCatching { Firebase.messaging.token.await() }.getOrElse { "" }
        if (token.isNotBlank()) {
            prefs.saveFcmToken(token)
        }
        return token.ifBlank { prefs.fcmToken.first() }
    }

    suspend fun syncAllFavoriteSubscriptions(
        prefs: UserPreferencesDataStore,
        repository: DyfootRepository,
        favoriteMatchIds: Set<String>,
        language: AppLanguage
    ): String {
        val token = fetchAndStoreToken(prefs)
        val messaging = runCatching { Firebase.messaging }.getOrNull()
        favoriteMatchIds.forEach { matchId ->
            runCatching { messaging?.subscribeToTopic(topicForMatch(matchId))?.await() }
        }
        if (token.isNotBlank()) {
            repository.syncFcmDeviceRegistration(
                fcmToken = token,
                favoriteMatchIds = favoriteMatchIds,
                favoriteTopics = favoriteMatchIds.map { topicForMatch(it) },
                language = language.name
            )
        }
        return token
    }

    suspend fun updateMatchFavoriteSubscription(
        prefs: UserPreferencesDataStore,
        repository: DyfootRepository,
        matchId: String,
        isFavorite: Boolean,
        updatedFavorites: Set<String>,
        language: AppLanguage
    ) {
        val topic = topicForMatch(matchId)
        val messaging = runCatching { Firebase.messaging }.getOrNull()
        runCatching {
            if (isFavorite) {
                messaging?.subscribeToTopic(topic)?.await()
            } else {
                messaging?.unsubscribeFromTopic(topic)?.await()
            }
        }.onFailure {
            Log.w(TAG, "FCM topic sync skipped for $topic: ${it.localizedMessage}")
        }

        val token = fetchAndStoreToken(prefs)
        if (token.isNotBlank()) {
            repository.syncFcmDeviceRegistration(
                fcmToken = token,
                favoriteMatchIds = updatedFavorites,
                favoriteTopics = updatedFavorites.map { topicForMatch(it) },
                language = language.name
            )
        }
    }
}

class DyfootFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val appContext = applicationContext
        serviceScope.launch {
            val prefs = UserPreferencesDataStore(appContext)
            prefs.saveFcmToken(token)

            val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
            if (!uid.isNullOrBlank()) {
                val favs = prefs.matchReminders.first()
                val lang = prefs.appLanguage.first()
                val repo = DyfootRepository(appContext)
                favs.forEach { matchId ->
                    runCatching {
                        Firebase.messaging.subscribeToTopic(DyfootFcmManager.topicForMatch(matchId)).await()
                    }
                }
                repo.syncFcmDeviceRegistration(
                    fcmToken = token,
                    favoriteMatchIds = favs,
                    favoriteTopics = favs.map { DyfootFcmManager.topicForMatch(it) },
                    language = lang.name
                )
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val appContext = applicationContext
        serviceScope.launch {
            val prefs = UserPreferencesDataStore(appContext)
            val favs = prefs.matchReminders.first()
            val lang = prefs.appLanguage.first()
            val isSw = lang == AppLanguage.SWAHILI

            val alert = DyfootFcmManager.buildAlertFromPayload(
                data = remoteMessage.data,
                fallbackTitle = remoteMessage.notification?.title,
                fallbackBody = remoteMessage.notification?.body,
                favoriteMatchIds = favs,
                isSwahili = isSw
            ) ?: return@launch

            val matchId = remoteMessage.data["matchId"].orEmpty().ifBlank { "fcm_live_${System.currentTimeMillis()}" }
            val helper = DyfootNotificationHelper(appContext)
            helper.notifyFavoriteMatchUpdate(
                matchId = matchId,
                title = alert.first,
                body = alert.second
            )

            val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
            if (!uid.isNullOrBlank()) {
                DyfootRepository(appContext).createUserNotification(
                    targetUid = uid,
                    title = alert.first,
                    body = alert.second,
                    category = "LIVE_MATCH",
                    deepLinkRoute = "live_scores"
                )
            }
        }
    }
}
