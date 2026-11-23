package com.example.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.model.FixtureItem
import kotlin.math.absoluteValue

class MatchReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val matchId = intent.getStringExtra(EXTRA_MATCH_ID).orEmpty()
        val homeTeam = intent.getStringExtra(EXTRA_HOME_TEAM).orEmpty()
        val awayTeam = intent.getStringExtra(EXTRA_AWAY_TEAM).orEmpty()
        val competition = intent.getStringExtra(EXTRA_COMPETITION).orEmpty()
        val isSwahili = intent.getBooleanExtra(EXTRA_IS_SWAHILI, false)
        val leadMinutes = intent.getIntExtra(EXTRA_LEAD_MINUTES, 15)

        if (matchId.isBlank() || homeTeam.isBlank()) return
        val helper = DyfootNotificationHelper(context.applicationContext)
        helper.notifyMatchKickoffReminder(
            matchId = matchId,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            competition = competition,
            leadMinutes = leadMinutes,
            isSwahili = isSwahili
        )
    }

    companion object {
        const val EXTRA_MATCH_ID = "extra_match_id"
        const val EXTRA_HOME_TEAM = "extra_home_team"
        const val EXTRA_AWAY_TEAM = "extra_away_team"
        const val EXTRA_COMPETITION = "extra_competition"
        const val EXTRA_IS_SWAHILI = "extra_is_swahili"
        const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"
    }
}

object MatchReminderScheduler {
    private const val TAG = "MatchReminderScheduler"

    fun computeTriggerTimeMs(kickoffEpochMs: Long, leadMinutes: Int = 15, nowMs: Long = System.currentTimeMillis()): Long {
        if (kickoffEpochMs <= 0L) return nowMs + 5_000L
        val target = kickoffEpochMs - (leadMinutes * 60_000L)
        return when {
            target > nowMs + 10_000L -> target
            kickoffEpochMs > nowMs + 10_000L -> kickoffEpochMs
            else -> nowMs + 5_000L
        }
    }

    fun scheduleReminder(
        context: Context,
        fixture: FixtureItem,
        leadMinutes: Int = 15,
        isSwahili: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val triggerAt = computeTriggerTimeMs(fixture.kickoffEpochMs, leadMinutes, now)
        val home = fixture.homeTeam.trim()
        val away = fixture.awayTeam.trim()

        val intent = Intent(context, MatchReminderReceiver::class.java).apply {
            putExtra(MatchReminderReceiver.EXTRA_MATCH_ID, fixture.id)
            putExtra(MatchReminderReceiver.EXTRA_HOME_TEAM, home)
            putExtra(MatchReminderReceiver.EXTRA_AWAY_TEAM, away)
            putExtra(MatchReminderReceiver.EXTRA_COMPETITION, fixture.competition)
            putExtra(MatchReminderReceiver.EXTRA_IS_SWAHILI, isSwahili)
            putExtra(MatchReminderReceiver.EXTRA_LEAD_MINUTES, leadMinutes)
        }
        val requestCode = fixture.id.hashCode().absoluteValue
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager?.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }.onFailure {
            Log.w(TAG, "Alarm schedule skipped: ${it.localizedMessage}")
        }
    }

    fun cancelReminder(context: Context, fixtureId: String) {
        val intent = Intent(context, MatchReminderReceiver::class.java)
        val requestCode = fixtureId.hashCode().absoluteValue
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(pending)
            pending.cancel()
        }
    }
}
