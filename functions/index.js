const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const DATABASE_ID = "ai-studio-android-dyfoot-31014079-85ea-4f7c-85bb-8c3293784f8e";

function topicForMatch(matchId) {
  const sanitized = String(matchId || "")
    .trim()
    .replace(/[^a-zA-Z0-9\-_.~%]/g, "_")
    .slice(0, 80) || "general";
  return `dyfoot_match_${sanitized}`;
}

/**
 * Triggers real-time Firebase Cloud Messaging notifications ONLY for users
 * who marked the fixture as a favourite (either via topic subscription
 * `dyfoot_match_<id>` or via `favoriteMatchIds` in `fcm_tokens`).
 *
 * Detects:
 *  - GOAL events (homeScore or awayScore increased)
 *  - FINAL_SCORE events (status transitioned to FINISHED)
 */
exports.onFavoriteMatchEventPush = onDocumentUpdated(
  {
    document: "fixtures/{fixtureId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!before || !after) return;

    const fixtureId = event.params.fixtureId;
    const prevHome = Number(before.homeScore || 0);
    const prevAway = Number(before.awayScore || 0);
    const nextHome = Number(after.homeScore || 0);
    const nextAway = Number(after.awayScore || 0);

    const goalScored = nextHome > prevHome || nextAway > prevAway;
    const becameFinished = before.status !== "FINISHED" && after.status === "FINISHED";
    const becameLive = before.status === "UPCOMING" && after.status === "LIVE";

    let eventType = null;
    if (goalScored) {
      eventType = "GOAL";
    } else if (becameFinished) {
      eventType = "FINAL_SCORE";
    } else if (becameLive) {
      eventType = "KICKOFF";
    }

    if (!eventType) return;

    const homeTeam = String(after.homeTeam || "Home").replace("[SAMPLE]", "").trim();
    const awayTeam = String(after.awayTeam || "Away").replace("[SAMPLE]", "").trim();
    const minuteLabel = String(after.minuteLabel || (eventType === "FINAL_SCORE" ? "FT" : ""));
    const competition = String(after.competition || "DYFOOT Live");
    const scorerTeam = nextHome > prevHome ? homeTeam : nextAway > prevAway ? awayTeam : "";

    const dataPayload = {
      matchId: String(fixtureId),
      eventType,
      homeTeam,
      awayTeam,
      homeScore: String(nextHome),
      awayScore: String(nextAway),
      minuteLabel,
      competition,
      scorer: scorerTeam,
    };

    const topic = topicForMatch(fixtureId);
    const messaging = getMessaging();

    // 1. Send to per-match FCM topic (subscribed when user taps Favourite star)
    const defaultTitle =
      eventType === "GOAL"
        ? `GOAL! ${homeTeam} ${nextHome} - ${nextAway} ${awayTeam}`
        : eventType === "FINAL_SCORE"
          ? `FINAL SCORE (FT): ${homeTeam} ${nextHome} - ${nextAway} ${awayTeam}`
          : `KICKOFF LIVE: ${homeTeam} vs ${awayTeam}`;

    const defaultBody =
      eventType === "GOAL"
        ? `${scorerTeam ? `${scorerTeam} scored ` : ""}(${minuteLabel}) • ${competition}`.trim()
        : eventType === "FINAL_SCORE"
          ? `Match ended • ${competition}`
          : `${competition} has kicked off!`;

    await messaging.send({
      topic,
      data: dataPayload,
      notification: {
        title: defaultTitle,
        body: defaultBody,
      },
      android: {
        priority: "high",
        notification: {
          channelId: "dyfoot_live_matches",
        },
      },
    });

    // 2. Also target registered tokens whose favoriteMatchIds array includes fixtureId
    //    with localized Swahili/English copy if needed.
    const db = getFirestore(DATABASE_ID);
    const tokenSnap = await db
      .collectionGroup("fcm_tokens")
      .where("favoriteMatchIds", "array-contains", fixtureId)
      .get();

    if (!tokenSnap.empty) {
      const tokens = tokenSnap.docs
        .map((doc) => doc.get("token"))
        .filter((t) => typeof t === "string" && t.length >= 8);

      if (tokens.length > 0) {
        await messaging.sendEachForMulticast({
          tokens,
          data: dataPayload,
          android: {
            priority: "high",
          },
        });
      }
    }
  }
);
