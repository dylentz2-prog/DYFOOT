package com.example.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.FixtureItem
import com.example.data.model.FixtureStatus
import com.example.data.model.MobileMoneyProvider
import com.example.data.model.VipTier
import kotlin.math.absoluteValue

object LiveMatchAlertEngine {

    fun ussdCodeFor(provider: MobileMoneyProvider): String = when (provider) {
        MobileMoneyProvider.M_PESA -> "*150*00#"
        MobileMoneyProvider.TIGO_PESA -> "*150*01#"
        MobileMoneyProvider.AIRTEL_MONEY -> "*150*60#"
        MobileMoneyProvider.HALOPESA -> "*150*88#"
    }

    fun generateStkPushRef(tier: VipTier, nowMs: Long = System.currentTimeMillis()): String {
        val code = when (tier) {
            VipTier.DAILY -> "RCK"
            VipTier.WEEKLY -> "STR"
            VipTier.MONTHLY -> "PRO"
            VipTier.ANNUAL -> "LGD"
            else -> "VIP"
        }
        val suffix = (nowMs % 1_000_000L).toString().padStart(6, '0')
        return "PUSH-$code-$suffix"
    }

    /**
     * Compares an updated fixture snapshot against a prior snapshot for favorited matches
     * and returns a notification `(title, body)` if a notable live event happened.
     */
    fun detectFavoriteMatchEvents(
        previous: FixtureItem,
        current: FixtureItem,
        isSwahili: Boolean
    ): Pair<String, String>? {
        val home = current.homeTeam.replace("[SAMPLE]", "").trim()
        val away = current.awayTeam.replace("[SAMPLE]", "").trim()
        val prevStatus = FixtureStatus.safeFrom(previous.status)
        val currStatus = FixtureStatus.safeFrom(current.status)

        // 1. Kickoff transition
        if (prevStatus == FixtureStatus.UPCOMING && currStatus == FixtureStatus.LIVE) {
            val title = if (isSwahili) "KIPENGA KIMEANZA: $home vs $away" else "KICKOFF LIVE: $home vs $away"
            val body = if (isSwahili) {
                "${current.competition} imeanza sasa! Fuatilia matokeo mubashara."
            } else {
                "${current.competition} has kicked off! Follow live score & match stats."
            }
            return title to body
        }

        // 2. Goal scored
        val homeGoalDelta = current.homeScore - previous.homeScore
        val awayGoalDelta = current.awayScore - previous.awayScore
        if (homeGoalDelta > 0 || awayGoalDelta > 0) {
            val scorerSide = if (homeGoalDelta > 0) home else away
            val title = if (isSwahili) {
                "GOLI! $home ${current.homeScore} - ${current.awayScore} $away"
            } else {
                "GOAL! $home ${current.homeScore} - ${current.awayScore} $away"
            }
            val body = if (isSwahili) {
                "$scorerSide wamefunga (${current.minuteLabel}) • ${current.competition}"
            } else {
                "$scorerSide scored (${current.minuteLabel}) • ${current.competition}"
            }
            return title to body
        }

        // 3. Red card issued
        val prevRedTotal = (previous.redCardsHome.toIntOrNull() ?: 0) + (previous.redCardsAway.toIntOrNull() ?: 0)
        val currRedTotal = (current.redCardsHome.toIntOrNull() ?: 0) + (current.redCardsAway.toIntOrNull() ?: 0)
        if (currRedTotal > prevRedTotal) {
            val title = if (isSwahili) {
                "KADI NYEKUNDU: $home ${current.homeScore}-${current.awayScore} $away"
            } else {
                "RED CARD: $home ${current.homeScore}-${current.awayScore} $away"
            }
            val body = if (isSwahili) {
                "Kadi nyekundu imetolewa dakika ya ${current.minuteLabel}."
            } else {
                "Red card shown at ${current.minuteLabel} • ${current.competition}"
            }
            return title to body
        }

        // 4. Full-time finished
        if (prevStatus == FixtureStatus.LIVE && currStatus == FixtureStatus.FINISHED) {
            val title = if (isSwahili) {
                "MECHI IMEISHA (FT): $home ${current.homeScore} - ${current.awayScore} $away"
            } else {
                "FULL TIME: $home ${current.homeScore} - ${current.awayScore} $away"
            }
            val body = if (isSwahili) {
                "Matokeo ya mwisho • ${current.competition}. Gusa kuona takwimu kamili."
            } else {
                "Final result • ${current.competition}. Tap to view full match stats."
            }
            return title to body
        }

        return null
    }
}

class DyfootNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_PAYMENTS = "dyfoot_payments"
        const val CHANNEL_LIVE_MATCHES = "dyfoot_live_matches"
        private const val PAYMENT_NOTIF_BASE_ID = 4100
        private const val VIP_ACTIVE_NOTIF_ID = 4200
        private const val MATCH_NOTIF_BASE_ID = 5000
    }

    init {
        ensureChannels()
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val paymentChannel = NotificationChannel(
                CHANNEL_PAYMENTS,
                "VIP Subscriptions & Payment Prompts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Carrier STK/USSD verification prompts and automatic VIP activation alerts."
            }
            val matchChannel = NotificationChannel(
                CHANNEL_LIVE_MATCHES,
                "Favorite Matches Live Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time kickoff, goal, card, and full-time alerts for favorited fixtures."
            }
            manager.createNotificationChannel(paymentChannel)
            manager.createNotificationChannel(matchChannel)
        }
    }

    fun notifyPaymentPinPrompt(
        tierLabel: String,
        amountTzs: Long,
        provider: MobileMoneyProvider,
        merchantNumber: String,
        requestRef: String,
        isSwahili: Boolean
    ) {
        if (!hasNotificationPermission()) return
        ensureChannels()

        val ussd = LiveMatchAlertEngine.ussdCodeFor(provider)
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(ussd))).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val dialPending = PendingIntent.getActivity(
            context,
            requestRef.hashCode(),
            dialIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPending = PendingIntent.getActivity(
            context,
            PAYMENT_NOTIF_BASE_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isSwahili) {
            "Thibitisha Malipo ($tierLabel • ${"%,d".format(amountTzs)} TSH)"
        } else {
            "Approve VIP Payment ($tierLabel • ${"%,d".format(amountTzs)} TSH)"
        }
        val body = if (isSwahili) {
            "Gusa kuingia ${provider.displayName} ($ussd) na uweke PIN yako ya siri kulipa $merchantNumber. Kifurushi kitafunguka moja kwa moja! Ref: $requestRef"
        } else {
            "Tap to open ${provider.displayName} ($ussd) and enter your carrier PIN to pay $merchantNumber. VIP activates automatically after confirmation! Ref: $requestRef"
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_PAYMENTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(dialPending)
            .addAction(
                0,
                if (isSwahili) "Ingiza PIN ($ussd)" else "Enter Carrier PIN ($ussd)",
                dialPending
            )
            .addAction(
                0,
                if (isSwahili) "Fungua DYFOOT" else "Open DYFOOT",
                appPending
            )
            .build()

        NotificationManagerCompat.from(context).notify(
            PAYMENT_NOTIF_BASE_ID + (requestRef.hashCode().absoluteValue % 90),
            notif
        )
    }

    fun notifyVipActivated(planName: String, isSwahili: Boolean) {
        if (!hasNotificationPermission()) return
        ensureChannels()

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPending = PendingIntent.getActivity(
            context,
            VIP_ACTIVE_NOTIF_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isSwahili) "Hongera! Kifurushi cha $planName Kimewashwa" else "$planName Activated!"
        val body = if (isSwahili) {
            "Malipo yako yamethibitishwa. Michezo na vidokezo vyote vya VIP vimefunguka moja kwa moja!"
        } else {
            "Your payment is verified. All VIP betting slips, codes & tactical breakdowns are now unlocked automatically!"
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_PAYMENTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(appPending)
            .build()

        NotificationManagerCompat.from(context).notify(VIP_ACTIVE_NOTIF_ID, notif)
    }

    fun notifyFavoriteMatchUpdate(matchId: String, title: String, body: String) {
        if (!hasNotificationPermission()) return
        ensureChannels()

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPending = PendingIntent.getActivity(
            context,
            matchId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_LIVE_MATCHES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(appPending)
            .build()

        NotificationManagerCompat.from(context).notify(
            MATCH_NOTIF_BASE_ID + (matchId.hashCode().absoluteValue % 900),
            notif
        )
    }

    fun notifyMatchKickoffReminder(
        matchId: String,
        homeTeam: String,
        awayTeam: String,
        competition: String,
        leadMinutes: Int,
        isSwahili: Boolean
    ) {
        if (!hasNotificationPermission()) return
        ensureChannels()

        val title = if (isSwahili) {
            "⏰ KUMBUKUMBU ($leadMinutes dk): $homeTeam vs $awayTeam"
        } else {
            "⏰ KICKOFF REMINDER (${leadMinutes}m): $homeTeam vs $awayTeam"
        }
        val body = if (isSwahili) {
            "$competition inakaribia kuanza! Fungua DYFOOT kuona kikosi na takwimu za mechi."
        } else {
            "$competition kicks off soon! Open DYFOOT for live squad lineups, H2H & match visualizer."
        }
        notifyFavoriteMatchUpdate(matchId = "rem_$matchId", title = title, body = body)
    }
}
