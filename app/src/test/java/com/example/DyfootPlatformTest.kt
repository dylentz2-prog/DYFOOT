package com.example

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AppSettings
import com.example.data.model.CommunityStatus
import com.example.data.model.FixtureItem
import com.example.data.model.FixtureStatus
import com.example.data.model.Membership
import com.example.data.model.MobileMoneyProvider
import com.example.data.model.NewsArticle
import com.example.data.model.OFFICIAL_MERCHANT_NUMBER
import com.example.data.model.OFFICIAL_OWNER_EMAIL
import com.example.data.model.OWNER_INSTAGRAM_HANDLE
import com.example.data.model.OWNER_TIKTOK_HANDLE
import com.example.data.model.OWNER_YOUTUBE_HANDLE
import com.example.data.model.TipItem
import com.example.data.model.TipTier
import com.example.data.model.UserRole
import com.example.data.model.VipTier
import com.example.data.model.VipTipSecret
import com.example.data.model.hasBilingualContent
import com.example.data.model.localizedContent
import com.example.data.model.localizedSummary
import com.example.data.model.localizedTitle
import com.example.data.model.supportsEnglish
import com.example.data.model.supportsSwahili
import com.example.data.notifications.DyfootFcmManager
import com.example.data.notifications.LiveMatchAlertEngine
import com.example.data.notifications.MatchReminderScheduler
import com.example.data.remote.DYFOOT_LIVE_LEAGUES
import com.example.data.remote.ExternalSportsService
import com.example.ui.components.DyfootTopBar
import com.example.ui.components.LiveMatchVisualizerCard
import com.example.ui.components.MainTab
import com.example.ui.components.PreMatchH2HAnalysisCard
import com.example.ui.components.SquadFormationVisualizerCard
import com.example.ui.components.TipSlipCard
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.dyfootStrings
import com.example.ui.screens.AdminBilingualNewsPanel
import com.example.ui.screens.InAppArticleReaderSheet
import com.example.ui.theme.DyfootTheme
import androidx.credentials.CredentialManager
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.onGoogleSignInClicked
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DyfootPlatformTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `owner email and Tanzania merchant number match specification`() {
        assertEquals("dyvix02@gmail.com", OFFICIAL_OWNER_EMAIL)
        assertEquals("0706499813", OFFICIAL_MERCHANT_NUMBER)
    }

    @Test
    fun `vip package names and TSH prices match ordered schedule`() {
        assertEquals("ROCK PACKAGE", VipTier.DAILY.label)
        assertEquals(1_000L, VipTier.DAILY.defaultTzs)
        assertEquals(1, VipTier.DAILY.durationDays)

        assertEquals("STAR PACKAGE", VipTier.WEEKLY.label)
        assertEquals(5_000L, VipTier.WEEKLY.defaultTzs)
        assertEquals(7, VipTier.WEEKLY.durationDays)

        assertEquals("PRO PACKAGE", VipTier.MONTHLY.label)
        assertEquals(15_000L, VipTier.MONTHLY.defaultTzs)
        assertEquals(30, VipTier.MONTHLY.durationDays)

        assertEquals("LEGEND PACKAGE", VipTier.ANNUAL.label)
        assertEquals(80_000L, VipTier.ANNUAL.defaultTzs)
        assertEquals(365, VipTier.ANNUAL.durationDays)

        val defaults = AppSettings()
        assertEquals(1_000L, defaults.vipDailyTzs)
        assertEquals(5_000L, defaults.vipWeeklyTzs)
        assertEquals(15_000L, defaults.vipMonthlyTzs)
        assertEquals(80_000L, defaults.vipAnnualTzs)
    }

    @Test
    fun `navigation bar has 5 screenshot tabs and worldwide multi-sport leagues`() {
        assertEquals(listOf("Home", "Tips", "Watch", "Hub", "Chat"), MainTab.entries.map { it.label })
        assertTrue(DYFOOT_LIVE_LEAGUES.any { it.code == "PL" && it.espnSlug == "eng.1" })
        assertTrue(DYFOOT_LIVE_LEAGUES.any { it.code == "CL" && it.espnSlug == "uefa.champions" })
        assertTrue(DYFOOT_LIVE_LEAGUES.any { it.code == "NBA" && it.sportCategory == "Basketball" })
        assertTrue(DYFOOT_LIVE_LEAGUES.any { it.code == "UFC" && it.sportCategory == "Boxing & MMA" })
        assertTrue(DYFOOT_LIVE_LEAGUES.any { it.code == "F1" && it.sportCategory == "Motorsport" })
        assertTrue(DYFOOT_LIVE_LEAGUES.size >= 12)

        val status = CommunityStatus(authorId = "u1", authorName = "Fan", text = "Matchday!")
        assertEquals("LIME", status.themeColor)
    }

    @Test
    fun `owner social handles bilingual Swahili strings and fixture stats match requirements`() {
        assertEquals("@dyvix_", OWNER_TIKTOK_HANDLE)
        assertEquals("@dyvix_02", OWNER_INSTAGRAM_HANDLE)
        assertEquals("@dyvix_02", OWNER_YOUTUBE_HANDLE)

        val sw = dyfootStrings(AppLanguage.SWAHILI)
        val en = dyfootStrings(AppLanguage.ENGLISH)
        assertEquals("Nyumbani", sw.navHome)
        assertEquals("Home", en.navHome)
        assertEquals("Umiliki wa Mpira", sw.possession)
        assertEquals("Kona", sw.corners)
        assertEquals("Kadi za Njano", sw.yellowCards)

        val fixture = FixtureItem(
            id = "fx1",
            possessionHome = "58%",
            possessionAway = "42%",
            cornersHome = "6",
            cornersAway = "3",
            yellowCardsHome = "2",
            yellowCardsAway = "1"
        )
        assertEquals("58%", fixture.possessionHome)
        assertEquals("6", fixture.cornersHome)
        assertEquals("2", fixture.yellowCardsHome)
    }

    @Test
    fun `role and mobile money providers parse safely`() {
        assertEquals(UserRole.OWNER, UserRole.safeFrom("OWNER"))
        assertEquals(UserRole.FREE_MEMBER, UserRole.safeFrom("UNKNOWN_SPOOF"))
        assertEquals("M-PESA", MobileMoneyProvider.M_PESA.wireValue())
        assertEquals("TIGO_PESA", MobileMoneyProvider.TIGO_PESA.wireValue())
        assertEquals("AIRTEL_MONEY", MobileMoneyProvider.AIRTEL_MONEY.wireValue())
        assertEquals("HALOPESA", MobileMoneyProvider.HALOPESA.wireValue())
    }

    @Test
    fun `membership active check enforces future expiry and ACTIVE status`() {
        val future = Timestamp(Date(System.currentTimeMillis() + 86_400_000L))
        val past = Timestamp(Date(System.currentTimeMillis() - 86_400_000L))

        assertTrue(Membership(status = "ACTIVE", expiresAt = future).isCurrentlyActive())
        assertFalse(Membership(status = "ACTIVE", expiresAt = past).isCurrentlyActive())
        assertFalse(Membership(status = "EXPIRED", expiresAt = future).isCurrentlyActive())
    }

    @Test
    fun `external sports service recognizes placeholder keys as unconfigured`() {
        val service = ExternalSportsService()
        assertEquals("", service.sanitizeConfiguredKey("YOUR_FOOTBALL_DATA_API_KEY", "YOUR_FOOTBALL_DATA_API_KEY"))
        assertEquals("", service.sanitizeConfiguredKey("  ", "YOUR_MOBILE_MONEY_MERCHANT_KEY"))
        assertEquals("live_token_123", service.sanitizeConfiguredKey(" live_token_123 ", "YOUR_FOOTBALL_DATA_API_KEY"))
    }

    @Test
    fun `vip tip card hides protected selection when locked and reveals combo button on free tip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vipTip = TipItem(
            id = "tip_vip_test",
            matchName = "Simba SC vs Young Africans",
            homeTeam = "Simba SC",
            awayTeam = "Young Africans",
            competition = "NBC Premier League",
            kickoffLabel = "Sun 16:00 EAT",
            tier = TipTier.VIP.name,
            market = "BTTS",
            odds = "2.25",
            bookmakerName = "BetPawa TZ"
        )
        val secret = VipTipSecret(
            tipId = "tip_vip_test",
            selection = "BTTS Yes & Over 1.5",
            bookmakerCode = "BP-8841",
            predictedScore = "2-1",
            analysis = "High transition derby matchup."
        )

        composeRule.setContent {
            DyfootTheme {
                TipSlipCard(
                    tip = vipTip,
                    vipSecret = secret,
                    hasVipAccess = false,
                    isBookmarked = false,
                    onBookmarkToggle = {},
                    onUpgradeVipClick = {},
                    context = context,
                    isInCombo = false,
                    onToggleCombo = {}
                )
            }
        }

        composeRule.onNodeWithText("Unlock VIP Access").assertIsDisplayed()
        composeRule.onNodeWithText("BTTS Yes & Over 1.5").assertDoesNotExist()
    }

    @Test
    fun `push payment ref generator and provider ussd codes match Tanzania mobile money`() {
        val ref = LiveMatchAlertEngine.generateStkPushRef(VipTier.DAILY, nowMs = 123_456L)
        assertTrue(ref.startsWith("PUSH-RCK-"))
        assertTrue(ref.length >= 10)

        assertEquals("*150*00#", LiveMatchAlertEngine.ussdCodeFor(MobileMoneyProvider.M_PESA))
        assertEquals("*150*01#", LiveMatchAlertEngine.ussdCodeFor(MobileMoneyProvider.TIGO_PESA))
        assertEquals("*150*60#", LiveMatchAlertEngine.ussdCodeFor(MobileMoneyProvider.AIRTEL_MONEY))
        assertEquals("*150*88#", LiveMatchAlertEngine.ussdCodeFor(MobileMoneyProvider.HALOPESA))
    }

    @Test
    fun `favorite match live alert engine detects kickoff goals red cards and full time`() {
        val upcoming = FixtureItem(
            id = "f_ars_che",
            homeTeam = "Arsenal",
            awayTeam = "Chelsea",
            competition = "Premier League",
            status = FixtureStatus.UPCOMING.name,
            homeScore = 0,
            awayScore = 0
        )
        val liveStart = upcoming.copy(status = FixtureStatus.LIVE.name, minuteLabel = "1'")
        val kickoffAlert = LiveMatchAlertEngine.detectFavoriteMatchEvents(upcoming, liveStart, isSwahili = false)
        assertTrue(kickoffAlert != null && kickoffAlert.first.contains("KICKOFF LIVE"))

        val goalScored = liveStart.copy(homeScore = 1, minuteLabel = "24'")
        val goalAlertSw = LiveMatchAlertEngine.detectFavoriteMatchEvents(liveStart, goalScored, isSwahili = true)
        assertTrue(goalAlertSw != null && goalAlertSw.first.contains("GOLI!") && goalAlertSw.first.contains("1 - 0"))

        val redCard = goalScored.copy(redCardsAway = "1", minuteLabel = "61'")
        val redAlert = LiveMatchAlertEngine.detectFavoriteMatchEvents(goalScored, redCard, isSwahili = false)
        assertTrue(redAlert != null && redAlert.first.contains("RED CARD"))

        val fullTime = redCard.copy(status = FixtureStatus.FINISHED.name, minuteLabel = "FT")
        val ftAlert = LiveMatchAlertEngine.detectFavoriteMatchEvents(redCard, fullTime, isSwahili = false)
        assertTrue(ftAlert != null && ftAlert.first.contains("FULL TIME"))
    }

    @Test
    fun `fcm manager filters out non favorite matches and formats goal and final score payloads`() {
        assertEquals("dyfoot_match_espn_PL_704123", DyfootFcmManager.topicForMatch("espn/PL:704123"))

        val favorites = setOf("espn_PL_704123")
        val nonFavoritePayload = mapOf(
            "matchId" to "espn_LL_999999",
            "eventType" to "GOAL",
            "homeTeam" to "Real Madrid",
            "awayTeam" to "Barcelona",
            "homeScore" to "1",
            "awayScore" to "0",
            "minuteLabel" to "12'",
            "competition" to "LaLiga"
        )
        assertNull(
            DyfootFcmManager.buildAlertFromPayload(
                data = nonFavoritePayload,
                fallbackTitle = null,
                fallbackBody = null,
                favoriteMatchIds = favorites,
                isSwahili = false
            )
        )

        val goalPayload = mapOf(
            "matchId" to "espn_PL_704123",
            "eventType" to "GOAL",
            "homeTeam" to "Arsenal",
            "awayTeam" to "Liverpool",
            "homeScore" to "2",
            "awayScore" to "1",
            "minuteLabel" to "68'",
            "competition" to "Premier League",
            "scorer" to "Saka"
        )
        val goalEn = DyfootFcmManager.buildAlertFromPayload(goalPayload, null, null, favorites, isSwahili = false)
        assertTrue(goalEn != null && goalEn.first.contains("GOAL! Arsenal 2 - 1 Liverpool") && goalEn.second.contains("Saka"))

        val goalSw = DyfootFcmManager.buildAlertFromPayload(goalPayload, null, null, favorites, isSwahili = true)
        assertTrue(goalSw != null && goalSw.first.contains("GOLI! Arsenal 2 - 1 Liverpool"))

        val finalScorePayload = mapOf(
            "matchId" to "espn_PL_704123",
            "eventType" to "FINAL_SCORE",
            "homeTeam" to "Arsenal",
            "awayTeam" to "Liverpool",
            "homeScore" to "2",
            "awayScore" to "1",
            "minuteLabel" to "FT",
            "competition" to "Premier League"
        )
        val ftEn = DyfootFcmManager.buildAlertFromPayload(finalScorePayload, null, null, favorites, isSwahili = false)
        assertTrue(ftEn != null && ftEn.first.contains("FINAL SCORE (FT): Arsenal 2 - 1 Liverpool"))
    }

    @Test
    fun `match detail bundle builds 11v11 squad formations and H2H pre-match probabilities`() {
        val fixture = FixtureItem(
            id = "fx_ars_liv",
            homeTeam = "Arsenal",
            awayTeam = "Liverpool",
            competition = "Premier League",
            status = FixtureStatus.LIVE.name,
            homeScore = 2,
            awayScore = 1,
            possessionHome = "57%",
            possessionAway = "43%",
            shotsHome = "14",
            shotsAway = "9",
            minuteLabel = "64'"
        )
        val bundle = ExternalSportsService().buildAnalyticalMatchBundle(fixture, root = null)
        assertEquals(11, bundle.homeSquad.size)
        assertEquals(11, bundle.awaySquad.size)
        assertEquals(57, bundle.attackMomentumHomePct)

        val analysis = bundle.preMatchAnalysis!!
        assertEquals(100, analysis.homeWinPct + analysis.drawPct + analysis.awayWinPct)
        assertEquals(5, analysis.homeForm.size)
        assertEquals(5, analysis.awayForm.size)
        assertTrue(analysis.recentH2H.isNotEmpty())
        assertTrue(analysis.tacticalEdgeNote.isNotBlank())
    }

    @Test
    fun `match reminder scheduler computes 15-minute lead trigger and imminent fallback`() {
        val now = 1_700_000_000_000L
        val twoHoursLater = now + (2 * 60 * 60_000L)
        val scheduled = MatchReminderScheduler.computeTriggerTimeMs(twoHoursLater, leadMinutes = 15, nowMs = now)
        assertEquals(twoHoursLater - (15 * 60_000L), scheduled)

        val fiveMinsLater = now + (5 * 60_000L)
        val imminent = MatchReminderScheduler.computeTriggerTimeMs(fiveMinsLater, leadMinutes = 15, nowMs = now)
        assertEquals(fiveMinsLater, imminent)
    }

    @Test
    fun `visualizer squad and H2H cards render analytical sections cleanly`() {
        val fixture = FixtureItem(
            id = "fx_vis_test",
            homeTeam = "Simba SC",
            awayTeam = "Young Africans",
            competition = "NBC Premier League",
            status = FixtureStatus.LIVE.name,
            homeScore = 1,
            awayScore = 1,
            minuteLabel = "52'",
            possessionHome = "54%",
            possessionAway = "46%"
        )
        val bundle = ExternalSportsService().buildAnalyticalMatchBundle(fixture, root = null)

        composeRule.setContent {
            DyfootTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    LiveMatchVisualizerCard(bundle = bundle, isSwahili = false)
                    SquadFormationVisualizerCard(bundle = bundle, isSwahili = false)
                    PreMatchH2HAnalysisCard(bundle = bundle, isSwahili = false)
                }
            }
        }

        composeRule.onNodeWithText("LIVE MATCH VISUALIZER").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("ANIMATED SQUAD FORMATION").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("PRE-MATCH STATISTICAL & H2H").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Recent Head-to-Head Meetings").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `initial in-app news includes Swahili Tanzania social, TZ leagues, African leagues and English updates`() {
        val news = ExternalSportsService().getInitialInAppNews()
        val swArticles = news.filter { it.language == "SW" }
        val enArticles = news.filter { it.language == "EN" }

        assertTrue(swArticles.isNotEmpty())
        assertTrue(enArticles.isNotEmpty())
        assertTrue(swArticles.any { it.category == "TANZANIA_LIGI" })
        assertTrue(swArticles.any { it.category == "TANZANIA_SOCIAL" })
        assertTrue(swArticles.any { it.category == "AFRICA_CAF" })
        assertTrue(news.all { it.sourceUrl.isBlank() && it.content.isNotBlank() })
    }

    @Test
    fun `top bar omits language button so language change exists only in menu`() {
        composeRule.setContent {
            DyfootTheme {
                DyfootTopBar(
                    unreadCount = 2,
                    userPhotoUrl = "",
                    userName = "DYFOOT Fan",
                    onNotificationsClick = {},
                    onProfileClick = {},
                    onMenuClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("top_lang_toggle_btn").assertDoesNotExist()
        composeRule.onNodeWithTag("top_menu_btn").assertIsDisplayed()
    }

    @Test
    fun `in-app article reader renders full content exclusively inside app without Open Source button`() {
        val article = NewsArticle(
            id = "sw_tz_test",
            title = "Simba na Yanga Wakaza Buti Ligi Kuu NBC",
            summary = "Muhtasari wa maandalizi ya mechi za mzunguko.",
            content = "Aya ya kwanza ya habari kamili ndani ya DYFOOT.\n\nAya ya pili yenye uchambuzi wa kina wa Ligi Kuu Tanzania.",
            category = "TANZANIA_LIGI",
            sourceName = "DYFOOT Tanzania Desk",
            sourceUrl = "",
            language = "SW"
        )

        composeRule.setContent {
            DyfootTheme {
                InAppArticleReaderSheet(
                    article = article,
                    isSwahili = true,
                    isLiked = false,
                    isBookmarked = false,
                    onToggleLike = {},
                    onToggleBookmark = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("SOMA NDANI YA APP").assertIsDisplayed()
        composeRule.onNodeWithText("Simba na Yanga Wakaza Buti Ligi Kuu NBC").assertIsDisplayed()
        composeRule.onNodeWithText("Open Source").assertDoesNotExist()
    }

    @Test
    fun `bilingual news article helpers resolve English and Kiswahili cleanly without external links`() {
        val bilingual = NewsArticle(
            id = "bi_nbc_1",
            title = "Yanga & Simba Ready for Kariakoo Derby",
            summary = "Full tactical preview in Dar es Salaam.",
            content = "English body inside DYFOOT.",
            titleSw = "Yanga na Simba Tayari kwa Kariakoo Derby",
            summarySw = "Uchambuzi wa mbinu jijini Dar es Salaam.",
            contentSw = "Habari kamili kwa Kiswahili ndani ya DYFOOT.",
            leagueTag = "NBC Premier League",
            localFootball = true,
            category = "TANZANIA_LIGI",
            sourceUrl = "",
            language = "BILINGUAL"
        )

        assertTrue(bilingual.hasBilingualContent())
        assertTrue(bilingual.supportsEnglish())
        assertTrue(bilingual.supportsSwahili())
        assertEquals("Yanga & Simba Ready for Kariakoo Derby", bilingual.localizedTitle(isSwahili = false))
        assertEquals("Yanga na Simba Tayari kwa Kariakoo Derby", bilingual.localizedTitle(isSwahili = true))
        assertEquals("Full tactical preview in Dar es Salaam.", bilingual.localizedSummary(isSwahili = false))
        assertEquals("Uchambuzi wa mbinu jijini Dar es Salaam.", bilingual.localizedSummary(isSwahili = true))
        assertEquals("English body inside DYFOOT.", bilingual.localizedContent(isSwahili = false))
        assertEquals("Habari kamili kwa Kiswahili ndani ya DYFOOT.", bilingual.localizedContent(isSwahili = true))
    }

    @Test
    fun `admin bilingual local news panel denies non-admins and hides composer inputs`() {
        composeRule.setContent {
            DyfootTheme {
                AdminBilingualNewsPanel(
                    isAuthorized = false,
                    articles = emptyList(),
                    onSaveArticle = { _, _ -> },
                    onDeleteArticle = {}
                )
            }
        }

        composeRule.onNodeWithTag("admin_news_access_denied").assertIsDisplayed()
        composeRule.onNodeWithText("Admin-Only Local News Desk").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_news_title_en").assertDoesNotExist()
        composeRule.onNodeWithTag("admin_news_save_btn").assertDoesNotExist()
    }

    @Test
    fun `admin bilingual local news panel validates and saves internal EN plus SW article with zero sourceUrl`() {
        var savedArticle: NewsArticle? = null

        composeRule.setContent {
            DyfootTheme {
                AdminBilingualNewsPanel(
                    isAuthorized = true,
                    articles = emptyList(),
                    onSaveArticle = { article, onDone ->
                        savedArticle = article
                        onDone()
                    },
                    onDeleteArticle = {}
                )
            }
        }

        composeRule.onNodeWithTag("admin_news_save_btn").performScrollTo().performClick()
        composeRule.onNodeWithTag("admin_news_validation_error").performScrollTo().assertIsDisplayed()
        assertNull(savedArticle)

        composeRule.onNodeWithTag("admin_news_title_en").performScrollTo().performTextInput("Azam FC Top NBC Standings")
        composeRule.onNodeWithTag("admin_news_summary_en").performScrollTo().performTextInput("Late winner seals three points.")
        composeRule.onNodeWithTag("admin_news_content_en").performScrollTo().performTextInput("Full match report stored internally in DYFOOT.")

        composeRule.onNodeWithTag("admin_news_title_sw").performScrollTo().performTextInput("Azam FC Yaongoza Msimamo wa NBC")
        composeRule.onNodeWithTag("admin_news_summary_sw").performScrollTo().performTextInput("Bao la ushindi dakika za mwisho.")
        composeRule.onNodeWithTag("admin_news_content_sw").performScrollTo().performTextInput("Taarifa kamili ya mchezo ndani ya DYFOOT bila viungo vya nje.")

        composeRule.onNodeWithTag("admin_news_save_btn").performScrollTo().performClick()

        val saved = savedArticle!!
        assertEquals("Azam FC Top NBC Standings", saved.title)
        assertEquals("Azam FC Yaongoza Msimamo wa NBC", saved.titleSw)
        assertEquals("BILINGUAL", saved.language)
        assertEquals("NBC Premier League", saved.leagueTag)
        assertTrue(saved.localFootball)
        assertEquals("", saved.sourceUrl)
    }

    @Test
    fun `firebase setup fallback screen renders cleanly without crashing and triggers retry`() {
        var retried = false

        composeRule.setContent {
            DyfootTheme {
                FirebaseSetupRequiredScreen(
                    onRetry = { retried = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("firebase_setup_required").assertIsDisplayed()
        composeRule.onNodeWithTag("firebase_setup_retry_button").performScrollTo().performClick()
        assertTrue(retried)
    }

    @Test
    fun `attemptAutoSignIn and onGoogleSignInClicked fail gracefully when FirebaseApp is uninitialized`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val credentialManager = CredentialManager.create(context)
        var unauthenticatedCalled = false
        var successCalled = false
        var errorMessage: String? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { successCalled = true },
            onUnauthenticated = { unauthenticatedCalled = true },
            scope = scope,
            authProvider = { null }
        )

        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { successCalled = true },
            onAuthError = { errorMessage = it },
            scope = scope,
            authProvider = { null }
        )

        assertFalse(successCalled)
        assertTrue(unauthenticatedCalled)
        assertTrue(errorMessage?.contains("google-services.json") == true)
    }
}
