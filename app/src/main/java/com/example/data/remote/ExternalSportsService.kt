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

    private fun resolveDataSportsKey(): String =
        sanitizeConfiguredKey(BuildConfig.DATASPORTS_API_KEY, "YOUR_DATASPORTS_API_KEY")

    private fun resolveLiveFootballApiKey(): String =
        sanitizeConfiguredKey(BuildConfig.LIVE_FOOTBALL_API_KEY, "YOUR_LIVE_FOOTBALL_API_KEY")

    fun hasFootballDataApiKey(): Boolean = resolveFootballDataKey().isNotEmpty()

    fun hasDataSportsApiKey(): Boolean = resolveDataSportsKey().isNotEmpty()

    fun hasLiveFootballApiKey(): Boolean = resolveLiveFootballApiKey().isNotEmpty()

    fun hasMobileMoneyGatewayKey(): Boolean = resolveMobileMoneyKey().isNotEmpty()

    /**
     * Full Worldwide Sports Live Sync:
     * Queries all configured real-time providers in parallel and merges without duplicates:
     * 1) DataSportsAPI (/v2/livescores)
     * 2) Live Football API (live-football-api.com/api/v1/matches)
     * 3) football-data.org v4
     * 4) Public multi-sport scoreboards
     */
    suspend fun fetchLiveMatches(
        leagueCode: String = "ALL",
        dateOffsetDays: Int = 0
    ): LiveApiResult<List<FixtureItem>> = withContext(Dispatchers.IO) {
        val fdKey = resolveFootballDataKey()
        val dsKey = resolveDataSportsKey()
        val lfaKey = resolveLiveFootballApiKey()
        val dateParam = yyyymmddForOffset(dateOffsetDays)
        val isoDateParam = isoDateForOffset(dateOffsetDays)
        val selectedLeague = DYFOOT_LIVE_LEAGUES.firstOrNull { it.code == leagueCode }
        val isFootballTarget = leagueCode == "ALL" || selectedLeague?.sportCategory == "Football"

        val dsDeferred = async {
            if (dsKey.isNotEmpty() && dateOffsetDays == 0 && isFootballTarget) {
                fetchDataSportsLivescores(dsKey, leagueCode)
            } else emptyList()
        }
        val lfaDeferred = async {
            if (lfaKey.isNotEmpty() && isFootballTarget) {
                fetchLiveFootballApiMatches(lfaKey, leagueCode, isoDateParam)
            } else emptyList()
        }
        val fdDeferred = async {
            if (fdKey.isNotEmpty() && dateOffsetDays == 0) {
                fetchFootballDataMatches(fdKey, leagueCode)
            } else emptyList()
        }

        val targets = if (leagueCode == "ALL") {
            DYFOOT_LIVE_LEAGUES.filter { it.sportPath.isNotBlank() }
        } else {
            DYFOOT_LIVE_LEAGUES.filter { it.code == leagueCode && it.sportPath.isNotBlank() }
        }
        val espnDeferred = targets.map { league ->
            async { fetchEspnScoreboardForLeague(league, dateParam) }
        }

        val dsFixtures = dsDeferred.await()
        val lfaFixtures = lfaDeferred.await()
        val fdFixtures = fdDeferred.await()
        val espnFixtures = espnDeferred.awaitAll().flatten()

        val sorted = deduplicateFixtures(dsFixtures + lfaFixtures + fdFixtures + espnFixtures).sortedWith(
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

        val activeProviders = buildList {
            if (dsKey.isNotEmpty()) add("DataSportsAPI")
            if (lfaKey.isNotEmpty()) add("Live Football API")
            if (fdKey.isNotEmpty()) add("football-data.org")
            add("World Feed")
        }
        LiveApiResult.Success(sorted, activeProviders.joinToString(" + "), nowLabel())
    }

    private fun fetchFootballDataMatches(apiKey: String, leagueCode: String): List<FixtureItem> {
        return try {
            val selectedLeague = DYFOOT_LIVE_LEAGUES.firstOrNull { it.code == leagueCode }
            val fdCode = selectedLeague?.footballDataCode.orEmpty()
            if (leagueCode != "ALL" && fdCode.isBlank()) return emptyList()
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
                if (!response.isSuccessful) return emptyList()
                val root = JSONObject(response.body?.string().orEmpty())
                parseFootballDataMatches(root, leagueCode)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchDataSportsLivescores(apiKey: String, leagueCode: String): List<FixtureItem> {
        return try {
            val request = Request.Builder()
                .url("https://api.datasportsapi.com/v2/livescores?sport=football")
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                val root = if (body.trimStart().startsWith("[")) {
                    JSONObject().put("data", JSONArray(body))
                } else {
                    JSONObject(body)
                }
                parseDataSportsLivescores(root, leagueCode)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseDataSportsLivescores(root: JSONObject, leagueCode: String = "ALL"): List<FixtureItem> {
        val items = root.optJSONArray("data")
            ?: root.optJSONArray("matches")
            ?: root.optJSONArray("events")
            ?: return emptyList()

        return buildList {
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val sportVal = obj.optString("sport", "football")
                if (sportVal.isNotBlank() && !sportVal.contains("football", true) && !sportVal.contains("soccer", true)) {
                    continue
                }
                val rawId = obj.optString("id").ifBlank { obj.optLong("id", i.toLong()).toString() }
                val homeObj = obj.optJSONObject("home") ?: obj.optJSONObject("homeTeam") ?: obj.optJSONObject("home_team")
                val awayObj = obj.optJSONObject("away") ?: obj.optJSONObject("awayTeam") ?: obj.optJSONObject("away_team")
                val homeName = homeObj?.optString("name")?.ifBlank { homeObj.optString("shortName") }
                    ?: obj.optString("homeTeam").ifBlank { obj.optString("home") }.ifBlank { "Home" }
                val awayName = awayObj?.optString("name")?.ifBlank { awayObj.optString("shortName") }
                    ?: obj.optString("awayTeam").ifBlank { obj.optString("away") }.ifBlank { "Away" }
                val homeLogo = homeObj?.optString("logo")?.ifBlank { homeObj.optString("crest") }.orEmpty()
                val awayLogo = awayObj?.optString("logo")?.ifBlank { awayObj.optString("crest") }.orEmpty()

                val leagueObj = obj.optJSONObject("league") ?: obj.optJSONObject("competition")
                val compName = leagueObj?.optString("name")?.ifBlank { obj.optString("competition") }
                    ?: obj.optString("league").ifBlank { "Football" }
                if (!matchesLeagueFilter(compName, leagueCode)) continue

                val homeScore = extractScoreValue(obj, "homeScore", "home_score", homeSide = true)
                val awayScore = extractScoreValue(obj, "awayScore", "away_score", homeSide = false)

                val statusObj = obj.optJSONObject("status")
                val statusType = statusObj?.optString("type")?.ifBlank { statusObj.optString("phase") }
                    ?: obj.optString("status", "notstarted")
                val statusDesc = statusObj?.optString("label")?.ifBlank { statusObj.optString("description") }.orEmpty()
                val fixtureStatus = mapRawStatus(statusType.ifBlank { statusDesc })

                val kickoffRaw = obj.optString("kickoff").ifBlank { obj.optString("startTime") }
                    .ifBlank { obj.optString("utcDate") }
                val epochMs = parseFlexibleEpochMs(kickoffRaw)

                val clockObj = obj.optJSONObject("clock")
                val clockLabel = clockObj?.optString("label")
                    ?.ifBlank { clockObj.optString("displayValue") }
                    ?: obj.optString("minute", "")
                val minuteLabel = when (fixtureStatus) {
                    FixtureStatus.LIVE -> clockLabel.ifBlank { statusDesc.ifBlank { "LIVE" } }
                    FixtureStatus.FINISHED -> statusDesc.ifBlank { "FT" }
                    FixtureStatus.POSTPONED -> "PPD"
                    FixtureStatus.UPCOMING -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(epochMs))
                }

                add(
                    FixtureItem(
                        id = "ds_$rawId",
                        homeTeam = homeName,
                        awayTeam = awayName,
                        competition = compName,
                        homeScore = homeScore,
                        awayScore = awayScore,
                        status = fixtureStatus.name,
                        minuteLabel = minuteLabel,
                        kickoffEpochMs = epochMs,
                        venue = obj.optJSONObject("venue")?.optString("name") ?: obj.optString("venue", ""),
                        sourceLabel = "DataSportsAPI • $compName",
                        streamStatus = "COMING_SOON",
                        published = true,
                        homeLogoUrl = homeLogo,
                        awayLogoUrl = awayLogo,
                        leagueCode = if (leagueCode == "ALL") inferLeagueCode(compName) else leagueCode,
                        sportCategory = "Football",
                        isHotMatch = isMarqueeCompetition(compName)
                    )
                )
            }
        }
    }

    private fun fetchLiveFootballApiMatches(
        apiKey: String,
        leagueCode: String,
        isoDate: String
    ): List<FixtureItem> {
        return try {
            val url = "https://live-football-api.com/api/v1/matches?api_key=$apiKey&date=$isoDate&lang=en"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                val root = if (body.trimStart().startsWith("[")) {
                    JSONObject().put("matches", JSONArray(body))
                } else {
                    JSONObject(body)
                }
                parseLiveFootballApiMatches(root, leagueCode)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseLiveFootballApiMatches(root: JSONObject, leagueCode: String = "ALL"): List<FixtureItem> {
        val dataObj = root.optJSONObject("data")
        val matches = root.optJSONArray("matches")
            ?: dataObj?.optJSONArray("matches")
            ?: root.optJSONArray("data")
            ?: root.optJSONArray("events")
            ?: return emptyList()

        return buildList {
            for (i in 0 until matches.length()) {
                val m = matches.optJSONObject(i) ?: continue
                val rawId = m.optString("match_id").ifBlank {
                    m.optString("id").ifBlank { m.optLong("id", i.toLong()).toString() }
                }
                val homeObj = m.optJSONObject("home") ?: m.optJSONObject("homeTeam") ?: m.optJSONObject("home_team")
                val awayObj = m.optJSONObject("away") ?: m.optJSONObject("awayTeam") ?: m.optJSONObject("away_team")

                val homeName = homeObj?.optString("name")
                    ?.ifBlank { homeObj.optString("shortName") }
                    ?.ifBlank { homeObj.optString("short_name") }
                    ?: m.optString("home_name").ifBlank { m.optString("homeTeam") }.ifBlank { "Home" }
                val awayName = awayObj?.optString("name")
                    ?.ifBlank { awayObj.optString("shortName") }
                    ?.ifBlank { awayObj.optString("short_name") }
                    ?: m.optString("away_name").ifBlank { m.optString("awayTeam") }.ifBlank { "Away" }
                val homeLogo = homeObj?.optString("logo")
                    ?.ifBlank { homeObj.optString("crest") }
                    ?.ifBlank { homeObj.optString("badge") }
                    ?: m.optString("home_logo")
                val awayLogo = awayObj?.optString("logo")
                    ?.ifBlank { awayObj.optString("crest") }
                    ?.ifBlank { awayObj.optString("badge") }
                    ?: m.optString("away_logo")

                val leagueObj = m.optJSONObject("league") ?: m.optJSONObject("competition")
                val compName = leagueObj?.optString("name")?.ifBlank { m.optString("league_name") }
                    ?: m.optString("competition").ifBlank { "Football" }
                if (!matchesLeagueFilter(compName, leagueCode)) continue

                val homeScore = extractScoreValue(m, "homeScore", "home_score", homeSide = true)
                val awayScore = extractScoreValue(m, "awayScore", "away_score", homeSide = false)

                val statusObj = m.optJSONObject("status")
                val isLiveBool = statusObj?.optBoolean("is_live", false) == true || m.optBoolean("is_live", false)
                val statusStr = if (isLiveBool) {
                    "LIVE"
                } else {
                    statusObj?.optString("type")
                        ?.ifBlank { statusObj.optString("short") }
                        ?.ifBlank { statusObj.optString("display") }
                        ?: m.optString("status", "NS")
                }
                val fixtureStatus = mapRawStatus(statusStr)

                val kickoffRaw = m.optString("start_time")
                    .ifBlank { m.optString("utcDate") }
                    .ifBlank { m.optString("kickoff") }
                    .ifBlank { m.optString("date") }
                val epochMs = parseFlexibleEpochMs(kickoffRaw)

                val liveClock = m.optString("minute")
                    .ifBlank { m.optString("clock") }
                    .ifBlank { statusObj?.optString("display").orEmpty() }
                val minuteLabel = when (fixtureStatus) {
                    FixtureStatus.LIVE -> liveClock.ifBlank { "LIVE" }
                    FixtureStatus.FINISHED -> statusObj?.optString("short").orEmpty().ifBlank { "FT" }
                    FixtureStatus.POSTPONED -> "PPD"
                    FixtureStatus.UPCOMING -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(epochMs))
                }

                add(
                    FixtureItem(
                        id = "lfa_$rawId",
                        homeTeam = homeName,
                        awayTeam = awayName,
                        competition = compName,
                        homeScore = homeScore,
                        awayScore = awayScore,
                        status = fixtureStatus.name,
                        minuteLabel = minuteLabel,
                        kickoffEpochMs = epochMs,
                        venue = m.optJSONObject("venue")?.optString("name") ?: m.optString("venue", ""),
                        sourceLabel = "Live Football API • $compName",
                        streamStatus = "COMING_SOON",
                        published = true,
                        homeLogoUrl = homeLogo,
                        awayLogoUrl = awayLogo,
                        leagueCode = if (leagueCode == "ALL") inferLeagueCode(compName) else leagueCode,
                        sportCategory = "Football",
                        isHotMatch = isMarqueeCompetition(compName)
                    )
                )
            }
        }
    }

    internal fun deduplicateFixtures(fixtures: List<FixtureItem>): List<FixtureItem> {
        val merged = linkedMapOf<String, FixtureItem>()
        for (item in fixtures) {
            val key = "${normalizeTeamKey(item.homeTeam)}__${normalizeTeamKey(item.awayTeam)}"
            val existing = merged[key]
            if (existing == null) {
                merged[key] = item
            } else {
                val preferIncomingScore = FixtureStatus.safeFrom(item.status) == FixtureStatus.LIVE &&
                    FixtureStatus.safeFrom(existing.status) != FixtureStatus.LIVE
                merged[key] = existing.copy(
                    homeScore = if (preferIncomingScore || item.homeScore > existing.homeScore) item.homeScore else existing.homeScore,
                    awayScore = if (preferIncomingScore || item.awayScore > existing.awayScore) item.awayScore else existing.awayScore,
                    status = if (preferIncomingScore) item.status else existing.status,
                    minuteLabel = if (preferIncomingScore || existing.minuteLabel.isBlank()) item.minuteLabel else existing.minuteLabel,
                    homeLogoUrl = existing.homeLogoUrl.ifBlank { item.homeLogoUrl },
                    awayLogoUrl = existing.awayLogoUrl.ifBlank { item.awayLogoUrl },
                    venue = existing.venue.ifBlank { item.venue },
                    espnEventId = existing.espnEventId.ifBlank { item.espnEventId },
                    espnSportPath = existing.espnSportPath.ifBlank { item.espnSportPath },
                    eventsSummary = existing.eventsSummary.ifBlank { item.eventsSummary },
                    possessionHome = existing.possessionHome.ifBlank { item.possessionHome },
                    possessionAway = existing.possessionAway.ifBlank { item.possessionAway },
                    shotsHome = existing.shotsHome.ifBlank { item.shotsHome },
                    shotsAway = existing.shotsAway.ifBlank { item.shotsAway },
                    cornersHome = existing.cornersHome.ifBlank { item.cornersHome },
                    cornersAway = existing.cornersAway.ifBlank { item.cornersAway }
                )
            }
        }
        return merged.values.toList()
    }

    private fun normalizeTeamKey(raw: String): String =
        raw.lowercase(Locale.US)
            .replace(Regex("\\b(fc|sc|cf|afc|ac|as|ss|cd|ud|rc|bk)\\b"), "")
            .replace(Regex("[^a-z0-9]"), "")

    private fun extractScoreValue(obj: JSONObject, camelKey: String, snakeKey: String, homeSide: Boolean): Long {
        val sideKey = if (homeSide) "home" else "away"
        val scoreObj = obj.optJSONObject("score")
        val nestedSide = obj.optJSONObject(camelKey)
        return when {
            nestedSide != null && nestedSide.has("current") -> nestedSide.optLong("current", 0L)
            obj.has(camelKey) && obj.opt(camelKey) is Number -> obj.optLong(camelKey, 0L)
            obj.has(snakeKey) && obj.opt(snakeKey) is Number -> obj.optLong(snakeKey, 0L)
            scoreObj?.optJSONObject("fullTime")?.has(sideKey) == true &&
                !scoreObj.optJSONObject("fullTime")!!.isNull(sideKey) ->
                scoreObj.optJSONObject("fullTime")!!.optLong(sideKey, 0L)
            scoreObj?.has(sideKey) == true && !scoreObj.isNull(sideKey) -> scoreObj.optLong(sideKey, 0L)
            else -> 0L
        }
    }

    private fun mapRawStatus(raw: String): FixtureStatus {
        val upper = raw.trim().uppercase(Locale.US)
        return when {
            upper in setOf("INPROGRESS", "IN_PROGRESS", "IN_PLAY", "LIVE", "PAUSED", "HT", "1H", "2H", "ET", "PEN") ||
                upper.contains("INPROGRESS") || upper.contains("LIVE") -> FixtureStatus.LIVE
            upper in setOf("FINISHED", "FT", "AET", "PEN_FT", "FULLTIME", "COMPLETE", "COMPLETED", "ENDED") ||
                upper.contains("FINISH") -> FixtureStatus.FINISHED
            upper in setOf("POSTPONED", "PPD", "PST", "CANCELLED", "CANCELED", "SUSPENDED", "ABANDONED") -> FixtureStatus.POSTPONED
            else -> FixtureStatus.UPCOMING
        }
    }

    private fun parseFlexibleEpochMs(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in formats) {
            val fmt = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            runCatching { fmt.parse(raw)?.time }.getOrNull()?.let { return it }
        }
        return raw.toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1000L else it }
            ?: System.currentTimeMillis()
    }

    private fun matchesLeagueFilter(competitionName: String, leagueCode: String): Boolean {
        if (leagueCode == "ALL") return true
        val option = DYFOOT_LIVE_LEAGUES.firstOrNull { it.code == leagueCode } ?: return true
        if (competitionName.contains(option.label, ignoreCase = true)) return true
        return when (leagueCode) {
            "PL" -> competitionName.contains("Premier League", true) && !competitionName.contains("NBC", true)
            "PD" -> competitionName.contains("La Liga", true) || competitionName.contains("Primera", true)
            "SA" -> competitionName.contains("Serie A", true)
            "BL1" -> competitionName.contains("Bundesliga", true)
            "FL1" -> competitionName.contains("Ligue 1", true)
            "CL" -> competitionName.contains("Champions League", true) && !competitionName.contains("CAF", true)
            "EL" -> competitionName.contains("Europa League", true)
            else -> false
        }
    }

    private fun inferLeagueCode(competitionName: String): String {
        return DYFOOT_LIVE_LEAGUES.firstOrNull { opt ->
            opt.code != "ALL" && competitionName.contains(opt.label, ignoreCase = true)
        }?.code ?: "ALL"
    }

    private fun isMarqueeCompetition(comp: String): Boolean {
        val c = comp.lowercase(Locale.US)
        return c.contains("premier league") || c.contains("champions league") ||
            c.contains("la liga") || c.contains("serie a") || c.contains("bundesliga") ||
            c.contains("europa") || c.contains("caf") || c.contains("nbc") || c.contains("world cup")
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
        val lfaKey = resolveLiveFootballApiKey()
        if (fixture.id.startsWith("lfa_") && lfaKey.isNotEmpty()) {
            val lfaFixture = runCatching {
                val matchId = fixture.id.removePrefix("lfa_")
                val req = Request.Builder()
                    .url("https://live-football-api.com/api/v1/live_match_details?api_key=$lfaKey&match_id=$matchId")
                    .header("Accept", "application/json")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use fixture
                    val root = JSONObject(resp.body?.string().orEmpty())
                    parseLiveFootballApiDetailsIntoFixture(fixture, root)
                }
            }.getOrDefault(fixture)
            if (lfaFixture.espnEventId.isBlank() || lfaFixture.espnSportPath.isBlank()) {
                return@withContext buildAnalyticalMatchBundle(lfaFixture, null)
            }
        }

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

    private fun parseLiveFootballApiDetailsIntoFixture(fixture: FixtureItem, root: JSONObject): FixtureItem {
        val data = root.optJSONObject("data") ?: root
        val eventsArr = data.optJSONArray("events") ?: data.optJSONArray("timeline")
        val timeline = buildString {
            if (eventsArr != null) {
                for (i in 0 until eventsArr.length().coerceAtMost(20)) {
                    val ev = eventsArr.optJSONObject(i) ?: continue
                    val min = ev.optString("minute").ifBlank { ev.optString("time") }
                    val type = ev.optString("type").ifBlank { ev.optString("event") }
                    val player = ev.optString("player").ifBlank { ev.optString("player_name") }
                    val icon = when {
                        type.contains("goal", true) -> "⚽"
                        type.contains("yellow", true) -> "🟨"
                        type.contains("red", true) -> "🟥"
                        type.contains("sub", true) -> "🔄"
                        else -> "•"
                    }
                    val line = "$icon ${min.ifBlank { "--'" }} ${type.ifBlank { "" }} ${if (player.isNotBlank()) "— $player" else ""}".trim()
                    if (line.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append(line)
                    }
                }
            }
        }.ifBlank { fixture.eventsSummary }

        val stats = data.optJSONObject("statistics") ?: data.optJSONObject("stats")
        val homeStats = stats?.optJSONObject("home")
        val awayStats = stats?.optJSONObject("away")
        return fixture.copy(
            eventsSummary = timeline,
            possessionHome = homeStats?.optString("possession")?.ifBlank { fixture.possessionHome } ?: fixture.possessionHome,
            possessionAway = awayStats?.optString("possession")?.ifBlank { fixture.possessionAway } ?: fixture.possessionAway,
            shotsHome = homeStats?.optString("shots")?.ifBlank { fixture.shotsHome } ?: fixture.shotsHome,
            shotsAway = awayStats?.optString("shots")?.ifBlank { fixture.shotsAway } ?: fixture.shotsAway,
            cornersHome = homeStats?.optString("corners")?.ifBlank { fixture.cornersHome } ?: fixture.cornersHome,
            cornersAway = awayStats?.optString("corners")?.ifBlank { fixture.cornersAway } ?: fixture.cornersAway
        )
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
        val homeClean = fixture.homeTeam.trim().ifBlank { "Home" }
        val awayClean = fixture.awayTeam.trim().ifBlank { "Away" }

        // Track real goal/card events from provider timeline
        val timelineLines = fixture.eventsSummary.lines().filter { it.isNotBlank() }
        val latestEventBanner = timelineLines.lastOrNull() ?: when (FixtureStatus.safeFrom(fixture.status)) {
            FixtureStatus.LIVE -> "LIVE ${fixture.minuteLabel} • ${fixture.competition}"
            FixtureStatus.FINISHED -> "FULL TIME • $homeClean ${fixture.homeScore}-${fixture.awayScore} $awayClean"
            FixtureStatus.UPCOMING -> "Kickoff ${fixture.minuteLabel} • ${fixture.competition}"
            FixtureStatus.POSTPONED -> "Fixture Postponed • ${fixture.competition}"
        }

        // Parse real rosters from live provider summary; do NOT fabricate stub lineups if absent.
        val rostersArr = root?.optJSONArray("rosters")
        var homeFormation = ""
        var awayFormation = ""
        val parsedHomePlayers = mutableListOf<SquadPlayerNode>()
        val parsedAwayPlayers = mutableListOf<SquadPlayerNode>()

        if (rostersArr != null) {
            for (rIdx in 0 until rostersArr.length().coerceAtMost(2)) {
                val r = rostersArr.optJSONObject(rIdx) ?: continue
                val isHomeRoster = r.optString("homeAway") == "home" ||
                    r.optJSONObject("team")?.optString("shortDisplayName")
                        ?.equals(homeClean, ignoreCase = true) == true ||
                    (rIdx == 0 && r.optString("homeAway").isBlank())
                val formStr = r.optString("formation").orEmpty()
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

        val homeSquad = if (parsedHomePlayers.size >= 5) parsedHomePlayers else emptyList()
        val awaySquad = if (parsedAwayPlayers.size >= 5) parsedAwayPlayers else emptyList()

        val preMatch = buildPreMatchAnalysis(fixture, homeClean, awayClean, root)

        val possHome = fixture.possessionHome.replace("%", "").trim().toIntOrNull()
        val shotsH = fixture.shotsHome.trim().toIntOrNull() ?: 0
        val shotsA = fixture.shotsAway.trim().toIntOrNull() ?: 0
        val momentumHome = when {
            possHome != null -> possHome.coerceIn(22, 78)
            shotsH + shotsA > 0 -> ((shotsH * 100f) / (shotsH + shotsA)).roundToInt().coerceIn(25, 75)
            preMatch != null && preMatch.homeWinPct > 0 -> preMatch.homeWinPct.coerceIn(25, 75)
            else -> 50
        }

        return MatchDetailBundle(
            fixture = fixture,
            homeFormation = homeFormation.ifBlank { if (homeSquad.isNotEmpty()) "XI" else "" },
            awayFormation = awayFormation.ifBlank { if (awaySquad.isNotEmpty()) "XI" else "" },
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
    ): PreMatchAnalysis? {
        if (root == null) return null

        // Extract real H2H games if present in provider summary
        val h2hMeetings = mutableListOf<H2HMeeting>()
        val h2hArr = root.optJSONArray("headToHeadGames")
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
                    if (parts.size < 2) continue
                    val hTeam = ev.optString("homeTeamName").ifBlank { homeClean }
                    val aTeam = ev.optString("awayTeamName").ifBlank { awayClean }
                    val hs = parts[0]
                    val asScore = parts[1]
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

        // Extract real win probability from provider predictor
        val predictor = root.optJSONObject("predictor")
        val homeProjWin = predictor?.optJSONObject("homeTeam")?.optDouble("gameProjection", -1.0) ?: -1.0
        val awayProjWin = predictor?.optJSONObject("awayTeam")?.optDouble("gameProjection", -1.0) ?: -1.0
        val hasPredictor = homeProjWin in 1.0..98.0 && awayProjWin in 1.0..98.0

        if (h2hMeetings.isEmpty() && !hasPredictor) {
            return null
        }

        val homeWinPct: Int
        val drawPct: Int
        val awayWinPct: Int
        if (hasPredictor) {
            homeWinPct = homeProjWin.roundToInt().coerceIn(10, 80)
            awayWinPct = awayProjWin.roundToInt().coerceIn(10, 80)
            drawPct = (100 - homeWinPct - awayWinPct).coerceIn(10, 38)
        } else {
            val totalH2H = (h2hHomeW + h2hD + h2hAwayW).coerceAtLeast(1)
            homeWinPct = ((h2hHomeW * 100f) / totalH2H).roundToInt()
            drawPct = ((h2hD * 100f) / totalH2H).roundToInt()
            awayWinPct = (100 - homeWinPct - drawPct).coerceAtLeast(0)
        }

        val totalGoalsH2H = h2hMeetings.sumOf { it.homeScore + it.awayScore }
        val avgGoals = if (h2hMeetings.isNotEmpty()) {
            String.format(Locale.US, "%.1f", totalGoalsH2H.toDouble() / h2hMeetings.size)
        } else "--"
        val bttsCount = h2hMeetings.count { it.homeScore > 0 && it.awayScore > 0 }
        val over25Count = h2hMeetings.count { it.homeScore + it.awayScore >= 3 }
        val bttsPct = if (h2hMeetings.isNotEmpty()) ((bttsCount * 100) / h2hMeetings.size) else 0
        val over25Pct = if (h2hMeetings.isNotEmpty()) ((over25Count * 100) / h2hMeetings.size) else 0

        val edgeNote = when {
            h2hMeetings.isNotEmpty() -> "$homeClean vs $awayClean • ${h2hMeetings.size} verified H2H meetings ($avgGoals goals/match)."
            else -> "Live win projection powered by official match telemetry."
        }

        return PreMatchAnalysis(
            homeForm = emptyList(),
            awayForm = emptyList(),
            homeWinPct = homeWinPct,
            drawPct = drawPct,
            awayWinPct = awayWinPct,
            h2hHomeWins = h2hHomeW,
            h2hDraws = h2hD,
            h2hAwayWins = h2hAwayW,
            avgGoalsPerMatch = avgGoals,
            bttsPct = bttsPct,
            over25Pct = over25Pct,
            homeGoalsAvg = "",
            awayGoalsAvg = "",
            homeRankLabel = "",
            awayRankLabel = "",
            recentH2H = h2hMeetings,
            tacticalEdgeNote = edgeNote,
            dataSourceLabel = "Live Official Match Intelligence"
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

        val combinedSw = liveRssArticles.filter { it.language == "SW" }
            .distinctBy { it.title.lowercase(Locale.US) }
            .take(24)
        val combinedEn = liveRssArticles.filter { it.language == "EN" }
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

    private fun yyyymmddForOffset(offsetDays: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
    }

    private fun isoDateForOffset(offsetDays: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    private fun nowLabel(): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date())
}
