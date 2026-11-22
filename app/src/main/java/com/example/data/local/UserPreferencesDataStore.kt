package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dyfootPrefs by preferencesDataStore(name = "dyfoot_prefs")

class UserPreferencesDataStore(private val context: Context) {

    private val recentlyPlayedKey = stringPreferencesKey("recently_played_podcasts")
    private val appLanguageKey = stringPreferencesKey("app_language_code")
    private val matchRemindersKey = stringSetPreferencesKey("match_reminder_ids")
    private val kickoffAlarmIdsKey = stringSetPreferencesKey("match_kickoff_alarm_ids")
    private val matchPredictionsKey = stringPreferencesKey("match_predictions_map")
    private val fcmTokenKey = stringPreferencesKey("fcm_registration_token")

    val recentlyPlayedPodcastIds: Flow<List<String>> = context.dyfootPrefs.data.map { prefs ->
        prefs[recentlyPlayedKey]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    val appLanguage: Flow<AppLanguage> = context.dyfootPrefs.data.map { prefs ->
        AppLanguage.fromCode(prefs[appLanguageKey])
    }

    val matchReminders: Flow<Set<String>> = context.dyfootPrefs.data.map { prefs ->
        prefs[matchRemindersKey] ?: emptySet()
    }

    val kickoffAlarmIds: Flow<Set<String>> = context.dyfootPrefs.data.map { prefs ->
        prefs[kickoffAlarmIdsKey] ?: emptySet()
    }

    val matchPredictions: Flow<Map<String, String>> = context.dyfootPrefs.data.map { prefs ->
        val raw = prefs[matchPredictionsKey].orEmpty()
        if (raw.isBlank()) emptyMap()
        else raw.split(";")
            .mapNotNull { entry ->
                val parts = entry.split("=")
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0] to parts[1]
                } else null
            }
            .toMap()
    }

    val fcmToken: Flow<String> = context.dyfootPrefs.data.map { prefs ->
        prefs[fcmTokenKey].orEmpty()
    }

    suspend fun recordPodcastPlayed(podcastId: String) {
        if (podcastId.isBlank()) return
        context.dyfootPrefs.edit { prefs ->
            val current = prefs[recentlyPlayedKey]
                ?.split(",")
                ?.filter { it.isNotBlank() && it != podcastId }
                ?: emptyList()
            val updated = (listOf(podcastId) + current).take(10)
            prefs[recentlyPlayedKey] = updated.joinToString(",")
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.dyfootPrefs.edit { prefs ->
            prefs[appLanguageKey] = language.code
        }
    }

    suspend fun toggleMatchReminder(fixtureId: String): Boolean {
        var nowEnabled = false
        context.dyfootPrefs.edit { prefs ->
            val current = prefs[matchRemindersKey]?.toMutableSet() ?: mutableSetOf()
            nowEnabled = if (current.contains(fixtureId)) {
                current.remove(fixtureId)
                false
            } else {
                current.add(fixtureId)
                true
            }
            prefs[matchRemindersKey] = current
        }
        return nowEnabled
    }

    suspend fun toggleKickoffAlarm(fixtureId: String): Boolean {
        var nowEnabled = false
        context.dyfootPrefs.edit { prefs ->
            val current = prefs[kickoffAlarmIdsKey]?.toMutableSet() ?: mutableSetOf()
            nowEnabled = if (current.contains(fixtureId)) {
                current.remove(fixtureId)
                false
            } else {
                current.add(fixtureId)
                true
            }
            prefs[kickoffAlarmIdsKey] = current
        }
        return nowEnabled
    }

    suspend fun setMatchPrediction(fixtureId: String, pick: String) {
        if (fixtureId.isBlank()) return
        context.dyfootPrefs.edit { prefs ->
            val map = prefs[matchPredictionsKey].orEmpty()
                .split(";")
                .mapNotNull { entry ->
                    val parts = entry.split("=")
                    if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
                }
                .toMap()
                .toMutableMap()
            map[fixtureId] = pick
            // Keep most recent 80 predictions
            val trimmed = map.entries.toList().takeLast(80).associate { it.key to it.value }
            prefs[matchPredictionsKey] = trimmed.entries.joinToString(";") { "${it.key}=${it.value}" }
        }
    }

    suspend fun saveFcmToken(token: String) {
        if (token.isBlank()) return
        context.dyfootPrefs.edit { prefs ->
            prefs[fcmTokenKey] = token.trim()
        }
    }
}
