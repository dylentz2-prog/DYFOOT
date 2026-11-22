package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.unwrapFirestoreMessage
import com.example.data.local.UserPreferencesDataStore
import com.example.data.model.AdminAuditLog
import com.example.data.model.Advertisement
import com.example.data.model.Announcement
import com.example.data.model.AppSettings
import com.example.data.model.BlockedUser
import com.example.data.model.OFFICIAL_MERCHANT_NUMBER
import com.example.data.notifications.DyfootFcmManager
import com.example.data.notifications.DyfootNotificationHelper
import com.example.data.notifications.LiveMatchAlertEngine
import com.example.data.notifications.MatchReminderScheduler
import com.example.data.model.CommunityGroup
import com.example.data.model.CommunityStatus
import com.example.data.model.ContentComment
import com.example.data.model.ContentReport
import com.example.data.model.FixtureItem
import com.example.data.model.MatchDetailBundle
import com.example.data.model.GroupMessage
import com.example.data.model.Membership
import com.example.data.model.MobileMoneyProvider
import com.example.data.model.NewsArticle
import com.example.data.model.NotificationItem
import com.example.data.model.OFFICIAL_OWNER_EMAIL
import com.example.data.model.PaymentRecord
import com.example.data.model.PodcastEpisode
import com.example.data.model.StandingRow
import com.example.data.model.TipItem
import com.example.data.model.TipTier
import com.example.data.model.UiState
import com.example.data.model.UserBookmark
import com.example.data.model.UserPrivateInfo
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VideoItem
import com.example.data.model.VipTier
import com.example.data.model.VipTipSecret
import com.example.data.remote.ExternalSportsService
import com.example.data.remote.LiveApiResult
import com.example.data.repository.DyfootRepository
import com.example.ui.i18n.AppLanguage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class GlobalSearchResults(
    val teamsAndCompetitions: List<String> = emptyList(),
    val matches: List<FixtureItem> = emptyList(),
    val news: List<NewsArticle> = emptyList(),
    val tips: List<TipItem> = emptyList(),
    val videos: List<VideoItem> = emptyList(),
    val podcasts: List<PodcastEpisode> = emptyList(),
    val users: List<UserProfile> = emptyList(),
    val groups: List<CommunityGroup> = emptyList()
)

data class PendingVipPushState(
    val tier: VipTier,
    val amountTzs: Long,
    val provider: MobileMoneyProvider,
    val payerPhone: String,
    val requestRef: String,
    val ussdCode: String
)

class DyfootViewModel(
    private val repository: DyfootRepository,
    private val sportsService: ExternalSportsService,
    private val prefs: UserPreferencesDataStore,
    val currentUserId: String,
    appContext: Context? = null
) : ViewModel() {

    private val applicationContext: Context? = appContext?.applicationContext
    private val notificationHelper: DyfootNotificationHelper? = applicationContext?.let {
        DyfootNotificationHelper(it)
    }

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents = _toastEvents.asSharedFlow()

    private val _pendingVipPush = MutableStateFlow<PendingVipPushState?>(null)
    val pendingVipPush: StateFlow<PendingVipPushState?> = _pendingVipPush.asStateFlow()

    private var previousVipActive: Boolean? = null
    private var previousFixtureSnapshots: Map<String, FixtureItem> = emptyMap()

    private fun <T> Flow<T>.toUiStateFlow(fallbackError: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                emit(UiState.Error(error.unwrapFirestoreMessage(fallbackError)))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val userProfile: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .toUiStateFlow("Failed to load profile")

    val privateInfo: StateFlow<UserPrivateInfo?> =
        repository.observePrivateInfo(currentUserId)
            .catch { if (it is CancellationException && it.cause == null) throw it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val membership: StateFlow<Membership?> =
        repository.observeMembership(currentUserId)
            .catch { if (it is CancellationException && it.cause == null) throw it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val appSettings: StateFlow<AppSettings> =
        repository.observeAppSettings(currentUserId)
            .catch { if (it is CancellationException && it.cause == null) throw it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppSettings())

    val publishedTips: StateFlow<UiState<List<TipItem>>> =
        repository.observePublishedTips(currentUserId)
            .toUiStateFlow("Failed to load tips")

    private val _vipSecrets = MutableStateFlow<Map<String, VipTipSecret>>(emptyMap())
    val vipSecrets: StateFlow<Map<String, VipTipSecret>> = _vipSecrets.asStateFlow()

    val publishedNews: StateFlow<UiState<List<NewsArticle>>> =
        repository.observePublishedNews(currentUserId)
            .toUiStateFlow("Failed to load news")

    private val _rssNews = MutableStateFlow<List<NewsArticle>>(sportsService.getInitialInAppNews())
    val rssNews: StateFlow<List<NewsArticle>> = _rssNews.asStateFlow()

    private val _isRefreshingNews = MutableStateFlow(false)
    val isRefreshingNews: StateFlow<Boolean> = _isRefreshingNews.asStateFlow()

    val combinedNews: StateFlow<List<NewsArticle>> = combine(publishedNews, _rssNews) { fsState, rss ->
        val firestoreArticles = (fsState as? UiState.Success)?.data.orEmpty()
        (firestoreArticles + rss).distinctBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), sportsService.getInitialInAppNews())

    val publishedVideos: StateFlow<UiState<List<VideoItem>>> =
        repository.observePublishedVideos(currentUserId)
            .toUiStateFlow("Failed to load videos")

    val publishedPodcasts: StateFlow<UiState<List<PodcastEpisode>>> =
        repository.observePublishedPodcasts(currentUserId)
            .toUiStateFlow("Failed to load podcasts")

    val recentlyPlayedPodcastIds: StateFlow<List<String>> =
        prefs.recentlyPlayedPodcastIds
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeAds: StateFlow<List<Advertisement>> =
        repository.observeActiveAds(currentUserId)
            .catch { if (it is CancellationException && it.cause == null) throw it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val announcements: StateFlow<List<Announcement>> =
        repository.observeAnnouncements(currentUserId)
            .catch { if (it is CancellationException && it.cause == null) throw it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val publishedFixtures: StateFlow<UiState<List<FixtureItem>>> =
        repository.observePublishedFixtures(currentUserId)
            .toUiStateFlow("Failed to load fixtures")

    private val _liveApiFixtures = MutableStateFlow<List<FixtureItem>>(emptyList())
    private val _liveFeedStatus = MutableStateFlow<String?>(null)
    val liveFeedStatus: StateFlow<String?> = _liveFeedStatus.asStateFlow()
    private val _liveFeedUpdatedAt = MutableStateFlow<String?>(null)
    val liveFeedUpdatedAt: StateFlow<String?> = _liveFeedUpdatedAt.asStateFlow()
    private val _isSyncingLiveScores = MutableStateFlow(false)
    val isSyncingLiveScores: StateFlow<Boolean> = _isSyncingLiveScores.asStateFlow()
    private val _liveLeagueFilter = MutableStateFlow("ALL")
    val liveLeagueFilter: StateFlow<String> = _liveLeagueFilter.asStateFlow()
    private val _liveDateOffset = MutableStateFlow(0)
    val liveDateOffset: StateFlow<Int> = _liveDateOffset.asStateFlow()

    val combinedFixtures: StateFlow<List<FixtureItem>> =
        combine(publishedFixtures, _liveApiFixtures) { fsState, apiList ->
            val firestoreFixtures = (fsState as? UiState.Success)?.data.orEmpty()
            (firestoreFixtures + apiList).sortedBy { it.kickoffEpochMs }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _standings = MutableStateFlow<List<StandingRow>>(emptyList())
    val standings: StateFlow<List<StandingRow>> = _standings.asStateFlow()
    private val _standingsStatus = MutableStateFlow<String?>(null)
    val standingsStatus: StateFlow<String?> = _standingsStatus.asStateFlow()

    val appLanguage: StateFlow<AppLanguage> =
        prefs.appLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppLanguage.ENGLISH)

    val matchReminders: StateFlow<Set<String>> =
        prefs.matchReminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val favoriteFixtures: StateFlow<Set<String>> = matchReminders

    val fcmToken: StateFlow<String> =
        prefs.fcmToken.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    val kickoffAlarmIds: StateFlow<Set<String>> =
        prefs.kickoffAlarmIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val matchPredictions: StateFlow<Map<String, String>> =
        prefs.matchPredictions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    private val _inspectedFixtureDetail = MutableStateFlow<FixtureItem?>(null)
    val inspectedFixtureDetail: StateFlow<FixtureItem?> = _inspectedFixtureDetail.asStateFlow()

    private val _inspectedMatchBundle = MutableStateFlow<MatchDetailBundle?>(null)
    val inspectedMatchBundle: StateFlow<MatchDetailBundle?> = _inspectedMatchBundle.asStateFlow()

    private val _isLoadingMatchDetail = MutableStateFlow(false)
    val isLoadingMatchDetail: StateFlow<Boolean> = _isLoadingMatchDetail.asStateFlow()

    val myGroups: StateFlow<List<CommunityGroup>> =
        repository.observeMyGroups(currentUserId)
            .catch { if (it is CancellationException && it.cause == null) throw it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val publicGroups: StateFlow<List<CommunityGroup>> =
        repository.observePublicGroups(currentUserId)
            .catch { if (it is CancellationException && it.cause == null) throw it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeStatuses: StateFlow<List<CommunityStatus>> =
        repository.observeActiveStatuses(currentUserId)
            .catch { if (it is CancellationException && it.cause == null) throw it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<NotificationItem>> =
        repository.observeNotifications(currentUserId)
            .catch { if (it is CancellationException && it.cause == null) throw it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val unreadNotificationsCount: StateFlow<Int> =
        notifications.map { list -> list.count { !it.read } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val bookmarks: StateFlow<List<UserBookmark>> =
        repository.observeBookmarks(currentUserId)
            .catch { if (it is CancellationException && it.cause == null) throw it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val blockedUsers: StateFlow<List<BlockedUser>> =
        repository.observeBlockedUsers(currentUserId)
            .catch { if (it is CancellationException && it.cause == null) throw it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val myPayments: StateFlow<List<PaymentRecord>> =
        repository.observeMyPayments(currentUserId)
            .catch { if (it is CancellationException && it.cause == null) throw it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Global Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _searchedUsers = MutableStateFlow<List<UserProfile>>(emptyList())

    val searchResults: StateFlow<GlobalSearchResults> = combine(
        _searchQuery,
        combinedFixtures,
        combinedNews,
        publishedTips,
        publishedVideos,
        publishedPodcasts,
        publicGroups,
        _searchedUsers
    ) { arr ->
        val q = (arr[0] as String).trim().lowercase()
        @Suppress("UNCHECKED_CAST")
        val fixtures = arr[1] as List<FixtureItem>
        @Suppress("UNCHECKED_CAST")
        val news = arr[2] as List<NewsArticle>
        @Suppress("UNCHECKED_CAST")
        val tipsState = arr[3] as UiState<List<TipItem>>
        @Suppress("UNCHECKED_CAST")
        val videosState = arr[4] as UiState<List<VideoItem>>
        @Suppress("UNCHECKED_CAST")
        val podcastsState = arr[5] as UiState<List<PodcastEpisode>>
        @Suppress("UNCHECKED_CAST")
        val groups = arr[6] as List<CommunityGroup>
        @Suppress("UNCHECKED_CAST")
        val users = arr[7] as List<UserProfile>

        val tips = (tipsState as? UiState.Success)?.data.orEmpty()
        val videos = (videosState as? UiState.Success)?.data.orEmpty()
        val podcasts = (podcastsState as? UiState.Success)?.data.orEmpty()

        if (q.isEmpty()) {
            GlobalSearchResults()
        } else {
            val teamSet = linkedSetOf<String>()
            fixtures.forEach {
                if (it.homeTeam.lowercase().contains(q)) teamSet.add(it.homeTeam)
                if (it.awayTeam.lowercase().contains(q)) teamSet.add(it.awayTeam)
                if (it.competition.lowercase().contains(q)) teamSet.add(it.competition)
            }
            tips.forEach {
                if (it.homeTeam.lowercase().contains(q)) teamSet.add(it.homeTeam)
                if (it.awayTeam.lowercase().contains(q)) teamSet.add(it.awayTeam)
                if (it.competition.lowercase().contains(q)) teamSet.add(it.competition)
            }
            GlobalSearchResults(
                teamsAndCompetitions = teamSet.toList(),
                matches = fixtures.filter {
                    it.homeTeam.lowercase().contains(q) ||
                        it.awayTeam.lowercase().contains(q) ||
                        it.competition.lowercase().contains(q)
                },
                news = news.filter {
                    it.title.lowercase().contains(q) ||
                        it.summary.lowercase().contains(q) ||
                        it.titleSw.lowercase().contains(q) ||
                        it.summarySw.lowercase().contains(q) ||
                        it.leagueTag.lowercase().contains(q) ||
                        it.category.lowercase().contains(q)
                },
                tips = tips.filter {
                    it.matchName.lowercase().contains(q) ||
                        it.competition.lowercase().contains(q) ||
                        it.market.lowercase().contains(q)
                },
                videos = videos.filter {
                    it.title.lowercase().contains(q) ||
                        it.caption.lowercase().contains(q) ||
                        it.creatorName.lowercase().contains(q)
                },
                podcasts = podcasts.filter {
                    it.title.lowercase().contains(q) ||
                        it.showName.lowercase().contains(q) ||
                        it.hostName.lowercase().contains(q)
                },
                users = users.filter {
                    it.username.lowercase().contains(q) || it.displayName.lowercase().contains(q)
                },
                groups = groups.filter {
                    it.title.lowercase().contains(q) || it.description.lowercase().contains(q)
                }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), GlobalSearchResults())

    // Active Podcast Player State
    private val _activePodcast = MutableStateFlow<PodcastEpisode?>(null)
    val activePodcast: StateFlow<PodcastEpisode?> = _activePodcast.asStateFlow()

    // Admin Studio States (only queried when authorized)
    private val _adminTips = MutableStateFlow<List<TipItem>>(emptyList())
    val adminTips: StateFlow<List<TipItem>> = _adminTips.asStateFlow()

    private val _adminNews = MutableStateFlow<List<NewsArticle>>(emptyList())
    val adminNews: StateFlow<List<NewsArticle>> = _adminNews.asStateFlow()

    private val _adminVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val adminVideos: StateFlow<List<VideoItem>> = _adminVideos.asStateFlow()

    private val _adminPodcasts = MutableStateFlow<List<PodcastEpisode>>(emptyList())
    val adminPodcasts: StateFlow<List<PodcastEpisode>> = _adminPodcasts.asStateFlow()

    private val _adminAds = MutableStateFlow<List<Advertisement>>(emptyList())
    val adminAds: StateFlow<List<Advertisement>> = _adminAds.asStateFlow()

    private val _adminAnnouncements = MutableStateFlow<List<Announcement>>(emptyList())
    val adminAnnouncements: StateFlow<List<Announcement>> = _adminAnnouncements.asStateFlow()

    private val _adminFixtures = MutableStateFlow<List<FixtureItem>>(emptyList())
    val adminFixtures: StateFlow<List<FixtureItem>> = _adminFixtures.asStateFlow()

    private val _adminUsers = MutableStateFlow<List<UserProfile>>(emptyList())
    val adminUsers: StateFlow<List<UserProfile>> = _adminUsers.asStateFlow()

    private val _adminPayments = MutableStateFlow<List<PaymentRecord>>(emptyList())
    val adminPayments: StateFlow<List<PaymentRecord>> = _adminPayments.asStateFlow()

    private val _adminReports = MutableStateFlow<List<ContentReport>>(emptyList())
    val adminReports: StateFlow<List<ContentReport>> = _adminReports.asStateFlow()

    private val _adminLogs = MutableStateFlow<List<AdminAuditLog>>(emptyList())
    val adminLogs: StateFlow<List<AdminAuditLog>> = _adminLogs.asStateFlow()

    private var adminObserversStarted = false

    init {
        viewModelScope.launch {
            repository.ensureUserProfile().onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Profile sync failed")
            }
            val initialFavs = prefs.matchReminders.first()
            val initialLang = prefs.appLanguage.first()
            DyfootFcmManager.syncAllFavoriteSubscriptions(
                prefs = prefs,
                repository = repository,
                favoriteMatchIds = initialFavs,
                language = initialLang
            )
        }
        refreshRssNews()
        syncLiveFootballScores()
        loadStandings("PL")
        viewModelScope.launch {
            while (isActive) {
                delay(45_000L)
                syncLiveFootballScores(_liveLeagueFilter.value, _liveDateOffset.value)
            }
        }

        // Automatically unlock VIP secrets ONLY when user is active VIP or Admin/Owner
        viewModelScope.launch {
            combine(userProfile, membership, publishedTips) { profState, mem, tipsState ->
                val prof = (profState as? UiState.Success)?.data
                val isAdminOrOwner = prof?.parsedRole() == UserRole.OWNER || prof?.parsedRole() == UserRole.ADMIN
                val isVip = mem?.isCurrentlyActive() == true
                val tips = (tipsState as? UiState.Success)?.data.orEmpty()
                Pair(Triple(isAdminOrOwner || isVip, isAdminOrOwner, tips), mem)
            }.collect { (triple, mem) ->
                val (canReadVip, isAdminOrOwner, tips) = triple
                val nowActive = mem?.isCurrentlyActive() == true
                if (previousVipActive == false && nowActive) {
                    val plan = mem?.planName ?: "VIP Package"
                    val isSw = appLanguage.value == AppLanguage.SWAHILI
                    _pendingVipPush.value = null
                    notificationHelper?.notifyVipActivated(plan, isSw)
                    _toastEvents.tryEmit(
                        if (isSw) "$plan imefunguliwa moja kwa moja!"
                        else "$plan activated automatically!"
                    )
                }
                previousVipActive = nowActive

                if (isAdminOrOwner && !adminObserversStarted) {
                    adminObserversStarted = true
                    startAdminObservers(profIsOwner = isCurrentFirebaseUserOwner())
                }
                if (canReadVip) {
                    val vipTipIds = tips.filter { it.tier == TipTier.VIP.name }.map { it.id }
                    val currentMap = _vipSecrets.value.toMutableMap()
                    var changed = false
                    for (tipId in vipTipIds) {
                        if (!currentMap.containsKey(tipId)) {
                            repository.getVipTipSecret(tipId).getOrNull()?.let { secret ->
                                currentMap[tipId] = secret
                                changed = true
                            }
                        }
                    }
                    if (changed) _vipSecrets.value = currentMap
                } else {
                    _vipSecrets.value = emptyMap()
                }
            }
        }
    }

    private fun isCurrentFirebaseUserOwner(): Boolean {
        val u = Firebase.auth.currentUser
        return u != null && u.isEmailVerified && u.email.equals(OFFICIAL_OWNER_EMAIL, ignoreCase = true)
    }

    private fun startAdminObservers(profIsOwner: Boolean) {
        viewModelScope.launch {
            repository.observeAdminTips(currentUserId, profIsOwner)
                .catch { /* logged in repository */ }
                .collect { _adminTips.value = it }
        }
        viewModelScope.launch {
            repository.observeAdminNews(currentUserId, profIsOwner)
                .catch { /* logged in repository */ }
                .collect { _adminNews.value = it }
        }
        viewModelScope.launch {
            repository.observeAdminVideos(currentUserId, profIsOwner)
                .catch { /* logged in repository */ }
                .collect { _adminVideos.value = it }
        }
        viewModelScope.launch {
            repository.observeAdminPodcasts(currentUserId, profIsOwner)
                .catch { /* logged in repository */ }
                .collect { _adminPodcasts.value = it }
        }
        viewModelScope.launch {
            repository.observeAdminAds(currentUserId, profIsOwner)
                .catch { /* logged in repository */ }
                .collect { _adminAds.value = it }
        }
        viewModelScope.launch {
            repository.observeAdminAnnouncements(currentUserId, profIsOwner)
                .catch { /* logged in repository */ }
                .collect { _adminAnnouncements.value = it }
        }
        viewModelScope.launch {
            repository.observeAdminFixtures(currentUserId, profIsOwner)
                .catch { /* logged in repository */ }
                .collect { _adminFixtures.value = it }
        }
        viewModelScope.launch {
            repository.observeAllUsersForAdmin(currentUserId, profIsOwner)
                .catch { /* logged in repository */ }
                .collect { _adminUsers.value = it }
        }
        viewModelScope.launch {
            repository.observeAdminLogs(currentUserId, profIsOwner)
                .catch { /* logged in repository */ }
                .collect { _adminLogs.value = it }
        }
        if (profIsOwner) {
            viewModelScope.launch {
                repository.observeAllPaymentsForOwner(currentUserId)
                    .catch { /* logged in repository */ }
                    .collect { _adminPayments.value = it }
            }
            viewModelScope.launch {
                repository.observeReportsForOwner(currentUserId)
                    .catch { /* logged in repository */ }
                    .collect { _adminReports.value = it }
            }
        }
    }

    fun hasActiveVipAccess(): Boolean {
        val prof = (userProfile.value as? UiState.Success)?.data
        if (prof?.parsedRole() == UserRole.OWNER || prof?.parsedRole() == UserRole.ADMIN) return true
        return membership.value?.isCurrentlyActive() == true
    }

    fun isAdminOrOwner(): Boolean {
        val prof = (userProfile.value as? UiState.Success)?.data ?: return false
        return prof.parsedRole() == UserRole.OWNER || prof.parsedRole() == UserRole.ADMIN
    }

    fun isVerifiedOwner(): Boolean {
        val prof = (userProfile.value as? UiState.Success)?.data ?: return false
        return prof.parsedRole() == UserRole.OWNER && isCurrentFirebaseUserOwner()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.trim().length >= 2) {
            viewModelScope.launch {
                repository.searchUsersByUsername(query).onSuccess {
                    _searchedUsers.value = it
                }
            }
        }
    }

    fun refreshRssNews() {
        viewModelScope.launch {
            _isRefreshingNews.value = true
            _rssNews.value = sportsService.fetchPermittedSportsRssNews()
            _isRefreshingNews.value = false
        }
    }

    fun syncLiveFootballScores(
        leagueCode: String = _liveLeagueFilter.value,
        dateOffsetDays: Int = _liveDateOffset.value
    ) {
        _liveLeagueFilter.value = leagueCode
        _liveDateOffset.value = dateOffsetDays
        viewModelScope.launch {
            _isSyncingLiveScores.value = true
            when (val res = sportsService.fetchLiveMatches(leagueCode, dateOffsetDays)) {
                is LiveApiResult.Success -> {
                    val favIds = favoriteFixtures.value
                    val isSw = appLanguage.value == AppLanguage.SWAHILI
                    if (previousFixtureSnapshots.isNotEmpty() && favIds.isNotEmpty()) {
                        res.data.filter { it.id in favIds }.forEach { updated ->
                            val prev = previousFixtureSnapshots[updated.id]
                            if (prev != null) {
                                LiveMatchAlertEngine.detectFavoriteMatchEvents(prev, updated, isSw)?.let { (title, body) ->
                                    notificationHelper?.notifyFavoriteMatchUpdate(updated.id, title, body)
                                    _toastEvents.tryEmit(title)
                                    viewModelScope.launch {
                                        repository.createUserNotification(
                                            targetUid = currentUserId,
                                            title = title,
                                            body = body,
                                            category = "LIVE_MATCH",
                                            deepLinkRoute = "live_scores"
                                        )
                                    }
                                }
                            }
                        }
                    }
                    previousFixtureSnapshots = previousFixtureSnapshots + res.data.associateBy { it.id }
                    _liveApiFixtures.value = res.data
                    _liveFeedStatus.value = "${res.sourceName} (${res.data.size} matches)"
                    _liveFeedUpdatedAt.value = res.updatedAtLabel
                }
                is LiveApiResult.MissingApiKey -> {
                    _liveFeedStatus.value = res.message
                }
                is LiveApiResult.Error -> {
                    _liveFeedStatus.value = res.message
                }
            }
            _isSyncingLiveScores.value = false
        }
    }

    fun loadStandings(competitionCode: String) {
        viewModelScope.launch {
            when (val res = sportsService.fetchStandings(competitionCode)) {
                is LiveApiResult.Success -> {
                    _standings.value = res.data
                    _standingsStatus.value = "${res.sourceName} • Updated ${res.updatedAtLabel}"
                }
                is LiveApiResult.MissingApiKey -> {
                    _standings.value = emptyList()
                    _standingsStatus.value = res.message
                }
                is LiveApiResult.Error -> {
                    _standingsStatus.value = res.message
                }
            }
        }
    }

    fun hasMobileMoneyGatewayKey(): Boolean = sportsService.hasMobileMoneyGatewayKey()
    fun hasFootballDataApiKey(): Boolean = sportsService.hasFootballDataApiKey()

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            prefs.setAppLanguage(language)
            val favs = prefs.matchReminders.first()
            DyfootFcmManager.syncAllFavoriteSubscriptions(
                prefs = prefs,
                repository = repository,
                favoriteMatchIds = favs,
                language = language
            )
            _toastEvents.tryEmit(
                if (language == AppLanguage.SWAHILI) "Lugha imebadilishwa kuwa Kiswahili"
                else "Language switched to English"
            )
        }
    }

    fun toggleMatchReminder(fixture: FixtureItem) {
        viewModelScope.launch {
            val enabled = prefs.toggleMatchReminder(fixture.id)
            val updatedFavs = prefs.matchReminders.first()
            val lang = appLanguage.value
            val isSw = lang == AppLanguage.SWAHILI
            val home = fixture.homeTeam.replace("[SAMPLE]", "").trim()
            val away = fixture.awayTeam.replace("[SAMPLE]", "").trim()

            // Sync Firebase Cloud Messaging per-match topic + Firestore device token
            DyfootFcmManager.updateMatchFavoriteSubscription(
                prefs = prefs,
                repository = repository,
                matchId = fixture.id,
                isFavorite = enabled,
                updatedFavorites = updatedFavs,
                language = lang
            )

            val msg = if (enabled) {
                if (isSw) "Umeongeza $home vs $away kwenye Mechi Pendwa! Utapata taarifa za magoli na matokeo kupitia FCM."
                else "Added $home vs $away to Favourites! Real-time FCM alerts active for goals & final score."
            } else {
                if (isSw) "Imeondolewa kwenye Mechi Pendwa" else "Removed from Favourites"
            }
            if (enabled && fixture.status == com.example.data.model.FixtureStatus.LIVE.name) {
                notificationHelper?.notifyFavoriteMatchUpdate(
                    matchId = fixture.id,
                    title = if (isSw) "MUBASHARA: $home ${fixture.homeScore}-${fixture.awayScore} $away"
                    else "LIVE FCM ALERTS ON: $home ${fixture.homeScore}-${fixture.awayScore} $away",
                    body = if (isSw) "Utapokea taarifa za magoli, kadi nyekundu na matokeo ya mwisho."
                    else "You will receive real-time FCM push alerts for goals, red cards, and final score."
                )
            }
            _toastEvents.tryEmit(msg)
        }
    }

    fun setMatchPrediction(fixtureId: String, pick: String) {
        viewModelScope.launch {
            prefs.setMatchPrediction(fixtureId, pick)
            val isSw = appLanguage.value == AppLanguage.SWAHILI
            _toastEvents.tryEmit(
                if (isSw) "Utabiri wako ($pick) umehifadhiwa!"
                else "Your match pick ($pick) saved!"
            )
        }
    }

    fun toggleKickoffAlarm(fixture: FixtureItem, leadMinutes: Int = 15) {
        viewModelScope.launch {
            val enabled = prefs.toggleKickoffAlarm(fixture.id)
            val isSw = appLanguage.value == AppLanguage.SWAHILI
            val home = fixture.homeTeam.replace("[SAMPLE]", "").trim()
            val away = fixture.awayTeam.replace("[SAMPLE]", "").trim()
            if (enabled) {
                applicationContext?.let {
                    MatchReminderScheduler.scheduleReminder(
                        context = it,
                        fixture = fixture,
                        leadMinutes = leadMinutes,
                        isSwahili = isSw
                    )
                }
                val msg = if (isSw) {
                    "⏰ Ukumbusho umewekwa ($leadMinutes dk kabla): $home vs $away"
                } else {
                    "⏰ Kickoff reminder set (${leadMinutes}m before): $home vs $away"
                }
                _toastEvents.tryEmit(msg)
            } else {
                applicationContext?.let {
                    MatchReminderScheduler.cancelReminder(it, fixture.id)
                }
                _toastEvents.tryEmit(if (isSw) "Ukumbusho umeondolewa" else "Match reminder cancelled")
            }
        }
    }

    fun inspectFixture(fixture: FixtureItem) {
        _inspectedFixtureDetail.value = fixture
        _inspectedMatchBundle.value = sportsService.buildAnalyticalMatchBundle(fixture, null)
        viewModelScope.launch {
            _isLoadingMatchDetail.value = true
            val bundle = sportsService.fetchMatchDetailBundle(fixture)
            if (_inspectedFixtureDetail.value?.id == fixture.id) {
                _inspectedFixtureDetail.value = bundle.fixture
                _inspectedMatchBundle.value = bundle
            }
            _isLoadingMatchDetail.value = false
        }
    }

    fun clearInspectedFixture() {
        _inspectedFixtureDetail.value = null
        _inspectedMatchBundle.value = null
        _isLoadingMatchDetail.value = false
    }

    fun selectPodcast(episode: PodcastEpisode) {
        _activePodcast.value = episode
        viewModelScope.launch {
            prefs.recordPodcastPlayed(episode.id)
        }
    }

    fun clearActivePodcast() {
        _activePodcast.value = null
    }

    fun observeNewsComments(newsId: String): Flow<List<ContentComment>> =
        repository.observeNewsComments(currentUserId, newsId)
            .catch { if (it is CancellationException && it.cause == null) throw it }

    fun observeVideoComments(videoId: String): Flow<List<ContentComment>> =
        repository.observeVideoComments(currentUserId, videoId)
            .catch { if (it is CancellationException && it.cause == null) throw it }

    fun observeGroupMessages(group: CommunityGroup): Flow<List<GroupMessage>> =
        repository.observeGroupMessages(currentUserId, group)
            .catch { if (it is CancellationException && it.cause == null) throw it }

    fun toggleBookmark(contentType: String, contentId: String, title: String, subtitle: String) {
        viewModelScope.launch {
            repository.toggleBookmark(contentType, contentId, title, subtitle)
                .onSuccess { saved ->
                    _toastEvents.tryEmit(if (saved) "Saved to Bookmarks" else "Removed from Bookmarks")
                }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Bookmark error") }
        }
    }

    fun likeNews(newsId: String) {
        viewModelScope.launch {
            repository.likeNews(newsId).onFailure {
                _toastEvents.tryEmit(it.localizedMessage ?: "Unable to like")
            }
        }
    }

    fun addNewsComment(newsId: String, text: String) {
        val name = (userProfile.value as? UiState.Success)?.data?.displayName ?: "Member"
        viewModelScope.launch {
            repository.addNewsComment(newsId, name, text)
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Comment failed") }
        }
    }

    fun likeVideo(videoId: String) {
        viewModelScope.launch {
            repository.likeVideo(videoId).onFailure {
                _toastEvents.tryEmit(it.localizedMessage ?: "Unable to like")
            }
        }
    }

    fun addVideoComment(videoId: String, text: String) {
        val name = (userProfile.value as? UiState.Success)?.data?.displayName ?: "Member"
        viewModelScope.launch {
            repository.addVideoComment(videoId, name, text)
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Comment failed") }
        }
    }

    fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        photoUrl: String,
        phone: String,
        searchable: Boolean,
        notifyTips: Boolean,
        notifyNews: Boolean,
        notifyCommunity: Boolean,
        notifyPayments: Boolean
    ) {
        viewModelScope.launch {
            repository.updateProfile(
                username, displayName, bio, photoUrl, phone, searchable,
                notifyTips, notifyNews, notifyCommunity, notifyPayments
            ).onSuccess {
                _toastEvents.tryEmit("Profile & preferences saved")
            }.onFailure {
                _toastEvents.tryEmit(it.localizedMessage ?: "Update failed")
            }
        }
    }

    fun submitMobileMoneyPayment(
        tier: VipTier,
        amountTzs: Long,
        provider: MobileMoneyProvider,
        payerPhone: String,
        transactionRef: String,
        onPushPromptReady: ((String) -> Unit)? = null
    ) {
        val name = (userProfile.value as? UiState.Success)?.data?.displayName ?: "Member"
        val isSw = appLanguage.value == AppLanguage.SWAHILI
        val merchantNum = appSettings.value.merchantNumber.ifBlank { OFFICIAL_MERCHANT_NUMBER }
        viewModelScope.launch {
            repository.submitMobileMoneyPayment(name, tier, amountTzs, provider, payerPhone, transactionRef)
                .onSuccess { generatedRef ->
                    val ussd = LiveMatchAlertEngine.ussdCodeFor(provider)
                    _pendingVipPush.value = PendingVipPushState(
                        tier = tier,
                        amountTzs = amountTzs,
                        provider = provider,
                        payerPhone = payerPhone.trim(),
                        requestRef = generatedRef,
                        ussdCode = ussd
                    )
                    notificationHelper?.notifyPaymentPinPrompt(
                        tierLabel = tier.label,
                        amountTzs = amountTzs,
                        provider = provider,
                        merchantNumber = merchantNum,
                        requestRef = generatedRef,
                        isSwahili = isSw
                    )
                    _toastEvents.tryEmit(
                        if (isSw) "Arifa ya malipo imetumwa! Ingiza PIN kwenye $ussd. Kifurushi kitawaka moja kwa moja."
                        else "Push notification sent! Approve PIN on $ussd. VIP loads automatically after confirmation."
                    )
                    onPushPromptReady?.invoke(ussd)
                }
                .onFailure {
                    _toastEvents.tryEmit(it.localizedMessage ?: "Submission failed")
                }
        }
    }

    fun dismissPendingVipPush() {
        _pendingVipPush.value = null
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationRead(id)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            notifications.value.filter { !it.read }.forEach {
                repository.markNotificationRead(it.id)
            }
        }
    }

    suspend fun searchUsers(query: String): List<UserProfile> =
        repository.searchUsersByUsername(query).getOrElse { emptyList() }

    fun openDirectChat(targetUser: UserProfile, onOpened: (CommunityGroup) -> Unit) {
        val me = (userProfile.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.createOrOpenDirectChat(me, targetUser)
                .onSuccess(onOpened)
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Could not open chat") }
        }
    }

    fun createGroup(title: String, description: String, isPublic: Boolean, onCreated: (CommunityGroup) -> Unit) {
        val me = (userProfile.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.createCommunityGroup(me, title, description, isPublic)
                .onSuccess(onCreated)
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Group creation failed") }
        }
    }

    fun joinPublicGroup(group: CommunityGroup, onJoined: () -> Unit) {
        viewModelScope.launch {
            repository.joinPublicGroup(group)
                .onSuccess { onJoined() }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Could not join group") }
        }
    }

    fun sendMessage(group: CommunityGroup, text: String, imageUrl: String = "") {
        val me = (userProfile.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.sendGroupMessage(group, me.displayName, text, imageUrl)
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Message failed") }
        }
    }

    fun blockUser(blockedUid: String, blockedName: String) {
        viewModelScope.launch {
            repository.blockUser(blockedUid, blockedName)
                .onSuccess { _toastEvents.tryEmit("Blocked $blockedName") }
        }
    }

    fun unblockUser(blockedUid: String) {
        viewModelScope.launch {
            repository.unblockUser(blockedUid)
                .onSuccess { _toastEvents.tryEmit("User unblocked") }
        }
    }

    fun submitReport(targetType: String, targetId: String, reason: String) {
        viewModelScope.launch {
            repository.submitReport(targetType, targetId, reason)
                .onSuccess { _toastEvents.tryEmit("Report submitted for moderation") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Report failed") }
        }
    }

    fun postStatus(text: String, themeColor: String) {
        val me = (userProfile.value as? UiState.Success)?.data
        viewModelScope.launch {
            repository.postStatus(
                authorName = me?.displayName ?: "DYFOOT Fan",
                authorPhotoUrl = me?.photoUrl.orEmpty(),
                text = text,
                themeColor = themeColor
            ).onSuccess {
                _toastEvents.tryEmit("Matchday status posted (24h)")
            }.onFailure {
                _toastEvents.tryEmit(it.localizedMessage ?: "Could not post status")
            }
        }
    }

    fun deleteStatus(statusId: String) {
        viewModelScope.launch {
            repository.deleteStatus(statusId)
                .onSuccess { _toastEvents.tryEmit("Status removed") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Delete failed") }
        }
    }

    // --- Admin Studio Actions ---

    fun saveTip(tip: TipItem, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveTip(tip)
                .onSuccess {
                    _toastEvents.tryEmit("Tip saved & published")
                    onDone()
                }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Failed to save tip") }
        }
    }

    fun deleteTip(tipId: String) {
        viewModelScope.launch {
            repository.deleteTip(tipId)
                .onSuccess { _toastEvents.tryEmit("Tip deleted") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Delete failed") }
        }
    }

    fun saveNews(article: NewsArticle, onDone: () -> Unit = {}) {
        if (!isAdminOrOwner()) {
            _toastEvents.tryEmit("Restricted: Admin or Owner authorization required")
            return
        }
        viewModelScope.launch {
            val sanitized = article.copy(sourceUrl = "")
            repository.saveNews(sanitized)
                .onSuccess {
                    val isSw = appLanguage.value == AppLanguage.SWAHILI
                    _toastEvents.tryEmit(
                        if (isSw) "Habari imehifadhiwa ndani ya DYFOOT"
                        else "Local football article saved internally"
                    )
                    onDone()
                }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Failed to save news") }
        }
    }

    fun deleteNews(newsId: String) {
        if (!isAdminOrOwner()) {
            _toastEvents.tryEmit("Restricted: Admin or Owner authorization required")
            return
        }
        viewModelScope.launch {
            repository.deleteNews(newsId).onSuccess { _toastEvents.tryEmit("Article deleted") }
        }
    }

    fun saveVideo(video: VideoItem, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveVideo(video)
                .onSuccess {
                    _toastEvents.tryEmit("Video published to Watch")
                    onDone()
                }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Failed to save video") }
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            repository.deleteVideo(videoId).onSuccess { _toastEvents.tryEmit("Video deleted") }
        }
    }

    fun savePodcast(podcast: PodcastEpisode, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.savePodcast(podcast)
                .onSuccess {
                    _toastEvents.tryEmit("Podcast episode saved")
                    onDone()
                }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Failed to save podcast") }
        }
    }

    fun deletePodcast(podcastId: String) {
        viewModelScope.launch {
            repository.deletePodcast(podcastId).onSuccess { _toastEvents.tryEmit("Podcast deleted") }
        }
    }

    fun saveAdvertisement(ad: Advertisement, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveAdvertisement(ad)
                .onSuccess {
                    _toastEvents.tryEmit("Campaign saved")
                    onDone()
                }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Failed to save ad") }
        }
    }

    fun deleteAdvertisement(adId: String) {
        viewModelScope.launch {
            repository.deleteAdvertisement(adId).onSuccess { _toastEvents.tryEmit("Ad removed") }
        }
    }

    fun saveAnnouncement(announcement: Announcement, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveAnnouncement(announcement)
                .onSuccess {
                    _toastEvents.tryEmit("Announcement posted")
                    onDone()
                }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Failed to post announcement") }
        }
    }

    fun deleteAnnouncement(announcementId: String) {
        viewModelScope.launch {
            repository.deleteAnnouncement(announcementId).onSuccess { _toastEvents.tryEmit("Announcement deleted") }
        }
    }

    fun saveFixture(fixture: FixtureItem, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveFixture(fixture)
                .onSuccess {
                    _toastEvents.tryEmit("Fixture saved")
                    onDone()
                }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Failed to save fixture") }
        }
    }

    fun deleteFixture(fixtureId: String) {
        viewModelScope.launch {
            repository.deleteFixture(fixtureId).onSuccess { _toastEvents.tryEmit("Fixture deleted") }
        }
    }

    fun verifyPayment(payment: PaymentRecord, approve: Boolean) {
        viewModelScope.launch {
            repository.verifyPaymentAndActivateVip(payment, approve)
                .onSuccess {
                    _toastEvents.tryEmit(if (approve) "Payment verified & VIP activated" else "Payment marked failed")
                }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Verification error") }
        }
    }

    fun updateUserRole(target: UserProfile, newRole: UserRole, suspended: Boolean) {
        viewModelScope.launch {
            repository.updateUserRoleAndSuspension(target, newRole, suspended)
                .onSuccess { _toastEvents.tryEmit("Updated ${target.displayName}") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Role update denied") }
        }
    }

    fun resolveReport(reportId: String, status: String) {
        viewModelScope.launch {
            repository.resolveReport(reportId, status)
                .onSuccess { _toastEvents.tryEmit("Report marked $status") }
        }
    }

    fun saveAppSettings(settings: AppSettings) {
        viewModelScope.launch {
            repository.saveAppSettings(settings)
                .onSuccess { _toastEvents.tryEmit("Platform settings updated") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Settings save failed") }
        }
    }

    fun seedStarterSampleContent() {
        viewModelScope.launch {
            repository.seedStarterSampleContent()
                .onSuccess { _toastEvents.tryEmit("Sample starter pack added to Firestore") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Seed failed") }
        }
    }
}
