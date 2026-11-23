const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_dyvix";
const OWNER_EMAIL = "dyvix02@gmail.com";
const FREE_UID = "free_user_1";
const VIP_UID = "vip_user_1";
const OTHER_UID = "other_user_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 5000) {
  return new Date(Date.now() - msOffset);
}

function futureDate(msOffset = 3600_000) {
  return new Date(Date.now() + msOffset);
}

function ownerDb(emailVerified = true) {
  return testEnv
    .authenticatedContext(OWNER_UID, {
      email: OWNER_EMAIL,
      email_verified: emailVerified,
    })
    .firestore();
}

function userDb(uid, email = `${uid}@gmail.com`) {
  return testEnv
    .authenticatedContext(uid, {
      email,
      email_verified: true,
    })
    .firestore();
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated requests are denied across all collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("tips").where("published", "==", true).get());
  await assertFails(unauth.collection("vip_tips_secrets").doc("tip_1").get());
  await assertFails(unauth.collection("news").where("published", "==", true).get());
  await assertFails(unauth.collection("users").doc(FREE_UID).get());
  await assertFails(unauth.collection("payments").where("userId", "==", FREE_UID).get());
});

test("Owner verified email (dyvix02@gmail.com) gets OWNER role; unverified email spoof is rejected", async () => {
  const created = pastDate();
  const spoofDb = ownerDb(false);
  await assertFails(
    spoofDb.collection("users").doc(OWNER_UID).set({
      userId: OWNER_UID,
      username: "dyfoot_owner",
      displayName: "DYFOOT Owner",
      photoUrl: "",
      bio: "Official",
      role: "OWNER",
      vipTier: "NONE",
      vipExpiresAt: 0,
      suspended: false,
      searchable: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  const verifiedOwnerDb = ownerDb(true);
  await assertSucceeds(
    verifiedOwnerDb.collection("users").doc(OWNER_UID).set({
      userId: OWNER_UID,
      username: "dyfoot_owner",
      displayName: "DYFOOT Owner",
      photoUrl: "",
      bio: "Official Owner",
      role: "OWNER",
      vipTier: "NONE",
      vipExpiresAt: 0,
      suspended: false,
      searchable: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Spoofed owner cannot create tips
  await assertFails(
    spoofDb.collection("tips").doc("tip_spoof").set({
      authorId: OWNER_UID,
      matchName: "Arsenal vs Chelsea",
      homeTeam: "Arsenal",
      awayTeam: "Chelsea",
      competition: "Premier League",
      kickoffLabel: "Today 20:00",
      tier: "FREE",
      market: "1X2",
      selection: "Arsenal Win",
      odds: "1.85",
      riskLevel: "LOW",
      status: "PENDING",
      published: true,
      featured: true,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Ordinary user registration, role escalation prevention, and PII isolation", async () => {
  const freeDb = userDb(FREE_UID);
  const created = pastDate();

  // Cannot self-register as OWNER or ADMIN
  await assertFails(
    freeDb.collection("users").doc(FREE_UID).set({
      userId: FREE_UID,
      username: "hacker",
      displayName: "Hacker",
      role: "ADMIN",
      vipTier: "NONE",
      vipExpiresAt: 0,
      suspended: false,
      searchable: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid FREE_MEMBER registration succeeds
  await assertSucceeds(
    freeDb.collection("users").doc(FREE_UID).set({
      userId: FREE_UID,
      username: "fan_tz",
      displayName: "TZ Fan",
      photoUrl: "",
      bio: "Simba SC fan",
      role: "FREE_MEMBER",
      vipTier: "NONE",
      vipExpiresAt: 0,
      suspended: false,
      searchable: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cannot escalate own role or vipTier on update
  await assertFails(
    freeDb.collection("users").doc(FREE_UID).update({
      role: "OWNER",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    freeDb.collection("users").doc(FREE_UID).update({
      vipTier: "ANNUAL",
      updatedAt: pastDate(1000),
    })
  );

  // Valid profile edit succeeds
  await assertSucceeds(
    freeDb.collection("users").doc(FREE_UID).update({
      bio: "Yanga & Arsenal supporter",
      updatedAt: pastDate(1000),
    })
  );

  // Private PII doc isolated from other users
  await assertSucceeds(
    freeDb.collection("users").doc(FREE_UID).collection("private").doc("info").set({
      userId: FREE_UID,
      email: "free@gmail.com",
      phone: "0712000111",
      notifyTips: true,
      notifyNews: true,
      notifyCommunity: true,
      notifyPayments: true,
      updatedAt: pastDate(1000),
    })
  );

  const otherDb = userDb(OTHER_UID);
  await assertFails(otherDb.collection("users").doc(FREE_UID).collection("private").doc("info").get());
});

test("VIP tip secrets strictly protected from Free and Expired users, allowed for Active VIP and Owner", async () => {
  const adminDb = ownerDb(true);
  const created = pastDate(10000);

  // Owner publishes VIP tip metadata + VIP secret
  await assertSucceeds(
    adminDb.collection("tips").doc("vip_tip_1").set({
      authorId: OWNER_UID,
      matchName: "Simba SC vs Young Africans",
      homeTeam: "Simba SC",
      awayTeam: "Young Africans",
      competition: "NBC Premier League",
      kickoffLabel: "Sat 16:00 EAT",
      tier: "VIP",
      market: "Both Teams To Score",
      selection: "",
      odds: "2.10",
      bookmakerName: "SportPesa",
      bookmakerCode: "",
      predictedScore: "",
      analysis: "VIP analysis locked for members.",
      riskLevel: "MEDIUM",
      status: "PENDING",
      published: true,
      featured: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    adminDb.collection("vip_tips_secrets").doc("vip_tip_1").set({
      tipId: "vip_tip_1",
      selection: "BTTS - Yes",
      bookmakerCode: "SP-99281",
      predictedScore: "2-1",
      analysis: "Both teams scored in 4 of last 5 Dar derbies.",
      updatedAt: created,
    })
  );

  const freeDb = userDb(FREE_UID);
  // Free user can view public teaser in /tips
  await assertSucceeds(freeDb.collection("tips").where("published", "==", true).get());
  // Free user CANNOT read VIP secret
  await assertFails(freeDb.collection("vip_tips_secrets").doc("vip_tip_1").get());
  // Free user CANNOT create or edit tips
  await assertFails(
    freeDb.collection("tips").doc("fake_tip").set({
      authorId: FREE_UID,
      matchName: "Fake vs Fake",
      homeTeam: "Fake",
      awayTeam: "Fake",
      competition: "Test",
      kickoffLabel: "Now",
      tier: "FREE",
      market: "1X2",
      odds: "1.50",
      riskLevel: "LOW",
      status: "PENDING",
      published: true,
      featured: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Expired VIP membership denied
  await assertSucceeds(
    adminDb.collection("memberships").doc(VIP_UID).set({
      userId: VIP_UID,
      planId: "monthly",
      planName: "VIP Monthly",
      status: "ACTIVE",
      expiresAt: pastDate(1000),
      updatedAt: created,
    })
  );
  const vipDb = userDb(VIP_UID);
  await assertFails(vipDb.collection("vip_tips_secrets").doc("vip_tip_1").get());

  // Active VIP membership allowed
  await assertSucceeds(
    adminDb.collection("memberships").doc(VIP_UID).set({
      userId: VIP_UID,
      planId: "monthly",
      planName: "VIP Monthly",
      status: "ACTIVE",
      expiresAt: futureDate(86400_000),
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(vipDb.collection("vip_tips_secrets").doc("vip_tip_1").get());
});

test("Mobile money payment security: users cannot self-approve or forge merchant number", async () => {
  const freeDb = userDb(FREE_UID);
  const created = pastDate(5000);

  // Reject self-approved payment
  await assertFails(
    freeDb.collection("payments").doc("pay_cheat").set({
      userId: FREE_UID,
      planId: "monthly",
      planName: "VIP Monthly",
      amountTzs: 25000,
      currency: "TZS",
      provider: "M-PESA",
      payerPhone: "0755123456",
      merchantNumber: "0706499813",
      transactionRef: "MPESA12345",
      status: "VERIFIED_PAID",
      verifiedBy: FREE_UID,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject wrong merchant number
  await assertFails(
    freeDb.collection("payments").doc("pay_wrong_num").set({
      userId: FREE_UID,
      planId: "monthly",
      planName: "VIP Monthly",
      amountTzs: 25000,
      currency: "TZS",
      provider: "M-PESA",
      payerPhone: "0755123456",
      merchantNumber: "0700000000",
      transactionRef: "MPESA12345",
      status: "PENDING_VERIFICATION",
      verifiedBy: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid pending submission succeeds
  await assertSucceeds(
    freeDb.collection("payments").doc("pay_valid_1").set({
      userId: FREE_UID,
      userDisplayName: "TZ Fan",
      planId: "monthly",
      planName: "VIP Monthly",
      amountTzs: 25000,
      currency: "TZS",
      provider: "M-PESA",
      payerPhone: "0755123456",
      merchantNumber: "0706499813",
      transactionRef: "MPESA998877",
      status: "PENDING_VERIFICATION",
      verifiedBy: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // User cannot mark own payment as paid
  await assertFails(
    freeDb.collection("payments").doc("pay_valid_1").update({
      status: "VERIFIED_PAID",
      verifiedBy: FREE_UID,
      updatedAt: pastDate(1000),
    })
  );

  // Owner can verify payment
  const adminDb = ownerDb(true);
  await assertSucceeds(
    adminDb.collection("payments").doc("pay_valid_1").update({
      status: "VERIFIED_PAID",
      verifiedBy: OWNER_UID,
      providerRef: "GW-CONFIRMED-01",
      updatedAt: pastDate(1000),
    })
  );
});

test("Community private group & DM isolation", async () => {
  const vipDb = userDb(VIP_UID);
  const freeDb = userDb(FREE_UID);
  const otherDb = userDb(OTHER_UID);
  const created = pastDate(5000);

  await assertSucceeds(
    vipDb.collection("groups").doc("dm_vip_free").set({
      title: "Direct Chat",
      description: "Private 1-to-1",
      directMessage: true,
      publicRoom: false,
      createdBy: VIP_UID,
      memberUids: [VIP_UID, FREE_UID],
      lastMessage: "Hey!",
      lastSenderName: "VIP",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    vipDb.collection("groups").doc("dm_vip_free").collection("messages").doc("msg_1").set({
      senderId: VIP_UID,
      senderName: "VIP User",
      text: "Check tonight's Champions League lineup.",
      publicRoom: false,
      memberUids: [VIP_UID, FREE_UID],
      createdAt: pastDate(2000),
    })
  );

  // Member can list/read
  await assertSucceeds(
    freeDb.collection("groups").doc("dm_vip_free").collection("messages").where("memberUids", "array-contains", FREE_UID).get()
  );

  // Non-member CANNOT read or write private DM
  await assertFails(otherDb.collection("groups").doc("dm_vip_free").get());
  await assertFails(
    otherDb.collection("groups").doc("dm_vip_free").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    otherDb.collection("groups").doc("dm_vip_free").collection("messages").doc("msg_intrude").set({
      senderId: OTHER_UID,
      senderName: "Intruder",
      text: "Spam",
      publicRoom: false,
      memberUids: [VIP_UID, FREE_UID],
      createdAt: pastDate(1000),
    })
  );
});

test("App settings 4-tier TSH pricing and ROCK DAILY package payment flow", async () => {
  const adminDb = ownerDb(true);
  const freeDb = userDb(FREE_UID);
  const created = pastDate(3000);

  await assertSucceeds(
    adminDb.collection("app_settings").doc("platform").set({
      vipDailyTzs: 1000,
      vipWeeklyTzs: 5000,
      vipMonthlyTzs: 15000,
      vipQuarterlyTzs: 40000,
      vipAnnualTzs: 80000,
      merchantNumber: "0706499813",
      merchantName: "DYFOOT Tanzania",
      categories: ["PREMIER_LEAGUE", "NBC_PREMIER_LEAGUE"],
      liveUpdateBanner: "Live DataSportsAPI & Live Football API active.",
      liveSyncIntervalSec: 30,
      configVersion: 2,
      updatedAt: created,
    })
  );

  // Invalid liveSyncIntervalSec (< 15s) rejected even for owner
  await assertFails(
    adminDb.collection("app_settings").doc("platform").update({
      liveSyncIntervalSec: 5,
      updatedAt: pastDate(1000),
    })
  );

  // Ordinary user cannot modify platform VIP pricing
  await assertFails(
    freeDb.collection("app_settings").doc("platform").update({
      vipDailyTzs: 1,
      updatedAt: pastDate(1000),
    })
  );

  // Free user can submit ROCK PACKAGE (1,000 TSH) payment
  await assertSucceeds(
    freeDb.collection("payments").doc("pay_rock_daily").set({
      userId: FREE_UID,
      userDisplayName: "TZ Fan",
      planId: "DAILY",
      planName: "ROCK PACKAGE",
      amountTzs: 1000,
      currency: "TZS",
      provider: "M-PESA",
      payerPhone: "0755123456",
      merchantNumber: "0706499813",
      transactionRef: "ROCK1000TX",
      status: "PENDING_VERIFICATION",
      verifiedBy: "",
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Community 24h matchday statuses enforce author ownership and signed-in visibility", async () => {
  const freeDb = userDb(FREE_UID);
  const otherDb = userDb(OTHER_UID);
  const created = pastDate(2000);
  const expiresAt = Date.now() + 86_400_000;

  // Reject spoofing another user's authorId
  await assertFails(
    otherDb.collection("statuses").doc("st_spoof").set({
      authorId: FREE_UID,
      authorName: "Spoofer",
      text: "Fake post",
      themeColor: "LIME",
      expiresAtEpochMs: expiresAt,
      createdAt: created,
    })
  );

  // Author posts valid status
  await assertSucceeds(
    freeDb.collection("statuses").doc("st_1").set({
      authorId: FREE_UID,
      authorName: "TZ Fan",
      authorPhotoUrl: "",
      text: "Simba SC matchday vibes!",
      themeColor: "LIME",
      expiresAtEpochMs: expiresAt,
      createdAt: created,
    })
  );

  // Other signed-in user can read, cannot delete
  await assertSucceeds(otherDb.collection("statuses").doc("st_1").get());
  await assertFails(otherDb.collection("statuses").doc("st_1").delete());

  // Author can delete own status
  await assertSucceeds(freeDb.collection("statuses").doc("st_1").delete());
});

test("FCM device tokens and favorite match subscriptions enforce strict owner isolation", async () => {
  const freeDb = userDb(FREE_UID);
  const otherDb = userDb(OTHER_UID);
  const now = pastDate(1000);

  // User can register own FCM token and favorite match IDs
  await assertSucceeds(
    freeDb.collection("users").doc(FREE_UID).collection("fcm_tokens").doc("android_primary").set({
      userId: FREE_UID,
      token: "fcm_token_sample_1234567890",
      favoriteMatchIds: ["espn_PL_704123", "espn_CL_901222"],
      favoriteTopics: ["dyfoot_match_espn_PL_704123", "dyfoot_match_espn_CL_901222"],
      platform: "ANDROID",
      language: "ENGLISH",
      updatedAt: now,
    })
  );

  // Other user cannot read or overwrite another user's FCM token doc
  await assertFails(
    otherDb.collection("users").doc(FREE_UID).collection("fcm_tokens").doc("android_primary").get()
  );
  await assertFails(
    otherDb.collection("users").doc(FREE_UID).collection("fcm_tokens").doc("android_primary").set({
      userId: FREE_UID,
      token: "spoof_token_123456789",
      favoriteMatchIds: ["espn_PL_704123"],
      favoriteTopics: ["dyfoot_match_espn_PL_704123"],
      platform: "ANDROID",
      language: "ENGLISH",
      updatedAt: now,
    })
  );

  // Invalid short token is rejected
  await assertFails(
    freeDb.collection("users").doc(FREE_UID).collection("fcm_tokens").doc("bad").set({
      userId: FREE_UID,
      token: "short",
      favoriteMatchIds: [],
      favoriteTopics: [],
      platform: "ANDROID",
      language: "ENGLISH",
      updatedAt: now,
    })
  );
});

test("Admin-only bilingual local football news enforces admin writes, draft privacy, and zero external links", async () => {
  const adminDb = ownerDb(true);
  const freeDb = userDb(FREE_UID);
  const created = pastDate(4000);

  // Non-admin user CANNOT create local football news
  await assertFails(
    freeDb.collection("news").doc("news_free_spoof").set({
      authorId: FREE_UID,
      title: "Fake Local News",
      summary: "Fake summary",
      content: "Fake article content",
      titleSw: "Habari ya Uongo",
      summarySw: "Muhtasari wa uongo",
      contentSw: "Maudhui ya uongo",
      language: "BILINGUAL",
      leagueTag: "NBC Premier League",
      localFootball: true,
      category: "TANZANIA_LIGI",
      sourceName: "DYFOOT Tanzania Desk",
      sourceUrl: "",
      imageUrl: "",
      published: true,
      likesCount: 0,
      commentsCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Admin CANNOT store localFootball news with an external sourceUrl
  await assertFails(
    adminDb.collection("news").doc("news_external_blocked").set({
      authorId: OWNER_UID,
      title: "Simba Derby Preview",
      summary: "External link attempt",
      content: "Full preview inside app.",
      titleSw: "Dabi ya Kariakoo",
      summarySw: "Jaribio la kiungo cha nje",
      contentSw: "Uchambuzi kamili ndani ya app.",
      language: "BILINGUAL",
      leagueTag: "NBC Premier League",
      localFootball: true,
      category: "TANZANIA_LIGI",
      sourceName: "DYFOOT Tanzania Desk",
      sourceUrl: "https://external-example.com/article",
      imageUrl: "",
      published: true,
      likesCount: 0,
      commentsCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Admin creates bilingual local football draft (unpublished) -> hidden from ordinary users
  await assertSucceeds(
    adminDb.collection("news").doc("news_tz_draft").set({
      authorId: OWNER_UID,
      title: "Azam FC Tactical Breakdown",
      summary: "Chamazi side prepares high-press setup.",
      content: "Full English tactical breakdown stored inside DYFOOT.",
      titleSw: "Uchambuzi wa Mbinu wa Azam FC",
      summarySw: "Kikosi cha Chamazi chajiandaa na shinikizo la juu.",
      contentSw: "Uchambuzi kamili wa Kiswahili uliohifadhiwa ndani ya DYFOOT.",
      language: "BILINGUAL",
      leagueTag: "NBC Premier League",
      localFootball: true,
      category: "TANZANIA_LIGI",
      sourceName: "DYFOOT Tanzania Desk",
      sourceUrl: "",
      imageUrl: "",
      published: false,
      likesCount: 0,
      commentsCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(freeDb.collection("news").doc("news_tz_draft").get());

  // Admin publishes bilingual local football article
  await assertSucceeds(
    adminDb.collection("news").doc("news_tz_draft").update({
      published: true,
      titleSw: "Uchambuzi Mpya wa Mbinu wa Azam FC",
      updatedAt: pastDate(1000),
    })
  );

  // Ordinary signed-in user can read published bilingual article & increment likesCount, but cannot edit content
  await assertSucceeds(freeDb.collection("news").where("published", "==", true).get());
  await assertSucceeds(
    freeDb.collection("news").doc("news_tz_draft").update({
      likesCount: 1,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    freeDb.collection("news").doc("news_tz_draft").update({
      contentSw: "Tampered body",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(freeDb.collection("news").doc("news_tz_draft").delete());

  // Admin can delete
  await assertSucceeds(adminDb.collection("news").doc("news_tz_draft").delete());
});

test("Advertisements enforce owner/admin publishing, valid placements/URLs, active visibility, and full management", async () => {
  const adminDb = ownerDb(true);
  const freeDb = userDb(FREE_UID);
  const created = pastDate(3000);

  // Non-admin user cannot publish an advertisement
  await assertFails(
    freeDb.collection("advertisements").doc("ad_spoof").set({
      authorId: FREE_UID,
      title: "Unauthorized Promo",
      promoText: "Should be denied",
      imageUrl: "https://firebasestorage.googleapis.com/v0/b/demo/o/banner.webp?alt=media",
      storagePath: "advertisements/ad_spoof/banner.webp",
      destinationUrl: "dyfoot://vip",
      placement: "HOME_TOP",
      active: true,
      startEpochMs: 0,
      endEpochMs: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Invalid placement or unsafe javascript: destinationUrl is rejected even for owner
  await assertFails(
    adminDb.collection("advertisements").doc("ad_bad_dest").set({
      authorId: OWNER_UID,
      title: "Bad Link Ad",
      promoText: "Testing URL validation",
      imageUrl: "",
      storagePath: "",
      destinationUrl: "javascript:alert(1)",
      placement: "HOME_TOP",
      active: true,
      startEpochMs: 0,
      endEpochMs: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Owner publishes banner with Firebase Cloud Storage imageUrl & TIPS_BANNER placement
  await assertSucceeds(
    adminDb.collection("advertisements").doc("ad_banner_1").set({
      authorId: OWNER_UID,
      title: "Weekend VIP Jackpot",
      promoText: "Get 4-tier VIP access starting at 1,000 TSH.",
      imageUrl: "https://firebasestorage.googleapis.com/v0/b/gen-lang-client-0684472529.firebasestorage.app/o/advertisements%2Fad_banner_1%2Fbanner.webp?alt=media",
      storagePath: "advertisements/ad_banner_1/banner.webp",
      destinationUrl: "dyfoot://vip",
      placement: "TIPS_BANNER",
      active: true,
      startEpochMs: 0,
      endEpochMs: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Signed-in user can read active ads via the exact client query filter
  await assertSucceeds(freeDb.collection("advertisements").where("active", "==", true).get());
  await assertSucceeds(freeDb.collection("advertisements").doc("ad_banner_1").get());

  // Non-admin user cannot deactivate or delete the banner
  await assertFails(
    freeDb.collection("advertisements").doc("ad_banner_1").update({
      active: false,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(freeDb.collection("advertisements").doc("ad_banner_1").delete());

  // Owner edits placement to SPORT_HUB and deactivates banner -> hidden from non-admins
  await assertSucceeds(
    adminDb.collection("advertisements").doc("ad_banner_1").update({
      title: "Updated Sport Hub Promo",
      placement: "SPORT_HUB",
      active: false,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(freeDb.collection("advertisements").doc("ad_banner_1").get());

  // Owner deletes banner
  await assertSucceeds(adminDb.collection("advertisements").doc("ad_banner_1").delete());
});




