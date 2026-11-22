package com.example.data.model

data class SquadPlayerNode(
    val name: String,
    val jerseyNumber: String,
    val positionRole: String, // GK, DEF, MID, FWD
    val formationX: Float,    // Normalized 0f..1f across pitch width (left=Home GK, right=Away GK or vertical)
    val formationY: Float,    // Normalized 0f..1f across pitch height
    val isCaptain: Boolean = false,
    val hasGoal: Boolean = false,
    val hasYellowCard: Boolean = false,
    val hasRedCard: Boolean = false,
    val isHome: Boolean = true
)

data class H2HMeeting(
    val dateLabel: String,
    val competition: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int,
    val awayScore: Int
)

data class PreMatchAnalysis(
    val homeForm: List<String>,         // e.g. ["W", "W", "D", "W", "L"]
    val awayForm: List<String>,         // e.g. ["L", "D", "W", "W", "D"]
    val homeWinPct: Int,                // 0..100
    val drawPct: Int,                   // 0..100
    val awayWinPct: Int,                // 0..100
    val h2hHomeWins: Int,
    val h2hDraws: Int,
    val h2hAwayWins: Int,
    val avgGoalsPerMatch: String,       // e.g. "2.8"
    val bttsPct: Int,                   // Both Teams To Score %
    val over25Pct: Int,                 // Over 2.5 Goals %
    val homeGoalsAvg: String,
    val awayGoalsAvg: String,
    val homeRankLabel: String,
    val awayRankLabel: String,
    val recentH2H: List<H2HMeeting>,
    val tacticalEdgeNote: String,
    val dataSourceLabel: String
)

data class MatchDetailBundle(
    val fixture: FixtureItem,
    val homeFormation: String = "4-3-3",
    val awayFormation: String = "4-2-3-1",
    val homeSquad: List<SquadPlayerNode> = emptyList(),
    val awaySquad: List<SquadPlayerNode> = emptyList(),
    val preMatchAnalysis: PreMatchAnalysis? = null,
    val latestEventBanner: String = "",
    val attackMomentumHomePct: Int = 52
)
