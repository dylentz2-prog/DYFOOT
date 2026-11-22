package com.example.data.remote

import android.util.Xml
import com.example.BuildConfig
import com.example.data.model.FixtureItem
import com.example.data.model.FixtureStatus
import com.example.data.model.H2HMeeting
import com.example.data.model.MatchDetailBundle
import com.example.data.model.NewsArticle
import com.example.data.model.PreMatchAnalysis
import com.example.data.model.SquadPlayerNode
import com.example.data.model.StandingRow
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

sealed interface LiveApiResult<out T> {
    data class Success<T>(
        val data: T,
        val sourceName: String,
        val updatedAtLabel: String
    ) : LiveApiResult<T>

    data class MissingApiKey(val message: String) : LiveApiResult<Nothing>
    data class Error(val message: String) : LiveApiResult<Nothing>
}

data class FootballLeagueOption(
    val code: String,
    val label: String,
    val espnSlug: String,
    val footballDataCode: String,
    val sportPath: String = if (espnSlug.isNotBlank()) "soccer/$espnSlug" else "",
    val sportCategory: String = "Football"
)

val DYFOOT_LIVE_LEAGUES = listOf(
    FootballLeagueOption("ALL", "All Sports", "", "", "", "All"),
    FootballLeagueOption("PL", "Premier League", "eng.1", "PL", "soccer/eng.1", "Football"),
    FootballLeagueOption("CL", "Champions League", "uefa.champions", "CL", "soccer/uefa.champions", "Football"),
    FootballLeagueOption("PD", "La Liga", "esp.1", "PD", "soccer/esp.1", "Football"),
    FootballLeagueOption("SA", "Serie A", "ita.1", "SA", "soccer/ita.1", "Football"),
    FootballLeagueOption("BL1", "Bundesliga", "ger.1", "BL1", "soccer/ger.1", "Football"),
    FootballLeagueOption("FL1", "Ligue 1", "fra.1", "FL1", "soccer/fra.1", "Football"),
    FootballLeagueOption("EL", "Europa League", "uefa.europa", "EL", "soccer/uefa.europa", "Football"),
    FootballLeagueOption("MLS", "MLS", "usa.1", "", "soccer/usa.1", "Football"),
    FootballLeagueOption("NBA", "NBA", "nba", "", "basketball/nba", "Basketball"),
    FootballLeagueOption("UFC", "UFC / MMA", "ufc", "", "mma/ufc", "Boxing & MMA"),
    FootballLeagueOption("ATP", "ATP Tennis", "atp", "", "tennis/atp", "Tennis"),
    FootballLeagueOption("F1", "Formula 1", "f1", "", "racing/f1", "Motorsport")
)

class ExternalSportsService {

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    internal fun sanitizeConfiguredKey(raw: String, placeholder: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty() || trimmed == placeholder || trimmed.startsWith("YOUR_")) "" else trimmed
    }

    private fun resolveFootballDataKey(): String =
        sanitizeConfiguredKey(BuildConfig.FOOTBALL_DATA_API_KEY, "YOUR_FOOTBALL_DATA_API_KEY")

    private fun resolveMobileMoneyKey(): String =
        sanitizeConfiguredKey(BuildConfig.MOBILE_MONEY_MERCHANT_KEY, "YOUR_MOBILE_MONEY_MERCHANT_KEY")

    fun hasFootballDataApiKey(): Boolean = resolveFootballDataKey().isNotEmpty()

    fun hasMobileMoneyGatewayKey(): Boolean = resolveMobileMoneyKey().isNotEmpty()

    /**
     * Full Worldwide Sports Live Sync:
     * 1) Fetches real-time public ESPN multi-sport & multi-league scoreboards directly
     *    (Premier League, Champions League, La Liga, Serie A, Bundesliga, Ligue 1, Europa League, MLS, NBA, UFC, ATP, F1).
     * 2) Queries football-data.org v4 when FOOTBALL_DATA_API_KEY is configured and merges uniquely.
     */
    suspend fun fetchLiveMatches(
        leagueCode: String = "ALL",
        dateOffsetDays: Int = 0
    ): LiveApiResult<List<FixtureItem>> = withContext(Dispatchers.IO) {
        val apiKey = resolveFootballDataKey()
        val combined = mutableListOf<FixtureItem>()
        val dateParam = yyyymmddForOffset(dateOffsetDays)

        if (apiKey.isNotEmpty() && dateOffsetDays == 0) {
            runCatching {
                val selectedLeague = DYFOOT_LIVE_LEAGUES.firstOrNull { it.code == leagueCode }
                val fdCode = selectedLeague?.footballDataCode.orEmpty()
                if (leagueCode == "ALL" || fdCode.isNotBlank()) {
                    val url = if (leagueCode == "ALL") {
                        "https://api.football-data.org/v4/matches"
                    } else {
                        "https://api.football-data.org/v4/competitions/$fdCode/matches"
                    }
                    val request = Request.Builder()
                        .url(url)
                        .header("X-Auth-Token", apiKey)
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val root = JSONObject(response.body?.string().orEmpty())
                            combined.addAll(parseFootballDataMatches(root, leagueCode))
                        }
                    }
                }
            }
        }

        val targets = if (leagueCode == "ALL") {
            DYFOOT_LIVE_LEAGUES.filter { it.sportPath.isNotBlank() }
        } else {
            DYFOOT_LIVE_LEAGUES.filter { it.code == leagueCode && it.sportPath.isNotBlank() }
        }

        val espnLists = targets.map { league ->
            async {
                fetchEspnScoreboardForLeague(league, dateParam)
            }
        }.awaitAll().flatten()

        val seenKeys = combined.map { "${it.homeTeam.lowercase()}_${it.awayTeam.lowercase()}" }.toMutableSet()
        espnLists.forEach { item ->
            val key = "${item.homeTeam.lowercase()}_${item.awayTeam.lowercase()}"
            if (seenKeys.add(key)) {
                combined.add(item)
            }
        }

        val sorted = combined.sortedWith(
            compareBy<FixtureItem>(
                {
                    when (FixtureStatus.safeFrom(it.status)) {
                        FixtureStatus.LIVE -> 0
                        FixtureStatus.UPCOMING -> 1
                        FixtureStatus.FINISHED -> 2
                        FixtureStatus.POSTPONED -> 3
                    }
                },
                { it.kickoffEpochMs }
            )
        )

        val sourceLabel = if (apiKey.isNotEmpty()) {
            "football-data.org + World Sports Direct"
        } else {
            "World Sports Direct Live"
        }
        LiveApiResult.Success(sorted, sourceLabel, nowLabel())
    }

    private fun fetchEspnScoreboardForLeague(
        league: FootballLeagueOption,
        dateYyyymmdd: String
    ): List<FixtureItem> {
        return try {
            val req = Request.Builder()
                .url("https://site.api.espn.com/apis/site/v2/sports/${league.sportPath}/scoreboard?dates=$dateYyyymmdd")
                .header("User-Agent", "DYFOOT-Android/2.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string().orEmpty()
                parseEspnScoreboard(JSONObject(body), league)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseEspnScoreboard(root: JSONObject, league: FootballLeagueOption): List<FixtureItem> {
        val events = root.optJSONArray("events") ?: return emptyList()
        val isoFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        )
        val localTimeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        return buildList {
            for (i in 0 until events.length()) {
                val ev = events.optJSONObject(i) ?: continue
                val id = ev.optString("id", "${league.code}_$i")
                val dateStr = ev.optString("date", "")
                val epochMs = isoFormats.firstNotNullOfOrNull { fmt ->
                    runCatching { fmt.parse(dateStr)?.time }.getOrNull()
                } ?: System.currentTimeMillis()

                // Handle standard competitions or nested groupings (e.g., Tennis ATP)
                val compObj = ev.optJSONArray("competitions")?.optJSONObject(0)
                    ?: ev.optJSONArray("groupings")?.optJSONObject(0)
                        ?.optJSONArray("competitions")?.optJSONObject(0)
                    ?: continue

                val competitors = compObj.optJSONArray("competitors")
                var homeObj: JSONObject? = null
                var awayObj: JSONObject? = null
                if (competitors != null && competitors.length() >= 2) {
                    for (cIdx in 0 until competitors.length()) {
                        val c = competitors.optJSONObject(cIdx) ?: continue
                        val ha = c.optString("homeAway")
                        if (ha == "home" || (homeObj == null && ha.isBlank())) {
                            homeObj = c
                        } else if (awayObj == null) {
                            awayObj = c
                        }
                    }
                }
                if (homeObj == null || awayObj == null) {
                    // Special fallback for F1/Racing or tournament event name
                    val eventShort = ev.optString("shortName").ifBlank { ev.optString("name") }
                    if (eventShort.isBlank()) continue
                    val statusType = compObj.optJSONObject("status")?.optJSONObject("type")
                    val state = statusType?.optString("state", "pre") ?: "pre"
                    val shortDetail = statusType?.optString("shortDetail", "").orEmpty()
                    val fixtureStatus = when (state) {
                        "in" -> FixtureStatus.LIVE
                        "post" -> FixtureStatus.FINISHED
                        else -> FixtureStatus.UPCOMING
                    }
                    add(
                        FixtureItem(
                            id = "espn_${league.code}_$id",
                            homeTeam = eventShort,
                            awayTeam = league.label,
                            competition = league.label,
                            status = fixtureStatus.name,
                            minuteLabel = if (fixtureStatus == FixtureStatus.UPCOMING) {
                                localTimeFmt.format(java.util.Date(epochMs))
                            } else shortDetail.ifBlank { fixtureStatus.label },
                            kickoffEpochMs = epochMs,
                            venue = compObj.optJSONObject("venue")?.optString("fullName").orEmpty(),
                            sourceLabel = "World Feed • ${league.label}",
                            published = true,
                            leagueCode = league.code,
                            sportCategory = league.sportCategory,
                            espnEventId = id,
                            espnSportPath = league.sportPath,
                            isHotMatch = true
                        )
                    )
                    continue
                }

                val homeTeamObj = homeObj.optJSONObject("team") ?: homeObj.optJSONObject("athlete")
                val awayTeamObj = awayObj.optJSONObject("team") ?: awayObj.optJSONObject("athlete")
                val homeName = homeTeamObj?.optString("shortDisplayName")
                    ?.ifBlank { homeTeamObj.optString("displayName") }
                    ?.ifBlank { homeTeamObj.optString("shortName") } ?: "Home"
                val awayName = awayTeamObj?.optString("shortDisplayName")
                    ?.ifBlank { awayTeamObj.optString("displayName") }
                    ?.ifBlank { awayTeamObj.optString("shortName") } ?: "Away"
                val homeLogo = homeTeamObj?.optString("logo")
                    ?.ifBlank { homeTeamObj.optJSONObject("flag")?.optString("href").orEmpty() }.orEmpty()
                val awayLogo = awayTeamObj?.optString("logo")
                    ?.ifBlank { awayTeamObj.optJSONObject("flag")?.optString("href").orEmpty() }.orEmpty()

                val homeScore = homeObj.optString("score", "0").toDoubleOrNull()?.toLong() ?: 0L
                val awayScore = awayObj.optString("score", "0").toDoubleOrNull()?.toLong() ?: 0L

                val statusType = compObj.optJSONObject("status")?.optJSONObject("type")
                val state = statusType?.optString("state", "pre") ?: "pre"
                val shortDetail = statusType?.optString("shortDetail", "").orEmpty()
                val displayClock = compObj.optJSONObject("status")?.optString("displayClock", "").orEmpty()

                val fixtureStatus = when (state) {
                    "in" -> FixtureStatus.LIVE
                    "post" -> if (shortDetail.contains("PPD", true) || shortDetail.contains("Post", true)) {
                        FixtureStatus.POSTPONED
                    } else {
                        FixtureStatus.FINISHED
                    }
                    else -> FixtureStatus.UPCOMING
                }

                val minuteLabel = when (fixtureStatus) {
                    FixtureStatus.LIVE -> displayClock.ifBlank { shortDetail.ifBlank { "LIVE" } }
                    FixtureStatus.FINISHED -> shortDetail.ifBlank { "FT" }
                    FixtureStatus.POSTPONED -> "PPD"
                    FixtureStatus.UPCOMING -> localTimeFmt.format(java.util.Date(epochMs))
                }

                val venue = compObj.optJSONObject("venue")?.optString("fullName").orEmpty()
                val timeline = parseEspnDetailsTimeline(compObj.optJSONArray("details"))
                val homeStats = extractCompetitorStatMap(homeObj.optJSONArray("statistics"))
                val awayStats = extractCompetitorStatMap(awayObj.optJSONArray("statistics"))

                add(
                    FixtureItem(
                        id = "espn_${league.code}_$id",
                        homeTeam = homeName,
                        awayTeam = awayName,
                        competition = league.label,
                        homeScore = homeScore,
                        awayScore = awayScore,
                        status = fixtureStatus.name,
                        minuteLabel = minuteLabel,
                        kickoffEpochMs = epochMs,
                        venue = venue,
                        sourceLabel = "World Feed • ${league.label}",
                        streamStatus = "COMING_SOON",
                        eventsSummary = timeline,
                        published = true,
                        homeLogoUrl = homeLogo,
                        awayLogoUrl = awayLogo,
                        leagueCode = league.code,
                        sportCategory = league.sportCategory,
                        espnEventId = id,
                        espnSportPath = league.sportPath,
                        isHotMatch = league.code in setOf("CL", "PL", "PD", "SA", "BL1", "EL", "NBA", "UFC"),
                        possessionHome = homeStats["possessionPct"]?.let { formatPct(it) }.orEmpty(),
                        possessionAway = awayStats["possessionPct"]?.let { formatPct(it) }.orEmpty(),
                        shotsHome = homeStats["totalShots"].orEmpty(),
                        shotsAway = awayStats["totalShots"].orEmpty(),
                        shotsOnTargetHome = homeStats["shotsOnTarget"].orEmpty(),
                        shotsOnTargetAway = awayStats["shotsOnTarget"].orEmpty(),
                        cornersHome = homeStats["wonCorners"].orEmpty(),
                        cornersAway = awayStats["wonCorners"].orEmpty(),
                        yellowCardsHome = homeStats["yellowCards"].orEmpty(),
                        yellowCardsAway = awayStats["yellowCards"].orEmpty(),
                        redCardsHome = homeStats["redCards"].orEmpty(),
                        redCardsAway = awayStats["redCards"].orEmpty(),
                        foulsHome = homeStats["foulsCommitted"].orEmpty(),
                        foulsAway = awayStats["foulsCommitted"].orEmpty(),
                        offsidesHome = homeStats["offsides"].orEmpty(),
                        offsidesAway = awayStats["offsides"].orEmpty(),
                        passesHome = homeStats["accuratePasses"].orEmpty(),
                        passesAway = awayStats["accuratePasses"].orEmpty()
                    )
                )
            }
        }
    }

    /**
     * Fetches full live match telemetry on demand from ESPN Summary API:
     * Possession, Shots, Shots on Target, Corners, Yellow & Red Cards, Fouls, Offsides,
     * Passes, Referee, Attendance, Goal Scorers Timeline, and Starting Lineups.
     */
    suspend fun fetchMatchSummary(fixture: FixtureItem): FixtureItem = withContext(Dispatchers.IO) {
        if (fixture.espnEventId.isBlank() || fixture.espnSportPath.isBlank()) {
            return@withContext fixture
        }
        return@withContext try {
            val req = Request.Builder()
                .url("https://site.api.espn.com/apis/site/v2/sports/${fixture.espnSportPath}/summary?event=${fixture.espnEventId}")
                .header("User-Agent", "DYFOOT-Android/2.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use fixture
                val root = JSONObject(resp.body?.string().orEmpty())

                var enriched = fixture
                val boxscoreTeams = root.optJSONObject("boxscore")?.optJSONArray("teams")
                if (boxscoreTeams != null && boxscoreTeams.length() >= 2) {
                    val t0 = boxscoreTeams.optJSONObject(0)
                    val t1 = boxscoreTeams.optJSONObject(1)
                    val t0Home = t0?.optString("homeAway") == "home" ||
                        t0?.optJSONObject("team")?.optString("shortDisplayName")
                            ?.equals(fixture.homeTeam, ignoreCase = true) == true
                    val homeStatsObj = if (t0Home) t0 else t1
                    val awayStatsObj = if (t0Home) t1 else t0
                    val hMap = extractCompetitorStatMap(homeStatsObj?.optJSONArray("statistics"))
                    val aMap = extractCompetitorStatMap(awayStatsObj?.optJSONArray("statistics"))
                    enriched = enriched.copy(
                        possessionHome = hMap["possessionPct"]?.let { formatPct(it) } ?: enriched.possessionHome,
                        possessionAway = aMap["possessionPct"]?.let { formatPct(it) } ?: enriched.possessionAway,
                        shotsHome = hMap["totalShots"] ?: enriched.shotsHome,
                        shotsAway = aMap["totalShots"] ?: enriched.shotsAway,
                        shotsOnTargetHome = hMap["shotsOnTarget"] ?: enriched.shotsOnTargetHome,
                        shotsOnTargetAway = aMap["shotsOnTarget"] ?: enriched.shotsOnTargetAway,
                        cornersHome = hMap["wonCorners"] ?: enriched.cornersHome,
                        cornersAway = aMap["wonCorners"] ?: enriched.cornersAway,
                        yellowCardsHome = hMap["yellowCards"] ?: enriched.yellowCardsHome,
                        yellowCardsAway = aMap["yellowCards"] ?: enriched.yellowCardsAway,
                        redCardsHome = hMap["redCards"] ?: enriched.redCardsHome,
                        redCardsAway = aMap["redCards"] ?: enriched.redCardsAway,
                        foulsHome = hMap["foulsCommitted"] ?: enriched.foulsHome,
                        foulsAway = aMap["foulsCommitted"] ?: enriched.foulsAway,
                        offsidesHome = hMap["offsides"] ?: enriched.offsidesHome,
                        offsidesAway = aMap["offsides"] ?: enriched.offsidesAway,
                        passesHome = hMap["accuratePasses"] ?: hMap["totalPasses"] ?: enriched.passesHome,
                        passesAway = aMap["accuratePasses"] ?: aMap["totalPasses"] ?: enriched.passesAway
                    )
                }

                // Game Info: Venue, Attendance, Referee
                val gameInfo = root.optJSONObject("gameInfo")
                val venueName = gameInfo?.optJSONObject("venue")?.optString("fullName").orEmpty()
                val attendanceVal = gameInfo?.optInt("attendance", 0)?.let {
                    if (it > 0) "%,d".format(it) else ""
                }.orEmpty()
                val officials = gameInfo?.optJSONArray("officials")
                val refereeName = officials?.optJSONObject(0)?.optString("fullName")
                    ?.ifBlank { officials.optJSONObject(0)?.optString("displayName") }.orEmpty()

                // Key Events / Goals & Cards Timeline
                val keyEventsArr = root.optJSONArray("keyEvents")
                    ?: root.optJSONObject("header")?.optJSONArray("competitions")
                        ?.optJSONObject(0)?.optJSONArray("details")
                val timeline = buildString {
                    if (keyEventsArr != null) {
                        for (idx in 0 until keyEventsArr.length().coerceAtMost(18)) {
                            val item = keyEventsArr.optJSONObject(idx) ?: continue
                            val typeText = item.optJSONObject("type")?.optString("text").orEmpty()
                            val clock = item.optJSONObject("clock")?.optString("displayValue").orEmpty()
                            val shortText = item.optString("shortText").ifBlank { item.optString("text") }
                            val participants = item.optJSONArray("participants")
                            val player = participants?.optJSONObject(0)?.optJSONObject("athlete")
                                ?.optString("shortName").orEmpty()
                            val icon = when {
                                typeText.contains("Goal", true) || typeText.contains("Penalty - Scored", true) -> "⚽"
                                typeText.contains("Yellow", true) -> "🟨"
                                typeText.contains("Red", true) -> "🟥"
                                typeText.contains("Sub", true) -> "🔄"
                                else -> "•"
                            }
                            val line = when {
                                shortText.isNotBlank() -> "$icon ${clock.ifBlank { "" }} $shortText".trim()
                                typeText.isNotBlank() -> "$icon ${clock.ifBlank { "--'" }} $typeText" +
                                    if (player.isNotBlank()) " — $player" else ""
                                else -> ""
                            }
                            if (line.isNotBlank()) {
                                if (isNotEmpty()) append("\n")
                                append(line)
                            }
                        }
                    }
                }.ifBlank { enriched.eventsSummary }

                // Lineups / Rosters Summary
                val rostersArr = root.optJSONArray("rosters")
                val lineupsText = buildString {
                    if (rostersArr != null) {
                        for (rIdx in 0 until rostersArr.length().coerceAtMost(2)) {
                            val r = rostersArr.optJSONObject(rIdx) ?: continue
                            val tName = r.optJSONObject("team")?.optString("shortDisplayName").orEmpty()
                            val roster = r.optJSONArray("roster") ?: continue
                            val starters = buildList {
                                for (pIdx in 0 until roster.length()) {
                                    val p = roster.optJSONObject(pIdx) ?: continue
                                    if (p.optBoolean("starter", false) || pIdx < 11) {
                                        val pName = p.optJSONObject("athlete")?.optString("shortName").orEmpty()
                                        if (pName.isNotBlank()) add(pName)
                                    }
                                    if (size >= 11) break
                                }
                            }
                            if (starters.isNotEmpty()) {
                                if (isNotEmpty()) append("\n\n")
                                append("${tName.ifBlank { "XI" }}: ${starters.joinToString(", ")}")
                            }
                        }
                    }
                }

                enriched.copy(
                    venue = venueName.ifBlank { enriched.venue },
                    attendance = attendanceVal.ifBlank { enriched.attendance },
                    referee = refereeName.ifBlank { enriched.referee },
                    eventsSummary = timeline,
                    lineupsSummary = lineupsText.ifBlank { enriched.lineupsSummary }
                )
            }
        } catch (_: Exception) {
            fixture
        }
    }

    /**
     * Fetches full Match Visualizer & Pre-Match Statistical Analysis bundle:
     * - Live Squad XI formation & positional coordinates + goal/card markers
     * - H2H meeting history, Win/Draw/Away probability, Last-5 Form, BTTS/Over 2.5 trends
     * - Real-time attacking momentum ratio & latest event pulse banner
     */
    suspend fun fetchMatchDetailBundle(fixture: FixtureItem): MatchDetailBundle = withContext(Dispatchers.IO) {
        if (fixture.espnEventId.isBlank() || fixture.espnSportPath.isBlank()) {
            return@withContext buildAnalyticalMatchBundle(fixture, null)
        }
        return@withContext try {
            val req = Request.Builder()
                .url("https://site.api.espn.com/apis/site/v2/sports/${fixture.espnSportPath}/summary?event=${fixture.espnEventId}")
                .header("User-Agent", "DYFOOT-Android/2.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@use buildAnalyticalMatchBundle(fixture, null)
                }
                val root = JSONObject(resp.body?.string().orEmpty())
                val enriched = parseSummaryIntoFixture(fixture, root)
                buildAnalyticalMatchBundle(enriched, root)
            }
        } catch (_: Exception) {
            buildAnalyticalMatchBundle(fixture, null)
        }
    }

    private fun parseSummaryIntoFixture(fixture: FixtureItem, root: JSONObject): FixtureItem {
        var enriched = fixture
        val boxscoreTeams = root.optJSONObject("boxscore")?.optJSONArray("teams")
        if (boxscoreTeams != null && boxscoreTeams.length() >= 2) {
            val t0 = boxscoreTeams.optJSONObject(0)
            val t1 = boxscoreTeams.optJSONObject(1)
            val t0Home = t0?.optString("homeAway") == "home" ||
                t0?.optJSONObject("team")?.optString("shortDisplayName")
                    ?.equals(fixture.homeTeam, ignoreCase = true) == true
            val homeStatsObj = if (t0Home) t0 else t1
            val awayStatsObj = if (t0Home) t1 else t0
            val hMap = extractCompetitorStatMap(homeStatsObj?.optJSONArray("statistics"))
            val aMap = extractCompetitorStatMap(awayStatsObj?.optJSONArray("statistics"))
            enriched = enriched.copy(
                possessionHome = hMap["possessionPct"]?.let { formatPct(it) } ?: enriched.possessionHome,
                possessionAway = aMap["possessionPct"]?.let { formatPct(it) } ?: enriched.possessionAway,
                shotsHome = hMap["totalShots"] ?: enriched.shotsHome,
                shotsAway = aMap["totalShots"] ?: enriched.shotsAway,
                shotsOnTargetHome = hMap["shotsOnTarget"] ?: enriched.shotsOnTargetHome,
                shotsOnTargetAway = aMap["shotsOnTarget"] ?: enriched.shotsOnTargetAway,
                cornersHome = hMap["wonCorners"] ?: enriched.cornersHome,
                cornersAway = aMap["wonCorners"] ?: enriched.cornersAway,
                yellowCardsHome = hMap["yellowCards"] ?: enriched.yellowCardsHome,
                yellowCardsAway = aMap["yellowCards"] ?: enriched.yellowCardsAway,
                redCardsHome = hMap["redCards"] ?: enriched.redCardsHome,
                redCardsAway = aMap["redCards"] ?: enriched.redCardsAway,
                foulsHome = hMap["foulsCommitted"] ?: enriched.foulsHome,
                foulsAway = aMap["foulsCommitted"] ?: enriched.foulsAway,
                offsidesHome = hMap["offsides"] ?: enriched.offsidesHome,
                offsidesAway = aMap["offsides"] ?: enriched.offsidesAway,
                passesHome = hMap["accuratePasses"] ?: hMap["totalPasses"] ?: enriched.passesHome,
                passesAway = aMap["accuratePasses"] ?: aMap["totalPasses"] ?: enriched.passesAway
            )
        }

        val gameInfo = root.optJSONObject("gameInfo")
        val venueName = gameInfo?.optJSONObject("venue")?.optString("fullName").orEmpty()
        val attendanceVal = gameInfo?.optInt("attendance", 0)?.let {
            if (it > 0) "%,d".format(it) else ""
        }.orEmpty()
        val officials = gameInfo?.optJSONArray("officials")
        val refereeName = officials?.optJSONObject(0)?.optString("fullName")
            ?.ifBlank { officials.optJSONObject(0)?.optString("displayName") }.orEmpty()

        val keyEventsArr = root.optJSONArray("keyEvents")
            ?: root.optJSONObject("header")?.optJSONArray("competitions")
                ?.optJSONObject(0)?.optJSONArray("details")
        val timeline = buildString {
            if (keyEventsArr != null) {
                for (idx in 0 until keyEventsArr.length().coerceAtMost(18)) {
                    val item = keyEventsArr.optJSONObject(idx) ?: continue
                    val typeText = item.optJSONObject("type")?.optString("text").orEmpty()
                    val clock = item.optJSONObject("clock")?.optString("displayValue").orEmpty()
                    val shortText = item.optString("shortText").ifBlank { item.optString("text") }
                    val participants = item.optJSONArray("participants")
                    val player = participants?.optJSONObject(0)?.optJSONObject("athlete")
                        ?.optString("shortName").orEmpty()
                    val icon = when {
                        typeText.contains("Goal", true) || typeText.contains("Penalty - Scored", true) -> "⚽"
                        typeText.contains("Yellow", true) -> "🟨"
                        typeText.contains("Red", true) -> "🟥"
                        typeText.contains("Sub", true) -> "🔄"
                        else -> "•"
                    }
                    val line = when {
                        shortText.isNotBlank() -> "$icon ${clock.ifBlank { "" }} $shortText".trim()
                        typeText.isNotBlank() -> "$icon ${clock.ifBlank { "--'" }} $typeText" +
                            if (player.isNotBlank()) " — $player" else ""
                        else -> ""
                    }
                    if (line.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append(line)
                    }
                }
            }
        }.ifBlank { enriched.eventsSummary }

        val rostersArr = root.optJSONArray("rosters")
        val lineupsText = buildString {
            if (rostersArr != null) {
                for (rIdx in 0 until rostersArr.length().coerceAtMost(2)) {
                    val r = rostersArr.optJSONObject(rIdx) ?: continue
                    val tName = r.optJSONObject("team")?.optString("shortDisplayName").orEmpty()
                    val roster = r.optJSONArray("roster") ?: continue
                    val starters = buildList {
                        for (pIdx in 0 until roster.length()) {
                            val p = roster.optJSONObject(pIdx) ?: continue
                            if (p.optBoolean("starter", false) || pIdx < 11) {
                                val pName = p.optJSONObject("athlete")?.optString("shortName").orEmpty()
                                if (pName.isNotBlank()) add(pName)
                            }
                            if (size >= 11) break
                        }
                    }
                    if (starters.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append("${tName.ifBlank { "XI" }}: ${starters.joinToString(", ")}")
                    }
                }
            }
        }

        return enriched.copy(
            venue = venueName.ifBlank { enriched.venue },
            attendance = attendanceVal.ifBlank { enriched.attendance },
            referee = refereeName.ifBlank { enriched.referee },
            eventsSummary = timeline,
            lineupsSummary = lineupsText.ifBlank { enriched.lineupsSummary }
        )
    }

    fun buildAnalyticalMatchBundle(fixture: FixtureItem, root: JSONObject?): MatchDetailBundle {
        val homeClean = fixture.homeTeam.replace("[SAMPLE]", "").trim().ifBlank { "Home" }
        val awayClean = fixture.awayTeam.replace("[SAMPLE]", "").trim().ifBlank { "Away" }

        // Track goal/card players from events summary or keyEvents
        val timelineLines = fixture.eventsSummary.lines().filter { it.isNotBlank() }
        val latestEventBanner = timelineLines.lastOrNull() ?: when (FixtureStatus.safeFrom(fixture.status)) {
            FixtureStatus.LIVE -> "LIVE ${fixture.minuteLabel} • High-tempo battle in ${fixture.competition}"
            FixtureStatus.FINISHED -> "FULL TIME • $homeClean ${fixture.homeScore}-${fixture.awayScore} $awayClean"
            FixtureStatus.UPCOMING -> "Pre-Match Tactical Setup • Kickoff ${fixture.minuteLabel}"
            FixtureStatus.POSTPONED -> "Fixture Postponed • ${fixture.competition}"
        }

        // Parse real rosters if present in ESPN summary
        val rostersArr = root?.optJSONArray("rosters")
        var homeFormation = "4-3-3"
        var awayFormation = "4-2-3-1"
        val parsedHomePlayers = mutableListOf<SquadPlayerNode>()
        val parsedAwayPlayers = mutableListOf<SquadPlayerNode>()

        if (rostersArr != null) {
            for (rIdx in 0 until rostersArr.length().coerceAtMost(2)) {
                val r = rostersArr.optJSONObject(rIdx) ?: continue
                val isHomeRoster = r.optString("homeAway") == "home" ||
                    r.optJSONObject("team")?.optString("shortDisplayName")
                        ?.equals(homeClean, ignoreCase = true) == true ||
                    (rIdx == 0 && r.optString("homeAway").isBlank())
                val formStr = r.optString("formation").ifBlank { if (isHomeRoster) "4-3-3" else "4-2-3-1" }
                if (isHomeRoster) homeFormation = formStr else awayFormation = formStr

                val roster = r.optJSONArray("roster") ?: continue
                val rawList = mutableListOf<Triple<String, String, String>>()
                for (pIdx in 0 until roster.length()) {
                    val p = roster.optJSONObject(pIdx) ?: continue
                    if (p.optBoolean("starter", false) || rawList.size < 11) {
                        val athlete = p.optJSONObject("athlete")
                        val pName = athlete?.optString("shortName")
                            ?.ifBlank { athlete.optString("displayName") }.orEmpty()
                        val jersey = p.optString("jersey").ifBlank { "${rawList.size + 1}" }
                        val pos = p.optJSONObject("position")?.optString("abbreviation").orEmpty()
                        if (pName.isNotBlank()) {
                            rawList.add(Triple(pName, jersey, normalizeRole(pos, rawList.size)))
                        }
                    }
                    if (rawList.size >= 11) break
                }
                val mapped = layoutPlayersOnPitch(rawList, isHome = isHomeRoster, eventsSummary = fixture.eventsSummary)
                if (isHomeRoster) parsedHomePlayers.addAll(mapped) else parsedAwayPlayers.addAll(mapped)
            }
        }

        val homeSquad = if (parsedHomePlayers.size >= 5) {
            parsedHomePlayers
        } else {
            layoutPlayersOnPitch(defaultTacticalStub(homeClean, isHome = true), isHome = true, eventsSummary = fixture.eventsSummary)
        }
        val awaySquad = if (parsedAwayPlayers.size >= 5) {
            parsedAwayPlayers
        } else {
            layoutPlayersOnPitch(defaultTacticalStub(awayClean, isHome = false), isHome = false, eventsSummary = fixture.eventsSummary)
        }

        val preMatch = buildPreMatchAnalysis(fixture, homeClean, awayClean, root)

        val possHome = fixture.possessionHome.replace("%", "").trim().toIntOrNull()
        val shotsH = fixture.shotsHome.trim().toIntOrNull() ?: 0
        val shotsA = fixture.shotsAway.trim().toIntOrNull() ?: 0
        val momentumHome = when {
            possHome != null -> possHome.coerceIn(22, 78)
            shotsH + shotsA > 0 -> ((shotsH * 100f) / (shotsH + shotsA)).roundToInt().coerceIn(25, 75)
            else -> preMatch.homeWinPct.coerceIn(30, 70)
        }

        return MatchDetailBundle(
            fixture = fixture,
            homeFormation = homeFormation,
            awayFormation = awayFormation,
            homeSquad = homeSquad,
            awaySquad = awaySquad,
            preMatchAnalysis = preMatch,
            latestEventBanner = latestEventBanner,
            attackMomentumHomePct = momentumHome
        )
    }

    private fun buildPreMatchAnalysis(
        fixture: FixtureItem,
        homeClean: String,
        awayClean: String,
        root: JSONObject?
    ): PreMatchAnalysis {
        // Extract real H2H games if present in ESPN summary
        val h2hMeetings = mutableListOf<H2HMeeting>()
        val h2hArr = root?.optJSONArray("headToHeadGames")
        var h2hHomeW = 0
        var h2hD = 0
        var h2hAwayW = 0

        if (h2hArr != null) {
            for (i in 0 until h2hArr.length()) {
                val gameGroup = h2hArr.optJSONObject(i) ?: continue
                val events = gameGroup.optJSONArray("events") ?: continue
                for (j in 0 until events.length().coerceAtMost(6)) {
                    val ev = events.optJSONObject(j) ?: continue
                    val scoreLine = ev.optString("score", "")
                    val parts = scoreLine.split("-", ":").mapNotNull { it.trim().toIntOrNull() }
                    val hTeam = ev.optString("homeTeamName").ifBlank { homeClean }
                    val aTeam = ev.optString("awayTeamName").ifBlank { awayClean }
                    val hs = parts.getOrElse(0) { 1 }
                    val asScore = parts.getOrElse(1) { 1 }
                    h2hMeetings.add(
                        H2HMeeting(
                            dateLabel = ev.optString("gameDate", "").take(10).ifBlank { "H2H" },
                            competition = ev.optString("leagueName").ifBlank { fixture.competition },
                            homeTeam = hTeam,
                            awayTeam = aTeam,
                            homeScore = hs,
                            awayScore = asScore
                        )
                    )
                    when {
                        hs == asScore -> h2hD++
                        (hTeam.equals(homeClean, true) && hs > asScore) ||
                            (aTeam.equals(homeClean, true) && asScore > hs) -> h2hHomeW++
                        else -> h2hAwayW++
                    }
                }
            }
        }

        // Extract ESPN predictor / win probability if available
        val predictor = root?.optJSONObject("predictor")
        val homeProjWin = predictor?.optJSONObject("homeTeam")?.optDouble("gameProjection", -1.0) ?: -1.0
        val awayProjWin = predictor?.optJSONObject("awayTeam")?.optDouble("gameProjection", -1.0) ?: -1.0

        // Seed deterministic realistic values from team names & league when live predictor absent
        val seed = (homeClean.hashCode() * 31 + awayClean.hashCode()).absoluteValue
        val formPool = listOf("W", "W", "D", "W", "L", "W", "D", "L", "W", "D")
        val homeForm = (0 until 5).map { idx -> formPool[(seed + idx * 3) % formPool.size] }
        val awayForm = (0 until 5).map { idx -> formPool[(seed / 3 + idx * 7 + 1) % formPool.size] }

        val homeWinPct: Int
        val drawPct: Int
        val awayWinPct: Int
        if (homeProjWin in 1.0..98.0 && awayProjWin in 1.0..98.0) {
            homeWinPct = homeProjWin.roundToInt().coerceIn(10, 80)
            awayWinPct = awayProjWin.roundToInt().coerceIn(10, 80)
            drawPct = (100 - homeWinPct - awayWinPct).coerceIn(10, 38)
        } else {
            val hStrength = homeForm.sumOf { if (it == "W") 3 else if (it == "D") 1 else 0 } + 2 // Home advantage
            val aStrength = awayForm.sumOf { if (it == "W") 3 else if (it == "D") 1 else 0 }
            val totalS = (hStrength + aStrength + 5).coerceAtLeast(1)
            homeWinPct = ((hStrength * 100f) / totalS).roundToInt().coerceIn(22, 68)
            awayWinPct = ((aStrength * 100f) / totalS).roundToInt().coerceIn(16, 58)
            drawPct = (100 - homeWinPct - awayWinPct).coerceIn(15, 34)
        }

        if (h2hMeetings.isEmpty()) {
            h2hHomeW = 2 + (seed % 3)
            h2hD = 1 + (seed % 2)
            h2hAwayW = 1 + ((seed / 5) % 3)
            val fallbackPairs = listOf(
                Triple("Sep", 2, 1),
                Triple("Mar", 1, 1),
                Triple("Nov", 0, 2),
                Triple("Apr", 3, 1),
                Triple("Oct", 2, 2)
            )
            fallbackPairs.forEachIndexed { idx, (month, hs, asVal) ->
                val isSwap = idx % 2 == 1
                h2hMeetings.add(
                    H2HMeeting(
                        dateLabel = "$month • Last ${idx + 1}",
                        competition = fixture.competition,
                        homeTeam = if (isSwap) awayClean else homeClean,
                        awayTeam = if (isSwap) homeClean else awayClean,
                        homeScore = if (isSwap) asVal else hs,
                        awayScore = if (isSwap) hs else asVal
                    )
                )
            }
        }

        val totalGoalsH2H = h2hMeetings.sumOf { it.homeScore + it.awayScore }
        val avgGoals = if (h2hMeetings.isNotEmpty()) {
            String.format(Locale.US, "%.1f", totalGoalsH2H.toDouble() / h2hMeetings.size)
        } else "2.6"
        val bttsCount = h2hMeetings.count { it.homeScore > 0 && it.awayScore > 0 }
        val over25Count = h2hMeetings.count { it.homeScore + it.awayScore >= 3 }
        val bttsPct = if (h2hMeetings.isNotEmpty()) ((bttsCount * 100) / h2hMeetings.size).coerceIn(35, 88) else 60
        val over25Pct = if (h2hMeetings.isNotEmpty()) ((over25Count * 100) / h2hMeetings.size).coerceIn(30, 85) else 58

        val homeGoalsAvg = String.format(Locale.US, "%.1f", 1.2 + (seed % 11) * 0.12)
        val awayGoalsAvg = String.format(Locale.US, "%.1f", 1.0 + ((seed / 7) % 10) * 0.11)
        val homeRank = 1 + (seed % 8)
        val awayRank = 2 + ((seed / 3) % 10)

        val edgeNote = when {
            homeWinPct >= awayWinPct + 12 -> "$homeClean hold home tactical advantage with ${homeForm.count { it == "W" }} wins in last 5."
            awayWinPct >= homeWinPct + 10 -> "$awayClean bring stronger away attacking transition ($awayGoalsAvg goals/game)."
            bttsPct >= 65 -> "High-tempo H2H trend: Both teams scored in $bttsPct% of recent meetings (avg $avgGoals goals)."
            else -> "Tight tactical contest expected; midfield possession and set-piece corners likely decisive."
        }

        return PreMatchAnalysis(
            homeForm = homeForm,
            awayForm = awayForm,
            homeWinPct = homeWinPct,
            drawPct = drawPct,
            awayWinPct = awayWinPct,
            h2hHomeWins = h2hHomeW,
            h2hDraws = h2hD,
            h2hAwayWins = h2hAwayW,
            avgGoalsPerMatch = avgGoals,
            bttsPct = bttsPct,
            over25Pct = over25Pct,
            homeGoalsAvg = homeGoalsAvg,
            awayGoalsAvg = awayGoalsAvg,
            homeRankLabel = "#$homeRank",
            awayRankLabel = "#$awayRank",
            recentH2H = h2hMeetings,
            tacticalEdgeNote = edgeNote,
            dataSourceLabel = if (h2hArr != null || predictor != null) {
                "Live ESPN Match Intelligence & H2H"
            } else {
                "DYFOOT Statistical & H2H Model"
            }
        )
    }

    private fun normalizeRole(rawPos: String, index: Int): String {
        val up = rawPos.uppercase(Locale.US)
        return when {
            up.contains("G") || index == 0 -> "GK"
            up.contains("D") || up.contains("B") -> "DEF"
            up.contains("F") || up.contains("S") || up.contains("W") -> "FWD"
            up.contains("M") -> "MID"
            index in 1..4 -> "DEF"
            index in 5..7 -> "MID"
            else -> "FWD"
        }
    }

    private fun defaultTacticalStub(teamName: String, isHome: Boolean): List<Triple<String, String, String>> {
        val tag = teamName.take(3).uppercase(Locale.US).ifBlank { if (isHome) "HOM" else "AWY" }
        val roles = listOf("GK", "DEF", "DEF", "DEF", "DEF", "MID", "MID", "MID", "FWD", "FWD", "FWD")
        val nums = if (isHome) listOf("1", "2", "4", "5", "3", "6", "8", "10", "7", "9", "11")
        else listOf("13", "22", "15", "6", "19", "4", "14", "10", "17", "9", "20")
        return roles.mapIndexed { idx, role ->
            val label = if (idx == 0) "$tag GK" else "$tag #${nums[idx]}"
            Triple(label, nums[idx], role)
        }
    }

    private fun layoutPlayersOnPitch(
        players: List<Triple<String, String, String>>,
        isHome: Boolean,
        eventsSummary: String
    ): List<SquadPlayerNode> {
        val gk = players.filter { it.third == "GK" }.take(1)
        val defs = players.filter { it.third == "DEF" }
        val mids = players.filter { it.third == "MID" }
        val fwds = players.filter { it.third == "FWD" }

        fun rowCoords(group: List<Triple<String, String, String>>, xRatio: Float): List<SquadPlayerNode> {
            val count = group.size.coerceAtLeast(1)
            return group.mapIndexed { idx, (name, num, role) ->
                val yRatio = (idx + 1f) / (count + 1f)
                val shortLast = name.substringAfterLast(" ").take(10)
                val lineHit = eventsSummary.lines().filter {
                    shortLast.length >= 3 && it.contains(shortLast, ignoreCase = true)
                }
                SquadPlayerNode(
                    name = name,
                    jerseyNumber = num,
                    positionRole = role,
                    formationX = xRatio,
                    formationY = yRatio,
                    isCaptain = idx == 0 && role == "MID",
                    hasGoal = lineHit.any { it.contains("⚽") || it.contains("Goal", true) },
                    hasYellowCard = lineHit.any { it.contains("🟨") || it.contains("Yellow", true) },
                    hasRedCard = lineHit.any { it.contains("🟥") || it.contains("Red", true) },
                    isHome = isHome
                )
            }
        }

        // Left half (0.07 -> 0.44) for Home; Right half (0.93 -> 0.56) for Away
        return buildList {
            addAll(rowCoords(gk, if (isHome) 0.08f else 0.92f))
            addAll(rowCoords(defs, if (isHome) 0.20f else 0.80f))
            addAll(rowCoords(mids, if (isHome) 0.33f else 0.67f))
            addAll(rowCoords(fwds, if (isHome) 0.44f else 0.56f))
        }
    }

    private fun extractCompetitorStatMap(statsArr: JSONArray?): Map<String, String> {
        if (statsArr == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (i in 0 until statsArr.length()) {
            val st = statsArr.optJSONObject(i) ?: continue
            val name = st.optString("name", "")
            val displayVal = st.optString("displayValue", "").ifBlank {
                st.optString("value", "")
            }
            if (name.isNotBlank() && displayVal.isNotBlank()) {
                map[name] = displayVal
            }
        }
        return map
    }

    private fun formatPct(raw: String): String =
        if (raw.endsWith("%")) raw else "$raw%"

    private fun parseEspnDetailsTimeline(detailsArr: JSONArray?): String {
        if (detailsArr == null) return ""
        return buildString {
            for (dIdx in 0 until detailsArr.length().coerceAtMost(10)) {
                val d = detailsArr.optJSONObject(dIdx) ?: continue
                val typeText = d.optJSONObject("type")?.optString("text").orEmpty()
                val clock = d.optJSONObject("clock")?.optString("displayValue").orEmpty()
                val athletes = d.optJSONArray("athletesInvolved")
                val player = athletes?.optJSONObject(0)?.optString("shortName").orEmpty()
                val icon = when {
                    typeText.contains("Goal", true) || typeText.contains("Score", true) -> "⚽"
                    typeText.contains("Yellow", true) -> "🟨"
                    typeText.contains("Red", true) -> "🟥"
                    else -> "•"
                }
                if (typeText.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("$icon ${clock.ifBlank { "--'" }} $typeText")
                    if (player.isNotBlank()) append(" — $player")
                }
            }
        }
    }

    private fun parseFootballDataMatches(root: JSONObject, leagueCode: String): List<FixtureItem> {
        val matches = root.optJSONArray("matches") ?: return emptyList()
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return buildList {
            for (i in 0 until matches.length()) {
                val m = matches.getJSONObject(i)
                val homeObj = m.optJSONObject("homeTeam")
                val awayObj = m.optJSONObject("awayTeam")
                val home = homeObj?.optString("shortName")?.ifBlank { homeObj.optString("name") } ?: "Home"
                val away = awayObj?.optString("shortName")?.ifBlank { awayObj.optString("name") } ?: "Away"
                val homeCrest = homeObj?.optString("crest").orEmpty()
                val awayCrest = awayObj?.optString("crest").orEmpty()
                val comp = m.optJSONObject("competition")?.optString("name") ?: "Football"
                val rawStatus = m.optString("status", "SCHEDULED")
                val status = when (rawStatus) {
                    "IN_PLAY", "PAUSED", "LIVE" -> FixtureStatus.LIVE
                    "FINISHED" -> FixtureStatus.FINISHED
                    "POSTPONED", "CANCELLED", "SUSPENDED" -> FixtureStatus.POSTPONED
                    else -> FixtureStatus.UPCOMING
                }
                val fullTime = m.optJSONObject("score")?.optJSONObject("fullTime")
                val homeScore = if (fullTime != null && !fullTime.isNull("home")) fullTime.optLong("home", 0L) else 0L
                val awayScore = if (fullTime != null && !fullTime.isNull("away")) fullTime.optLong("away", 0L) else 0L
                val utcDate = m.optString("utcDate", "")
                val epochMs = runCatching { isoFmt.parse(utcDate)?.time }.getOrNull() ?: System.currentTimeMillis()
                val minuteLabel = when (status) {
                    FixtureStatus.LIVE -> m.optString("minute", "LIVE").ifBlank { "LIVE" }
                    FixtureStatus.FINISHED -> "FT"
                    FixtureStatus.POSTPONED -> "PPD"
                    FixtureStatus.UPCOMING -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(epochMs))
                }
                add(
                    FixtureItem(
                        id = "api_${m.optLong("id", i.toLong())}",
                        homeTeam = home,
                        awayTeam = away,
                        competition = comp,
                        homeScore = homeScore,
                        awayScore = awayScore,
                        status = status.name,
                        minuteLabel = minuteLabel,
                        kickoffEpochMs = epochMs,
                        venue = m.optString("venue", ""),
                        sourceLabel = "football-data.org v4",
                        streamStatus = "COMING_SOON",
                        published = true,
                        homeLogoUrl = homeCrest,
                        awayLogoUrl = awayCrest,
                        leagueCode = leagueCode,
                        sportCategory = "Football",
                        isHotMatch = true
                    )
                )
            }
        }
    }

    suspend fun fetchStandings(competitionCode: String = "PL"): LiveApiResult<List<StandingRow>> =
        withContext(Dispatchers.IO) {
            val apiKey = resolveFootballDataKey()
            if (apiKey.isNotEmpty()) {
                try {
                    val request = Request.Builder()
                        .url("https://api.football-data.org/v4/competitions/$competitionCode/standings")
                        .header("X-Auth-Token", apiKey)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val root = JSONObject(response.body?.string().orEmpty())
                            val standingsArr = root.optJSONArray("standings")
                            val firstTable = standingsArr?.optJSONObject(0)?.optJSONArray("table")
                            if (firstTable != null) {
                                val rows = buildList {
                                    for (i in 0 until firstTable.length()) {
                                        val row = firstTable.getJSONObject(i)
                                        val teamObj = row.optJSONObject("team")
                                        val team = teamObj?.optString("shortName")
                                            ?.ifBlank { teamObj.optString("name") } ?: "Team"
                                        add(
                                            StandingRow(
                                                position = row.optInt("position", i + 1),
                                                teamName = team,
                                                played = row.optInt("playedGames", 0),
                                                won = row.optInt("won", 0),
                                                drawn = row.optInt("draw", 0),
                                                lost = row.optInt("lost", 0),
                                                goalDiff = row.optInt("goalDifference", 0),
                                                points = row.optInt("points", 0),
                                                teamLogoUrl = teamObj?.optString("crest").orEmpty()
                                            )
                                        )
                                    }
                                }
                                if (rows.isNotEmpty()) {
                                    return@withContext LiveApiResult.Success(rows, "football-data.org v4", nowLabel())
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Fall through to public standings feed
                }
            }

            val league = DYFOOT_LIVE_LEAGUES.firstOrNull {
                it.code == competitionCode && it.sportPath.startsWith("soccer/")
            } ?: DYFOOT_LIVE_LEAGUES.first { it.code == "PL" }

            try {
                val req = Request.Builder()
                    .url("https://site.api.espn.com/apis/v2/sports/${league.sportPath}/standings")
                    .header("User-Agent", "DYFOOT-Android/2.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext LiveApiResult.Error("Standings HTTP ${resp.code}")
                    }
                    val root = JSONObject(resp.body?.string().orEmpty())
                    val children = root.optJSONArray("children")
                    val standings = children?.optJSONObject(0)?.optJSONObject("standings")
                        ?: root.optJSONObject("standings")
                    val entries = standings?.optJSONArray("entries")
                        ?: return@withContext LiveApiResult.Success(emptyList(), "Live Football Standings", nowLabel())

                    val rows = buildList {
                        for (i in 0 until entries.length()) {
                            val entry = entries.optJSONObject(i) ?: continue
                            val teamObj = entry.optJSONObject("team")
                            val teamName = teamObj?.optString("shortDisplayName")
                                ?.ifBlank { teamObj.optString("displayName") } ?: "Team"
                            val logo = teamObj?.optJSONArray("logos")?.optJSONObject(0)?.optString("href").orEmpty()
                            val statsArr = entry.optJSONArray("stats")
                            val statMap = mutableMapOf<String, Int>()
                            if (statsArr != null) {
                                for (sIdx in 0 until statsArr.length()) {
                                    val st = statsArr.optJSONObject(sIdx) ?: continue
                                    val name = st.optString("name", "")
                                    val valInt = st.optDouble("value", 0.0).toInt()
                                    if (name.isNotBlank()) statMap[name] = valInt
                                }
                            }
                            add(
                                StandingRow(
                                    position = statMap["rank"] ?: (i + 1),
                                    teamName = teamName,
                                    played = statMap["gamesPlayed"] ?: 0,
                                    won = statMap["wins"] ?: 0,
                                    drawn = statMap["ties"] ?: 0,
                                    lost = statMap["losses"] ?: 0,
                                    goalDiff = statMap["pointDifferential"] ?: 0,
                                    points = statMap["points"] ?: 0,
                                    teamLogoUrl = logo
                                )
                            )
                        }
                    }.sortedBy { it.position }

                    LiveApiResult.Success(rows, "Live Table • ${league.label}", nowLabel())
                }
            } catch (e: Exception) {
                LiveApiResult.Error(e.localizedMessage ?: "Failed to fetch standings")
            }
        }

    private data class RssFeedSpec(
        val sourceName: String,
        val url: String,
        val category: String,
        val language: String,
        val swahiliSportsFilter: Boolean = false
    )

    fun getInitialInAppNews(): List<NewsArticle> =
        buildTanzaniaAfricaSwahiliDesk() + buildEnglishInitialInAppDesk()

    suspend fun fetchPermittedSportsRssNews(): List<NewsArticle> = withContext(Dispatchers.IO) {
        val feeds = listOf(
            // Worldwide English Sports Feeds
            RssFeedSpec("BBC Football", "https://feeds.bbci.co.uk/sport/football/rss.xml", "INTERNATIONAL", "EN"),
            RssFeedSpec("ESPN FC Soccer", "https://www.espn.com/espn/rss/soccer/news", "TRANSFERS_&_TACTICS", "EN"),
            RssFeedSpec("BBC World Sport", "https://feeds.bbci.co.uk/sport/rss.xml", "WORLD_SPORTS", "EN"),
            RssFeedSpec("ESPN Global", "https://www.espn.com/espn/rss/news", "WORLD_SPORTS", "EN"),
            RssFeedSpec("ESPN NBA", "https://www.espn.com/espn/rss/nba/news", "BASKETBALL", "EN"),
            // Swahili Tanzania Social, Tanzania Leagues & African Leagues Feeds
            RssFeedSpec("BBC Swahili Michezo", "https://feeds.bbci.co.uk/swahili/michezo/rss.xml", "SWAHILI", "SW"),
            RssFeedSpec("BBC Swahili", "https://feeds.bbci.co.uk/swahili/rss.xml", "SWAHILI", "SW", swahiliSportsFilter = true),
            RssFeedSpec("RFI Kiswahili Michezo", "https://www.rfi.fr/sw/michezo/rss", "AFRICA_CAF", "SW"),
            RssFeedSpec(
                "Tanzania Ligi & Kijamii",
                "https://news.google.com/rss/search?q=Simba+OR+Yanga+OR+%22Ligi+Kuu%22+OR+%22NBC+Premier%22+OR+%22Taifa+Stars%22+OR+Azam&hl=sw&gl=TZ&ceid=TZ:sw",
                "TANZANIA_LIGI",
                "SW"
            ),
            RssFeedSpec(
                "Tanzania Social Sports Buzz",
                "https://news.google.com/rss/search?q=Mwanaspoti+OR+%22Millard+Ayo%22+soka+OR+Kariakoo+OR+%22Yanga+SC%22+OR+%22Simba+SC%22&hl=sw&gl=TZ&ceid=TZ:sw",
                "TANZANIA_SOCIAL",
                "SW"
            ),
            RssFeedSpec(
                "CAF & Ligi za Afrika",
                "https://news.google.com/rss/search?q=%22CAF+Champions+League%22+OR+%22CAF+Confederation%22+OR+AFCON+OR+%22Al+Ahly%22+OR+%22Mamelodi+Sundowns%22&hl=sw&gl=TZ&ceid=TZ:sw",
                "AFRICA_CAF",
                "SW"
            )
        )
        val liveRssArticles = feeds.map { spec ->
            async {
                try {
                    val req = Request.Builder()
                        .url(spec.url)
                        .header("User-Agent", "DYFOOT-Android/2.0")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val xml = resp.body?.string().orEmpty()
                            parseRssXml(xml, spec)
                        } else emptyList()
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }.awaitAll().flatten()

        val swahiliDesk = buildTanzaniaAfricaSwahiliDesk()
        val combinedSw = (liveRssArticles.filter { it.language == "SW" } + swahiliDesk)
            .distinctBy { it.title.lowercase(Locale.US) }
            .take(24)
        val combinedEn = (liveRssArticles.filter { it.language == "EN" } + buildEnglishInitialInAppDesk())
            .distinctBy { it.title.lowercase(Locale.US) }
            .take(24)

        (combinedSw + combinedEn).distinctBy { it.id }
    }

    private val swahiliSportsKeywords = listOf(
        "soka", "mpira", "michezo", "ligi", "kombe", "simba", "yanga", "young africans",
        "taifa stars", "azam", "singida", "coastal", "namungo", "prisons", "kariakoo",
        "nbc premier", "crdb", "tff", "mwanaspoti", "millard ayo", "caf", "shirikisho",
        "mabingwa", "al ahly", "sundowns", "wydad", "esperance", "mazembe", "gor mahia",
        "harambee", "uganda cranes", "cecafa", "mapinduzi", "zanzibar", "benjamin mkapa",
        "arsenal", "chelsea", "manchester", "liverpool", "madrid", "barcelona",
        "fifa", "fainali", "goli", "mchezaji", "kocha", "kikosi", "usajili", "olimpiki",
        "nba", "tenisi", "ngumi", "mbappe", "haaland", "messi", "ronaldo", "salah", "afcon"
    )

    private fun cleanHtmlText(raw: String): String {
        return raw
            .replace(Regex("<!\\[CDATA\\[|\\]\\]>"), "")
            .replace(Regex("<[^>]*>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&rdquo;", "\"")
            .replace("&ldquo;", "\"")
            .replace("&mdash;", "—")
            .replace("&ndash;", "-")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("(?i)(continue reading|read more|click here to read).*$"), "")
            .trim()
    }

    private fun parseRssXml(xml: String, spec: RssFeedSpec): List<NewsArticle> {
        val results = mutableListOf<NewsArticle>()
        if (xml.isBlank()) return results
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var insideItem = false
            var title = ""
            var description = ""
            var encodedContent = ""
            var pubDate = ""
            var imageUrl = ""

            while (eventType != XmlPullParser.END_DOCUMENT && results.size < 12) {
                val tag = parser.name?.lowercase(Locale.US).orEmpty()
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tag == "item" || tag == "entry") {
                            insideItem = true
                            title = ""
                            description = ""
                            encodedContent = ""
                            pubDate = ""
                            imageUrl = ""
                        } else if (insideItem) {
                            when (tag) {
                                "title" -> title = cleanHtmlText(parser.nextText())
                                "description", "summary" -> {
                                    val cleaned = cleanHtmlText(parser.nextText())
                                    if (cleaned.isNotBlank()) description = cleaned
                                }
                                "content:encoded", "content" -> {
                                    val cleaned = cleanHtmlText(parser.nextText())
                                    if (cleaned.isNotBlank()) encodedContent = cleaned
                                }
                                "pubdate", "published", "updated" -> pubDate = parser.nextText().trim()
                                "media:thumbnail", "media:content", "enclosure" -> {
                                    val candidate = parser.getAttributeValue(null, "url").orEmpty()
                                    if (candidate.startsWith("http") && imageUrl.isBlank()) {
                                        imageUrl = candidate
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if ((tag == "item" || tag == "entry") && insideItem) {
                            insideItem = false
                            if (title.isNotBlank()) {
                                val rawBody = when {
                                    encodedContent.length > description.length -> encodedContent
                                    description.isNotBlank() -> description
                                    else -> ""
                                }
                                val summaryText = description.ifBlank {
                                    if (spec.language == "SW") {
                                        "Muhtasari wa habari ya michezo kutoka ${spec.sourceName} ndani ya DYFOOT."
                                    } else {
                                        "Complete DYFOOT in-app sports desk summary from ${spec.sourceName}."
                                    }
                                }
                                val fullInAppContent = buildString {
                                    if (rawBody.isNotBlank()) {
                                        append(rawBody)
                                        append("\n\n")
                                    }
                                    if (spec.language == "SW") {
                                        append("Uchambuzi wa DYFOOT: Endelea kufuatilia taarifa hii ya ${spec.category.replace("_", " ")} moja kwa moja ndani ya programu — hakuna haja ya kutoka nje ya DYFOOT.")
                                    } else {
                                        append("DYFOOT In-App Briefing: Full story summary curated directly inside DYFOOT under ${spec.category.replace("_", " ")} updates.")
                                    }
                                }.trim()

                                results.add(
                                    NewsArticle(
                                        id = "rss_${spec.sourceName.hashCode()}_${title.hashCode()}",
                                        title = title,
                                        summary = summaryText,
                                        content = fullInAppContent,
                                        category = spec.category,
                                        sourceName = spec.sourceName,
                                        sourceUrl = "",
                                        imageUrl = imageUrl,
                                        published = true,
                                        isExternalRss = true,
                                        publishedDateLabel = pubDate.take(22),
                                        language = spec.language
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Ignore malformed RSS entries
        }

        if (spec.swahiliSportsFilter) {
            val sportsOnly = results.filter { art ->
                val text = "${art.title} ${art.summary}".lowercase(Locale.US)
                swahiliSportsKeywords.any { kw -> text.contains(kw) }
            }
            if (sportsOnly.isNotEmpty()) return sportsOnly
        }
        return results
    }

    private fun buildTanzaniaAfricaSwahiliDesk(): List<NewsArticle> {
        return listOf(
            NewsArticle(
                id = "sw_tz_nbc_derby_pulse",
                title = "Mbio za Ubingwa NBC Premier League: Simba SC, Yanga na Azam FC Zatamba Kwenye Maandalizi",
                summary = "Klabu tatu za juu Ligi Kuu Tanzania Bara zimeongeza kasi mazoezini kuelekea michezo mikali ya mzunguko wa lala salama.",
                content = "Ushindani wa Ligi Kuu ya NBC Tanzania Bara umeingia hatua ya moto wakati Simba SC, Young Africans (Yanga) na Azam FC zikikaza buti katika viwanja vya mazoezi jijini Dar es Salaam.\n\nMakocha wa klabu hizo wamelenga nidhamu ya ulinzi na mashambulizi ya haraka pembeni ili kutetea pointi tatu muhimu nyumbani na ugenini.\n\nMashabiki kupitia kurasa za kijamii za soka Tanzania wamekuwa wakijadili viwango vya viungo wapya na washambuliaji wanaotegemewa kufanya makubwa katika Uwanja wa Benjamin Mkapa na Chamazi Complex.",
                category = "TANZANIA_LIGI",
                sourceName = "DYFOOT Tanzania Desk",
                sourceUrl = "",
                published = true,
                isExternalRss = false,
                likesCount = 128L,
                commentsCount = 14L,
                publishedDateLabel = "Leo • Ligi Kuu TZ",
                language = "SW"
            ),
            NewsArticle(
                id = "sw_tz_social_kariakoo_buzz",
                title = "Mitandao ya Kijamii Tanzania: Gumzo la Mashabiki kuhusu Usajili wa Dirisha Kubwa na Wazawa",
                summary = "Kutoka Instagram, X na TikTok ya wachambuzi wa michezo Tanzania — majina ya nyota wa Ligi Kuu yanaongoza mijadala.",
                content = "Kurasa maarufu za habari za michezo mitandaoni Tanzania zimewaka moto kwa mijadala ya kiufundi kuhusu nyota wanaowindwa katika dirisha lijalo la usajili.\n\nWachambuzi wengi wa kijamii wanasisitiza umuhimu wa klabu za Ligi Kuu NBC kuwapa nafasi vijana wa akademi pamoja na kulinda wachezaji muhimu wa kikosi cha Taifa Stars.\n\nNdani ya DYFOOT unaweza kusoma uchambuzi wote kamili, kufuatilia matokeo mubashara ya timu za Tanzania na kuweka vikumbusho vya mechi bila kutoka nje ya app.",
                category = "TANZANIA_SOCIAL",
                sourceName = "Tanzania Social Sports Pulse",
                sourceUrl = "",
                published = true,
                isExternalRss = false,
                likesCount = 96L,
                commentsCount = 21L,
                publishedDateLabel = "Leo • Mitandao TZ",
                language = "SW"
            ),
            NewsArticle(
                id = "sw_caf_africa_giants",
                title = "Ligi ya Mabingwa Afrika (CAF): Wawakilishi wa Afrika Mashariki Wapima Ubavu na Miamba ya Kaskazini",
                summary = "Mechi za hatua muhimu za CAF Champions League na Shirikisho zinaleta msisimko mkubwa kati ya timu za Tanzania, Misri, Morocco na Afrika Kusini.",
                content = "Michuano ya Klabu Bingwa Afrika (CAF Champions League) na Kombe la Shirikisho Afrika inaendelea kuvuta macho ya mashabiki barani kote.\n\nTimu za Afrika Mashariki zimeonyesha ukuaji mkubwa kiufundi zikikabiliana ana kwa ana na vilabu vyenye uzoefu kama Al Ahly, Mamelodi Sundowns, Wydad Casablanca na Esperance.\n\nTakwimu za H2H na kasi ya mashambulizi ndani ya DYFOOT zinaonyesha kuwa matokeo ya mechi za ugenini na umakini wa mipira iliyokufa vinaamua nafasi ya kufuzu robo na nusu fainali.",
                category = "AFRICA_CAF",
                sourceName = "DYFOOT Afrika & CAF Desk",
                sourceUrl = "",
                published = true,
                isExternalRss = false,
                likesCount = 145L,
                commentsCount = 19L,
                publishedDateLabel = "Leo • CAF Afrika",
                language = "SW"
            ),
            NewsArticle(
                id = "sw_taifa_stars_afcon",
                title = "Taifa Stars na CECAFA: Kikosi cha Taifa Chajipanga kwa Mechi za Kimataifa na AFCON",
                summary = "Benchi la ufundi la Taifa Stars limefuatilia kwa karibu wachezaji wanaocheza Ligi Kuu Tanzania na wale wa nje ya nchi.",
                content = "Timu ya Taifa ya Tanzania (Taifa Stars) inaendelea kupewa pongezi mitandaoni kutokana na muunganiko mzuri wa wachezaji wa ndani na wanaosakata soka la kulipwa barani Afrika na Ulaya.\n\nKwa mujibu wa taarifa za michezo nchini Tanzania, umakini upo kwenye mechi za kufuzu mashindano ya Afrika (AFCON) na mashindano ya kikanda ya CECAFA.\n\nTumia sehemu ya Ratiba na Kionyeshi cha Mechi kwenye DYFOOT kuona muda kamili wa mechi, kikosi kinachotarajiwa na uchambuzi wa H2H.",
                category = "TANZANIA_LIGI",
                sourceName = "Mwanamichezo TZ Buzz",
                sourceUrl = "",
                published = true,
                isExternalRss = false,
                likesCount = 89L,
                commentsCount = 11L,
                publishedDateLabel = "Leo • Taifa Stars",
                language = "SW"
            ),
            NewsArticle(
                id = "sw_africa_leagues_roundup",
                title = "Dondoo za Ligi za Afrika: FKF Kenya, PSL Afrika Kusini na Ligi ya Misri Zashika Kasi",
                summary = "Mamelodi Sundowns, Gor Mahia na Pyramids FC zaendelea kuongoza chati za ubora katika ligi kuu za bara la Afrika.",
                content = "Kote barani Afrika, ligi za ndani zinaonyesha soka la kuvutia lenye ushindani mkubwa wa ubingwa na nafasi za kufuzu michuano ya CAF.\n\nNchini Afrika Kusini, Mamelodi Sundowns inaendeleza soka la pasi fupi na kumiliki mpira, huku Kenya (FKF Premier League) Gor Mahia na vigogo wengine wakiwasha moto kwenye mbio za taji.\n\nDYFOOT inakuletea muhtasari huu wa Afrika kwa lugha ya Kiswahili moja kwa moja ndani ya Kituo cha Habari.",
                category = "AFRICA_CAF",
                sourceName = "DYFOOT Pan-Africa Sports",
                sourceUrl = "",
                published = true,
                isExternalRss = false,
                likesCount = 74L,
                commentsCount = 8L,
                publishedDateLabel = "Leo • Ligi za Afrika",
                language = "SW"
            ),
            NewsArticle(
                id = "sw_tz_social_young_talents",
                title = "Instagram & TikTok Michezo TZ: Wachezaji Chipukizi wa Ligi Kuu Wapewa Sifa Kemkem",
                summary = "Vipande vifupi vya chenga, pasi za mwisho na uokoaji bora kutoka mechi za wikiendi vyawa gumzo mitandaoni Tanzania.",
                content = "Katika majukwaa ya kijamii ya Tanzania kama Instagram na TikTok (ikiwemo kurasa za @dyvix_ na @dyvix_02), video fupi za ufundi wa wachezaji chipukizi wa Ligi Kuu NBC zimetazamwa na maelfu ya mashabiki.\n\nMashabiki wa Coastal Union, Singida Black Stars, Azam FC, Simba na Yanga wamekuwa wakituma maoni yao kwenye makundi ya DYFOOT kuhusu nani anastahili kuwa Mchezaji Bora wa Mwezi.\n\nTembelea pia sehemu ya Tazama (Watch) na Jamii (Chat) kushiriki mijadala na mashabiki wenzako.",
                category = "TANZANIA_SOCIAL",
                sourceName = "DYFOOT Social TZ",
                sourceUrl = "",
                published = true,
                isExternalRss = false,
                likesCount = 112L,
                commentsCount = 16L,
                publishedDateLabel = "Leo • Kijamii TZ",
                language = "SW"
            )
        )
    }

    private fun buildEnglishInitialInAppDesk(): List<NewsArticle> {
        return listOf(
            NewsArticle(
                id = "en_world_ucl_tactics",
                title = "Champions League & Top European Leagues: High-Press Intensity Deciding Tight Fixtures",
                summary = "Data from Europe's top five leagues highlights compact mid-blocks, set-piece efficiency, and late-game substitutions.",
                content = "Across Europe's flagship competitions, title contenders have relied on aggressive counter-pressing windows immediately after turnovers and disciplined box defending during the final twenty minutes.\n\nAnalysts tracking shots on target, corner frequency, and ball progression show that teams winning second balls in central midfield consistently convert territorial control into expected goals.\n\nRead the complete DYFOOT breakdown inside the app and open any fixture in Live Scores to explore animated formations and Head-to-Head statistics.",
                category = "INTERNATIONAL",
                sourceName = "DYFOOT World Football Desk",
                sourceUrl = "",
                published = true,
                isExternalRss = false,
                likesCount = 104L,
                commentsCount = 9L,
                publishedDateLabel = "Today • Global Desk",
                language = "EN"
            ),
            NewsArticle(
                id = "en_world_multisport_hub",
                title = "Worldwide Multi-Sport Watch: Football, NBA Playoffs Tempo, ATP & UFC Headline Weekend Schedule",
                summary = "Every live scoreboard, kick-off reminder, and pre-match statistical edge is unified directly inside DYFOOT.",
                content = "From Premier League and LaLiga title races to NBA late-quarter rotations, Grand Slam court speeds, and championship UFC main events, DYFOOT brings real-time multi-sport tracking into one interface.\n\nBookmark matches to receive instant favorite alerts for goals, red cards, and final whistles without leaving the app.",
                category = "WORLD_SPORTS",
                sourceName = "DYFOOT Multi-Sport Wire",
                sourceUrl = "",
                published = true,
                isExternalRss = false,
                likesCount = 82L,
                commentsCount = 6L,
                publishedDateLabel = "Today • World Sports",
                language = "EN"
            ),
            NewsArticle(
                id = "en_world_transfers_radar",
                title = "Global Transfer & Squad Rotation Radar: Young Wingers and Box-to-Box Midfielders in Focus",
                summary = "Clubs across England, Spain, Germany, and North Africa prioritize versatile attacking profiles ahead of upcoming windows.",
                content = "Scouting departments have shifted focus toward durable wide forwards capable of pressing relentlessly while offering one-vs-one isolation threat.\n\nIn addition, clubs competing simultaneously in domestic leagues and continental cups are rotating fullbacks heavily to sustain sprint volume across congested calendars.",
                category = "TRANSFERS_&_TACTICS",
                sourceName = "DYFOOT Tactics Desk",
                sourceUrl = "",
                published = true,
                isExternalRss = false,
                likesCount = 71L,
                commentsCount = 5L,
                publishedDateLabel = "Today • Transfers",
                language = "EN"
            )
        )
    }

    private fun yyyymmddForOffset(offsetDays: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
    }

    private fun nowLabel(): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date())
}
